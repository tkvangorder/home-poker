package org.homepoker.game.table;

import org.homepoker.game.GameContext;
import org.homepoker.game.GameSettings;
import org.homepoker.lib.exception.ValidationException;
import org.homepoker.model.command.*;
import org.homepoker.model.event.table.*;
import org.homepoker.model.game.*;
import org.homepoker.model.game.Seat.SeatCard;
import org.homepoker.model.game.cash.CashGame;
import org.homepoker.model.poker.Card;
import org.homepoker.poker.BitwisePokerRanker;
import org.homepoker.poker.ClassicPokerRanker;
import org.homepoker.poker.Deck;
import org.homepoker.poker.HandResult;

import java.time.Instant;
import java.util.*;

public class TexasHoldemTableManager<T extends Game<T>> extends TableManager<T> {

  private final ClassicPokerRanker pokerRanker = new BitwisePokerRanker();
  private final Map<String, Deck> activeDecks = new HashMap<>();

  public TexasHoldemTableManager(GameSettings gameSettings) {
    super(gameSettings);
  }

  @Override
  public void transitionTable(Game<T> game, Table table, GameContext gameContext) {
    if (table.status() != Table.Status.PLAYING && table.status() != Table.Status.PAUSE_AFTER_HAND) {
      return;
    }

    switch (table.handPhase()) {
      case WAITING_FOR_PLAYERS -> transitionFromWaitingForPlayers(game, table, gameContext);
      case PREDEAL -> transitionFromPredeal(game, table, gameContext);
      case DEAL -> transitionFromDeal(game, table, gameContext);
      case PRE_FLOP_BETTING -> transitionFromBetting(game, table, gameContext, HandPhase.FLOP);
      case FLOP -> transitionFromCommunityCard(game, table, gameContext, 3, "Flop", HandPhase.FLOP_BETTING);
      case FLOP_BETTING -> transitionFromBetting(game, table, gameContext, HandPhase.TURN);
      case TURN -> transitionFromCommunityCard(game, table, gameContext, 1, "Turn", HandPhase.TURN_BETTING);
      case TURN_BETTING -> transitionFromBetting(game, table, gameContext, HandPhase.RIVER);
      case RIVER -> transitionFromCommunityCard(game, table, gameContext, 1, "River", HandPhase.RIVER_BETTING);
      case RIVER_BETTING -> transitionFromBetting(game, table, gameContext, HandPhase.SHOWDOWN);
      case SHOWDOWN -> transitionFromShowdown(game, table, gameContext);
      case HAND_COMPLETE -> transitionFromHandComplete(game, table, gameContext);
    }
  }

  @Override
  protected void applySubcommand(GameCommand command, Game<T> game, Table table, GameContext gameContext) {
    switch (command) {
      case PlayerActionCommand pac -> applyPlayerAction(pac, game, table, gameContext);
      case PlayerIntent pi -> applyPlayerIntent(pi, table);
      case ShowCards sc -> applyShowCards(sc, table);
      case PostBlind pb -> applyPostBlind(pb, table);
      default -> throw new ValidationException("Unknown table command: " + command.commandId());
    }
  }

  @Override
  public Table createTable(String tableId) {
    List<Seat> seats = new ArrayList<>();
    for (int i = 0; i < gameSettings().numberOfSeats(); i++) {
      seats.add(Seat.builder().build());
    }
    return Table.builder()
        .id(tableId)
        .seats(seats)
        .build();
  }

  // ========== State Transition Methods ==========

  private void transitionFromWaitingForPlayers(Game<T> game, Table table, GameContext gameContext) {
    // If PAUSE_AFTER_HAND, transition to PAUSED since no hand is in progress
    if (table.status() == Table.Status.PAUSE_AFTER_HAND) {
      table.status(Table.Status.PAUSED);
      return;
    }

    int activeCount = countActivePlayers(table);
    if (activeCount >= 2) {
      // Skip PREDEAL for now and go directly to DEAL
      table.handPhase(HandPhase.DEAL);
      transitionFromDeal(game, table, gameContext);
    }
  }

  private void transitionFromPredeal(Game<T> game, Table table, GameContext gameContext) {
    Instant phaseStarted = table.phaseStartedAt();
    if (phaseStarted == null) {
      table.phaseStartedAt(Instant.now());
      return;
    }

    boolean timerExpired = phaseStarted.plusSeconds(gameSettings().predealTimeSeconds()).isBefore(Instant.now());
    boolean noBuyingInPlayers = noBuyingInPlayers(table);

    if (timerExpired || noBuyingInPlayers) {
      // Activate JOINED_WAITING players
      activateWaitingPlayers(table);

      int activeCount = countActivePlayers(table);
      if (activeCount >= 2) {
        table.phaseStartedAt(null);
        table.handPhase(HandPhase.DEAL);
        transitionFromDeal(game, table, gameContext);
      } else {
        table.phaseStartedAt(null);
        table.handPhase(HandPhase.WAITING_FOR_PLAYERS);
      }
    }
  }

  private void transitionFromDeal(Game<T> game, Table table, GameContext gameContext) {
    int smallBlind = game.smallBlind();
    int bigBlind = game.bigBlind();

    // Activate JOINED_WAITING players for this hand
    activateWaitingPlayers(table);

    int activeCount = countActivePlayers(table);
    if (activeCount < 2) {
      table.handPhase(HandPhase.WAITING_FOR_PLAYERS);
      return;
    }

    // Rotate dealer button
    int dealerPosition = rotateDealerButton(table);
    table.dealerPosition(dealerPosition);

    // Compute blind positions
    boolean headsUp = activeCount == 2;
    int sbPosition;
    int bbPosition;

    if (headsUp) {
      // Heads-up: dealer is small blind
      sbPosition = dealerPosition;
      bbPosition = nextActivePosition(table, dealerPosition);
    } else {
      sbPosition = nextActivePosition(table, dealerPosition);
      bbPosition = nextActivePosition(table, sbPosition);
    }

    table.smallBlindPosition(sbPosition);
    table.bigBlindPosition(bbPosition);

    // Create a new deck
    Deck deck = new Deck();
    activeDecks.put(table.id(), deck);

    // Post blinds
    postBlind(table, sbPosition, smallBlind);
    postBlind(table, bbPosition, bigBlind);

    // Set current bet and minimum raise
    table.currentBet(bigBlind);
    table.minimumRaise(bigBlind);

    // Deal 2 hole cards to each ACTIVE seat
    for (int i = 0; i < table.seats().size(); i++) {
      Seat seat = table.seats().get(i);
      if (seat.status() == Seat.Status.ACTIVE) {
        List<Card> cards = deck.drawCards(2);
        List<SeatCard> seatCards = cards.stream()
            .map(c -> new SeatCard(c, false))
            .toList();
        seat.cards(new ArrayList<>(seatCards));
      }
    }

    // Set action position (UTG = first active seat after BB)
    int utgPosition = nextActiveNonAllInPosition(table, bbPosition);
    table.actionPosition(utgPosition);

    // lastRaiserPosition = BB (BB has the option to raise)
    table.lastRaiserPosition(bbPosition);

    // Set action deadline
    table.actionDeadline(Instant.now().plusSeconds(gameSettings().actionTimeSeconds()));

    // Increment hand number
    table.handNumber(table.handNumber() + 1);

    // Emit events
    gameContext.queueEvent(new HandStarted(
        Instant.now(), game.id(), table.id(),
        table.handNumber(), dealerPosition,
        sbPosition, bbPosition, smallBlind, bigBlind
    ));

    // Emit HoleCardsDealt per player
    for (int i = 0; i < table.seats().size(); i++) {
      Seat seat = table.seats().get(i);
      if (seat.status() == Seat.Status.ACTIVE && seat.cards() != null && seat.player() != null) {
        gameContext.queueEvent(new HoleCardsDealt(
            Instant.now(), game.id(), table.id(),
            seat.player().userId(), i, seat.cards()
        ));
      }
    }

    // Advance to PRE_FLOP_BETTING
    table.handPhase(HandPhase.PRE_FLOP_BETTING);
    table.phaseStartedAt(Instant.now());
    gameContext.forceUpdate(true);
  }

  private void transitionFromBetting(Game<T> game, Table table, GameContext gameContext, HandPhase nextPhase) {
    // Check player timeout
    if (table.actionDeadline() != null && table.actionDeadline().isBefore(Instant.now())) {
      Integer actionPos = table.actionPosition();
      if (actionPos != null) {
        Seat actionSeat = table.seats().get(actionPos);
        if (actionSeat.status() == Seat.Status.ACTIVE && !actionSeat.isAllIn()) {
          // Auto-apply default: check if free, else fold
          PlayerAction defaultAction;
          if (actionSeat.currentBetAmount() >= table.currentBet()) {
            defaultAction = new PlayerAction.Check();
          } else {
            defaultAction = new PlayerAction.Fold();
          }

          gameContext.queueEvent(new PlayerTimedOut(
              Instant.now(), game.id(), table.id(), actionPos,
              actionSeat.player() != null ? actionSeat.player().userId() : "unknown",
              defaultAction
          ));

          executePlayerAction(table, actionPos, actionSeat, defaultAction, game, gameContext);
        }
      }
    }

    // Check if all but one have folded
    int activeSeatCount = countNonFoldedPlayers(table);
    if (activeSeatCount <= 1) {
      // Award pot to the last remaining player
      awardPotToLastPlayer(game, table, gameContext);
      return;
    }

    // Check if betting round is complete
    if (isBettingRoundComplete(table)) {
      collectBetsIntoPots(table);

      gameContext.queueEvent(new BettingRoundComplete(
          Instant.now(), game.id(), table.id(),
          table.handPhase(), table.pots()
      ));

      // Check for all-in shortcut: if all non-folded players are all-in, deal remaining cards
      if (allNonFoldedAreAllIn(table) || countActiveNonAllInPlayers(table) <= 1) {
        // Deal remaining community cards and go to showdown
        dealRemainingCommunityCards(game, table, gameContext);
        table.handPhase(HandPhase.SHOWDOWN);
        transitionFromShowdown(game, table, gameContext);
        return;
      }

      // Advance to next phase
      table.handPhase(nextPhase);

      if (nextPhase == HandPhase.SHOWDOWN) {
        transitionFromShowdown(game, table, gameContext);
      }
      // FLOP/TURN/RIVER are transient - they'll be handled on the next tick
    }
  }

  private void transitionFromCommunityCard(Game<T> game, Table table, GameContext gameContext,
                                           int cardCount, String phaseName, HandPhase nextBettingPhase) {
    Deck deck = activeDecks.get(table.id());
    if (deck == null) {
      throw new IllegalStateException("No deck found for table " + table.id());
    }

    List<Card> newCards = deck.drawCards(cardCount);
    table.communityCards().addAll(newCards);

    gameContext.queueEvent(new CommunityCardsDealt(
        Instant.now(), game.id(), table.id(), newCards, phaseName
    ));

    // Clear pending intents
    clearPendingIntents(table);

    // Reset for new betting round
    table.currentBet(0);
    table.minimumRaise(game.bigBlind());

    // Set action position to first active non-all-in seat left of dealer
    Integer firstActor = firstActiveNonAllInAfterDealer(table);
    if (firstActor == null) {
      // All remaining players are all-in, skip to showdown
      table.handPhase(HandPhase.SHOWDOWN);
      transitionFromShowdown(game, table, gameContext);
      return;
    }

    table.actionPosition(firstActor);
    table.lastRaiserPosition(firstActor); // Post-flop: first actor determines round completion
    table.actionDeadline(Instant.now().plusSeconds(gameSettings().actionTimeSeconds()));

    table.handPhase(nextBettingPhase);
    table.phaseStartedAt(Instant.now());

    // Apply pending intent if the current player has one
    tryApplyPendingIntent(game, table, gameContext);
  }

  private void transitionFromShowdown(Game<T> game, Table table, GameContext gameContext) {
    // Evaluate hands and award pots
    List<ShowdownResult.PotResult> potResults = new ArrayList<>();

    for (int potIndex = 0; potIndex < table.pots().size(); potIndex++) {
      Table.Pot pot = table.pots().get(potIndex);
      List<ShowdownResult.Winner> winners = evaluatePotWinners(game, table, pot, potIndex);
      potResults.add(new ShowdownResult.PotResult(potIndex, pot.amount(), winners));
    }

    gameContext.queueEvent(new ShowdownResult(
        Instant.now(), game.id(), table.id(), potResults
    ));

    // Mark winning cards as showCard = true
    markShowdownCards(table, potResults);

    table.handPhase(HandPhase.HAND_COMPLETE);
    table.phaseStartedAt(Instant.now());
    gameContext.forceUpdate(true);
  }

  private void transitionFromHandComplete(Game<T> game, Table table, GameContext gameContext) {
    Instant phaseStarted = table.phaseStartedAt();
    if (phaseStarted == null) {
      table.phaseStartedAt(Instant.now());
      return;
    }

    // Wait for review period
    boolean reviewComplete = phaseStarted.plusSeconds(gameSettings().reviewHandTimeSeconds()).isBefore(Instant.now());
    if (!reviewComplete) {
      return;
    }

    // Emit hand complete event
    gameContext.queueEvent(new HandComplete(
        Instant.now(), game.id(), table.id(), table.handNumber()
    ));

    // Remove departed players (status == OUT) and handle busted players
    handlePostHandPlayerStatus(game, table);

    // Clear hand state
    clearHandState(table);

    // Clean up deck
    activeDecks.remove(table.id());

    // Check for PAUSE_AFTER_HAND
    if (table.status() == Table.Status.PAUSE_AFTER_HAND) {
      table.status(Table.Status.PAUSED);
      table.handPhase(HandPhase.WAITING_FOR_PLAYERS);
      gameContext.forceUpdate(true);
      return;
    }

    // Determine next phase
    if (hasBuyingInPlayers(table)) {
      table.handPhase(HandPhase.PREDEAL);
      table.phaseStartedAt(Instant.now());
    } else {
      int activeCount = countActivePlayers(table);
      if (activeCount >= 2) {
        table.handPhase(HandPhase.DEAL);
        transitionFromDeal(game, table, gameContext);
      } else {
        table.handPhase(HandPhase.WAITING_FOR_PLAYERS);
      }
    }
    gameContext.forceUpdate(true);
  }

  // ========== Command Handlers ==========

  private void applyPlayerAction(PlayerActionCommand command, Game<T> game, Table table, GameContext gameContext) {
    // Validate it's a betting phase
    if (!isBettingPhase(table.handPhase())) {
      throw new ValidationException("Player actions are only valid during a betting phase.");
    }

    // Find the player's seat
    int seatPosition = findPlayerSeat(table, command.user().id());
    if (seatPosition < 0) {
      throw new ValidationException("You are not seated at this table.");
    }

    // Validate it's the player's turn
    if (table.actionPosition() == null || table.actionPosition() != seatPosition) {
      throw new ValidationException("It is not your turn to act.");
    }

    Seat seat = table.seats().get(seatPosition);
    if (seat.status() != Seat.Status.ACTIVE || seat.isAllIn()) {
      throw new ValidationException("You cannot act in your current state.");
    }

    // Validate and execute the action
    validateAction(command.action(), seat, table, game);
    executePlayerAction(table, seatPosition, seat, command.action(), game, gameContext);
  }

  private void applyPlayerIntent(PlayerIntent command, Table table) {
    if (!isBettingPhase(table.handPhase())) {
      throw new ValidationException("Intents are only valid during a betting phase.");
    }

    int seatPosition = findPlayerSeat(table, command.user().id());
    if (seatPosition < 0) {
      throw new ValidationException("You are not seated at this table.");
    }

    Seat seat = table.seats().get(seatPosition);
    if (seat.status() != Seat.Status.ACTIVE) {
      throw new ValidationException("You cannot set an intent in your current state.");
    }

    seat.pendingIntent(command.action());
  }

  private void applyShowCards(ShowCards command, Table table) {
    if (table.handPhase() != HandPhase.HAND_COMPLETE) {
      throw new ValidationException("You can only show cards during the hand review period.");
    }

    int seatPosition = findPlayerSeat(table, command.user().id());
    if (seatPosition < 0) {
      throw new ValidationException("You are not seated at this table.");
    }

    Seat seat = table.seats().get(seatPosition);
    if (seat.cards() != null) {
      List<SeatCard> shownCards = seat.cards().stream()
          .map(sc -> sc.withShowCard(true))
          .toList();
      seat.cards(new ArrayList<>(shownCards));
    }
  }

  private void applyPostBlind(PostBlind command, Table table) {
    if (table.handPhase() != HandPhase.PREDEAL && table.handPhase() != HandPhase.WAITING_FOR_PLAYERS) {
      throw new ValidationException("You can only post a blind during the predeal phase.");
    }

    int seatPosition = findPlayerSeat(table, command.user().id());
    if (seatPosition < 0) {
      throw new ValidationException("You are not seated at this table.");
    }

    Seat seat = table.seats().get(seatPosition);
    seat.mustPostBlind(false);
    seat.missedBigBlind(false);
  }

  // ========== Action Execution ==========

  private void executePlayerAction(Table table, int seatPosition, Seat seat, PlayerAction action, Game<T> game, GameContext gameContext) {
    Player player = seat.player();
    int chipsBefore = player != null ? player.chipCount() : 0;

    switch (action) {
      case PlayerAction.Fold fold -> {
        seat.status(Seat.Status.FOLDED);
        seat.action(fold);
      }
      case PlayerAction.Check check -> {
        seat.action(check);
      }
      case PlayerAction.Call call -> {
        int amountToCall = table.currentBet() - seat.currentBetAmount();
        int actualCall = Math.min(amountToCall, chipsBefore);
        deductChips(seat, actualCall);
        seat.currentBetAmount(seat.currentBetAmount() + actualCall);
        if (player != null && player.chipCount() <= 0) {
          seat.isAllIn(true);
        }
        seat.action(new PlayerAction.Call(actualCall));
      }
      case PlayerAction.Bet bet -> {
        int betAmount = bet.amount();
        deductChips(seat, betAmount);
        seat.currentBetAmount(seat.currentBetAmount() + betAmount);
        table.currentBet(seat.currentBetAmount());
        table.minimumRaise(betAmount);
        table.lastRaiserPosition(seatPosition);
        if (player != null && player.chipCount() <= 0) {
          seat.isAllIn(true);
        }
        seat.action(new PlayerAction.Bet(betAmount));
      }
      case PlayerAction.Raise raise -> {
        int raiseTotal = raise.amount(); // This is the new total bet amount
        int additionalChips = raiseTotal - seat.currentBetAmount();
        int actualAdditional = Math.min(additionalChips, chipsBefore);
        deductChips(seat, actualAdditional);
        int raiseIncrease = seat.currentBetAmount() + actualAdditional - table.currentBet();
        seat.currentBetAmount(seat.currentBetAmount() + actualAdditional);
        table.currentBet(seat.currentBetAmount());
        if (raiseIncrease > table.minimumRaise()) {
          table.minimumRaise(raiseIncrease);
        }
        table.lastRaiserPosition(seatPosition);
        if (player != null && player.chipCount() <= 0) {
          seat.isAllIn(true);
        }
        seat.action(new PlayerAction.Raise(raiseTotal));
      }
    }

    seat.pendingIntent(null); // Clear any pending intent after acting

    // Emit event
    int chipCount = player != null ? player.chipCount() : 0;
    gameContext.queueEvent(new PlayerActed(
        Instant.now(), game.id(), table.id(), seatPosition,
        player != null ? player.userId() : "unknown",
        action, chipCount
    ));

    // Advance action position
    advanceActionPosition(table, seatPosition);

    // Try to apply pending intent for the next player
    tryApplyPendingIntent(game, table, gameContext);
  }

  // ========== Betting Logic ==========

  private void validateAction(PlayerAction action, Seat seat, Table table, Game<T> game) {
    int chipCount = seat.player() != null ? seat.player().chipCount() : 0;

    switch (action) {
      case PlayerAction.Fold fold -> {
        // Always valid
      }
      case PlayerAction.Check check -> {
        if (seat.currentBetAmount() < table.currentBet()) {
          throw new ValidationException("You cannot check when there is a bet to call.");
        }
      }
      case PlayerAction.Call call -> {
        if (seat.currentBetAmount() >= table.currentBet()) {
          throw new ValidationException("There is nothing to call.");
        }
      }
      case PlayerAction.Bet bet -> {
        if (table.currentBet() > 0) {
          throw new ValidationException("You cannot bet when there is already a bet. Use raise instead.");
        }
        if (bet.amount() < game.bigBlind()) {
          throw new ValidationException("Bet must be at least the big blind (" + game.bigBlind() + ").");
        }
        if (bet.amount() > chipCount) {
          throw new ValidationException("You don't have enough chips to bet " + bet.amount() + ".");
        }
      }
      case PlayerAction.Raise raise -> {
        if (table.currentBet() == 0) {
          throw new ValidationException("You cannot raise when there is no bet. Use bet instead.");
        }
        int minRaise = table.currentBet() + table.minimumRaise();
        int raiseTotal = raise.amount();
        int additionalChips = raiseTotal - seat.currentBetAmount();
        // Allow all-in for less than minimum raise
        if (additionalChips >= chipCount) {
          // All-in raise is always valid
          return;
        }
        if (raiseTotal < minRaise) {
          throw new ValidationException("Raise must be at least " + minRaise + " (current bet " + table.currentBet() + " + minimum raise " + table.minimumRaise() + ").");
        }
      }
    }
  }

  private boolean isBettingRoundComplete(Table table) {
    Integer actionPos = table.actionPosition();
    Integer lastRaiser = table.lastRaiserPosition();

    if (actionPos == null || lastRaiser == null) {
      return true;
    }

    // Check if any active non-all-in player still needs to act
    // The round is complete when action comes back to the lastRaiserPosition
    // AND every active non-all-in player has acted
    Seat actionSeat = table.seats().get(actionPos);
    if (actionSeat.status() != Seat.Status.ACTIVE || actionSeat.isAllIn()) {
      // Shouldn't happen since advanceActionPosition skips these
      return true;
    }

    // If the action hasn't reached the last raiser, the round continues
    // The action is on a player whose action is null => they haven't acted yet
    if (actionSeat.action() == null) {
      return false;
    }

    // If everyone has acted and action returns to the last raiser, the round is complete
    return actionPos.equals(lastRaiser) && actionSeat.action() != null;
  }

  private void advanceActionPosition(Table table, int currentPosition) {
    Integer nextPos = nextActiveNonAllInPositionOrNull(table, currentPosition);
    if (nextPos == null) {
      // No more players can act
      table.actionPosition(table.lastRaiserPosition());
      return;
    }

    // If we've come back to the last raiser and they've already acted, round is complete
    table.actionPosition(nextPos);
    table.actionDeadline(Instant.now().plusSeconds(gameSettings().actionTimeSeconds()));
  }

  private void tryApplyPendingIntent(Game<T> game, Table table, GameContext gameContext) {
    Integer actionPos = table.actionPosition();
    if (actionPos == null) return;

    Seat seat = table.seats().get(actionPos);
    if (seat.pendingIntent() == null || seat.status() != Seat.Status.ACTIVE || seat.isAllIn()) return;

    // Validate the pending intent is still legal
    try {
      validateAction(seat.pendingIntent(), seat, table, game);
      // Intent is valid, auto-execute it
      PlayerAction intent = seat.pendingIntent();
      seat.pendingIntent(null);
      executePlayerAction(table, actionPos, seat, intent, game, gameContext);
    } catch (ValidationException e) {
      // Intent is no longer valid, clear it and wait for manual action
      seat.pendingIntent(null);
    }
  }

  // ========== Pot Management ==========

  /**
   * Collects all outstanding bets into pots using the "peeling" algorithm.
   * This correctly handles side pots when players go all-in for different amounts.
   */
  private void collectBetsIntoPots(Table table) {
    // Gather all non-zero bets with their seat positions
    List<int[]> bets = new ArrayList<>(); // [seatPosition, betAmount]
    for (int i = 0; i < table.seats().size(); i++) {
      Seat seat = table.seats().get(i);
      if (seat.currentBetAmount() > 0) {
        bets.add(new int[]{i, seat.currentBetAmount()});
      }
    }

    if (bets.isEmpty()) return;

    // Sort by bet amount ascending for peeling
    bets.sort(Comparator.comparingInt(a -> a[1]));

    int previousPeel = 0;
    for (int[] bet : bets) {
      int peelLevel = bet[1];
      if (peelLevel <= previousPeel) continue;

      int potAmount = 0;
      List<Integer> eligible = new ArrayList<>();

      for (int[] b : bets) {
        if (b[1] >= peelLevel) {
          // This seat is eligible
          Seat seat = table.seats().get(b[0]);
          if (seat.status() != Seat.Status.FOLDED) {
            eligible.add(b[0]);
          }
          potAmount += (peelLevel - previousPeel);
        } else if (b[1] > previousPeel) {
          // Partially contributes
          potAmount += (b[1] - previousPeel);
          // Folded players still contribute chips but aren't eligible
        }
      }

      if (potAmount > 0) {
        // Try to merge with existing pot if same eligible seats
        boolean merged = false;
        if (!table.pots().isEmpty()) {
          Table.Pot lastPot = table.pots().getLast();
          if (lastPot.seatPositions().equals(eligible)) {
            table.pots().set(table.pots().size() - 1,
                new Table.Pot(lastPot.amount() + potAmount, eligible));
            merged = true;
          }
        }
        if (!merged) {
          table.pots().add(new Table.Pot(potAmount, eligible));
        }
      }
      previousPeel = peelLevel;
    }

    // Clear all bets
    for (Seat seat : table.seats()) {
      seat.currentBetAmount(0);
    }
  }

  private void awardPotToLastPlayer(Game<T> game, Table table, GameContext gameContext) {
    // Collect any outstanding bets first
    collectBetsIntoPots(table);

    // Find the last non-folded player
    int winnerPosition = -1;
    for (int i = 0; i < table.seats().size(); i++) {
      Seat seat = table.seats().get(i);
      if (seat.status() == Seat.Status.ACTIVE) {
        winnerPosition = i;
        break;
      }
    }

    if (winnerPosition < 0) return;

    Seat winnerSeat = table.seats().get(winnerPosition);
    Player winner = winnerSeat.player();
    if (winner == null) return;

    int totalWon = 0;
    List<ShowdownResult.PotResult> potResults = new ArrayList<>();
    for (int i = 0; i < table.pots().size(); i++) {
      Table.Pot pot = table.pots().get(i);
      totalWon += pot.amount();
      potResults.add(new ShowdownResult.PotResult(i, pot.amount(),
          List.of(new ShowdownResult.Winner(winnerPosition, winner.userId(), pot.amount(), "Last player standing"))));
    }

    winner.chipCount(winner.chipCount() + totalWon);

    // Emit the showdown result (even though there's no actual showdown)
    if (!potResults.isEmpty()) {
      gameContext.queueEvent(new ShowdownResult(
          Instant.now(), game.id(), table.id(), potResults
      ));
    }

    table.handPhase(HandPhase.HAND_COMPLETE);
    table.phaseStartedAt(Instant.now());
    gameContext.forceUpdate(true);
  }

  // ========== Showdown Logic ==========

  private List<ShowdownResult.Winner> evaluatePotWinners(Game<T> game, Table table, Table.Pot pot, int potIndex) {
    List<ShowdownResult.Winner> winners = new ArrayList<>();

    // Build hand results for eligible seats
    record SeatHandResult(int position, HandResult result, Seat seat) {}
    List<SeatHandResult> results = new ArrayList<>();

    for (int position : pot.seatPositions()) {
      Seat seat = table.seats().get(position);
      if (seat.status() == Seat.Status.FOLDED || seat.cards() == null) continue;

      List<Card> allCards = new ArrayList<>();
      for (SeatCard sc : seat.cards()) {
        allCards.add(sc.card());
      }
      allCards.addAll(table.communityCards());

      HandResult handResult = pokerRanker.rankHand(allCards);
      results.add(new SeatHandResult(position, handResult, seat));
    }

    if (results.isEmpty()) return winners;

    // Find the best hand
    results.sort((a, b) -> b.result().compareTo(a.result()));
    HandResult bestHand = results.getFirst().result();

    // Find all players with the best hand (ties)
    List<SeatHandResult> tiedWinners = results.stream()
        .filter(r -> r.result().compareTo(bestHand) == 0)
        .toList();

    int share = pot.amount() / tiedWinners.size();
    int remainder = pot.amount() % tiedWinners.size();

    for (int i = 0; i < tiedWinners.size(); i++) {
      SeatHandResult shr = tiedWinners.get(i);
      Player player = shr.seat().player();
      if (player == null) continue;

      int amount = share;
      // Remainder to the player closest left of dealer
      if (i == 0 && remainder > 0) {
        // For simplicity, award remainder to the first winner (closest to dealer's left)
        amount += remainder;
      }

      player.chipCount(player.chipCount() + amount);

      String handDesc = shr.result().getRank().toString() + " : " + shr.result().getCardValues();
      winners.add(new ShowdownResult.Winner(shr.position(), player.userId(), amount, handDesc));
    }

    return winners;
  }

  private void markShowdownCards(Table table, List<ShowdownResult.PotResult> potResults) {
    Set<Integer> winnerPositions = new HashSet<>();
    for (ShowdownResult.PotResult pr : potResults) {
      for (ShowdownResult.Winner w : pr.winners()) {
        winnerPositions.add(w.seatPosition());
      }
    }

    for (int pos : winnerPositions) {
      Seat seat = table.seats().get(pos);
      if (seat.cards() != null) {
        List<SeatCard> shown = seat.cards().stream()
            .map(sc -> sc.withShowCard(true))
            .toList();
        seat.cards(new ArrayList<>(shown));
      }
    }
  }

  private void dealRemainingCommunityCards(Game<T> game, Table table, GameContext gameContext) {
    Deck deck = activeDecks.get(table.id());
    if (deck == null) return;

    int currentCount = table.communityCards().size();
    if (currentCount < 3) {
      List<Card> flop = deck.drawCards(3);
      table.communityCards().addAll(flop);
      gameContext.queueEvent(new CommunityCardsDealt(
          Instant.now(), game.id(), table.id(), flop, "Flop"));
      currentCount = 3;
    }
    if (currentCount < 4) {
      List<Card> turn = deck.drawCards(1);
      table.communityCards().addAll(turn);
      gameContext.queueEvent(new CommunityCardsDealt(
          Instant.now(), game.id(), table.id(), turn, "Turn"));
      currentCount = 4;
    }
    if (currentCount < 5) {
      List<Card> river = deck.drawCards(1);
      table.communityCards().addAll(river);
      gameContext.queueEvent(new CommunityCardsDealt(
          Instant.now(), game.id(), table.id(), river, "River"));
    }
  }

  // ========== Hand Complete Helpers ==========

  private void handlePostHandPlayerStatus(Game<T> game, Table table) {
    for (Seat seat : table.seats()) {
      if (seat.player() == null) continue;
      Player player = seat.player();

      // Remove departed players
      if (player.status() == PlayerStatus.OUT) {
        seat.status(Seat.Status.EMPTY);
        seat.player(null);
        player.tableId(null);
        continue;
      }

      // Handle busted players (no chips)
      if (player.chipCount() != null && player.chipCount() <= 0) {
        seat.status(Seat.Status.JOINED_WAITING);
        player.status(PlayerStatus.BUYING_IN);
      }
    }
  }

  private void clearHandState(Table table) {
    for (Seat seat : table.seats()) {
      seat.cards(null);
      seat.action(null);
      seat.currentBetAmount(0);
      seat.isAllIn(false);
      seat.pendingIntent(null);

      // Reset active/folded seats back to ACTIVE (but not EMPTY or JOINED_WAITING)
      if (seat.status() == Seat.Status.FOLDED) {
        seat.status(Seat.Status.ACTIVE);
      }
    }
    table.communityCards().clear();
    table.pots().clear();
    table.currentBet(0);
    table.minimumRaise(0);
    table.actionPosition(null);
    table.actionDeadline(null);
    table.lastRaiserPosition(null);
    table.smallBlindPosition(null);
    table.bigBlindPosition(null);
    table.phaseStartedAt(null);
  }

  private void activateWaitingPlayers(Table table) {
    for (Seat seat : table.seats()) {
      if (seat.status() == Seat.Status.JOINED_WAITING && isPlayerReadyToPlay(seat.player())) {
        seat.status(Seat.Status.ACTIVE);
      }
    }
  }

  // ========== Position Helpers ==========

  private int rotateDealerButton(Table table) {
    Integer current = table.dealerPosition();
    if (current == null) {
      // First hand: pick a random active position
      List<Integer> activePositions = getActivePositions(table);
      return activePositions.get(new Random().nextInt(activePositions.size()));
    }
    return nextActivePosition(table, current);
  }

  /**
   * Finds the next ACTIVE seat position clockwise from the given position.
   */
  private int nextActivePosition(Table table, int fromPosition) {
    int size = table.seats().size();
    for (int i = 1; i <= size; i++) {
      int pos = (fromPosition + i) % size;
      Seat seat = table.seats().get(pos);
      if (seat.status() == Seat.Status.ACTIVE) {
        return pos;
      }
    }
    return fromPosition; // Shouldn't happen with 2+ active players
  }

  /**
   * Finds the next ACTIVE, non-all-in seat position clockwise from the given position.
   */
  private int nextActiveNonAllInPosition(Table table, int fromPosition) {
    int size = table.seats().size();
    for (int i = 1; i <= size; i++) {
      int pos = (fromPosition + i) % size;
      Seat seat = table.seats().get(pos);
      if (seat.status() == Seat.Status.ACTIVE && !seat.isAllIn()) {
        return pos;
      }
    }
    return fromPosition;
  }

  private Integer nextActiveNonAllInPositionOrNull(Table table, int fromPosition) {
    int size = table.seats().size();
    for (int i = 1; i <= size; i++) {
      int pos = (fromPosition + i) % size;
      if (pos == fromPosition) return null; // Wrapped around
      Seat seat = table.seats().get(pos);
      if (seat.status() == Seat.Status.ACTIVE && !seat.isAllIn()) {
        return pos;
      }
    }
    return null;
  }

  private Integer firstActiveNonAllInAfterDealer(Table table) {
    Integer dealer = table.dealerPosition();
    if (dealer == null) return null;
    int size = table.seats().size();
    for (int i = 1; i <= size; i++) {
      int pos = (dealer + i) % size;
      Seat seat = table.seats().get(pos);
      if (seat.status() == Seat.Status.ACTIVE && !seat.isAllIn()) {
        return pos;
      }
    }
    return null;
  }

  private List<Integer> getActivePositions(Table table) {
    List<Integer> positions = new ArrayList<>();
    for (int i = 0; i < table.seats().size(); i++) {
      if (table.seats().get(i).status() == Seat.Status.ACTIVE) {
        positions.add(i);
      }
    }
    return positions;
  }

  // ========== Counting Helpers ==========

  /**
   * Counts players who are eligible to play in a hand. This includes ACTIVE seats
   * and JOINED_WAITING seats with a player that has chips and is not OUT or BUYING_IN.
   */
  private int countActivePlayers(Table table) {
    int count = 0;
    for (Seat seat : table.seats()) {
      if (seat.status() == Seat.Status.ACTIVE || seat.status() == Seat.Status.JOINED_WAITING) {
        if (isPlayerReadyToPlay(seat.player())) {
          count++;
        }
      }
    }
    return count;
  }

  private boolean isPlayerReadyToPlay(Player player) {
    if (player == null) return false;
    if (player.chipCount() == null || player.chipCount() <= 0) return false;
    // Only ACTIVE and AWAY players can be dealt into a hand
    return player.status() == PlayerStatus.ACTIVE || player.status() == PlayerStatus.AWAY;
  }

  private int countNonFoldedPlayers(Table table) {
    int count = 0;
    for (Seat seat : table.seats()) {
      if (seat.status() == Seat.Status.ACTIVE) {
        count++;
      }
    }
    return count;
  }

  private int countActiveNonAllInPlayers(Table table) {
    int count = 0;
    for (Seat seat : table.seats()) {
      if (seat.status() == Seat.Status.ACTIVE && !seat.isAllIn()) {
        count++;
      }
    }
    return count;
  }

  private boolean allNonFoldedAreAllIn(Table table) {
    for (Seat seat : table.seats()) {
      if (seat.status() == Seat.Status.ACTIVE && !seat.isAllIn()) {
        return false;
      }
    }
    return true;
  }

  private boolean noBuyingInPlayers(Table table) {
    for (Seat seat : table.seats()) {
      if (seat.player() != null && seat.player().status() == PlayerStatus.BUYING_IN) {
        return false;
      }
    }
    return true;
  }

  private boolean hasBuyingInPlayers(Table table) {
    return !noBuyingInPlayers(table);
  }

  // ========== Blind / Chip Helpers ==========

  private void postBlind(Table table, int position, int blindAmount) {
    Seat seat = table.seats().get(position);
    Player player = seat.player();
    if (player == null) return;

    int chips = player.chipCount() != null ? player.chipCount() : 0;
    int actualPost = Math.min(blindAmount, chips);
    player.chipCount(chips - actualPost);
    seat.currentBetAmount(actualPost);

    if (player.chipCount() <= 0) {
      seat.isAllIn(true);
    }
  }

  private void deductChips(Seat seat, int amount) {
    Player player = seat.player();
    if (player == null) return;
    int chips = player.chipCount() != null ? player.chipCount() : 0;
    player.chipCount(chips - amount);
  }

  // ========== Utility Helpers ==========

  private boolean isBettingPhase(HandPhase phase) {
    return phase == HandPhase.PRE_FLOP_BETTING ||
           phase == HandPhase.FLOP_BETTING ||
           phase == HandPhase.TURN_BETTING ||
           phase == HandPhase.RIVER_BETTING;
  }

  private int findPlayerSeat(Table table, String userId) {
    for (int i = 0; i < table.seats().size(); i++) {
      Seat seat = table.seats().get(i);
      if (seat.player() != null && seat.player().userId() != null
          && seat.player().userId().equals(userId)) {
        return i;
      }
    }
    return -1;
  }

  private void clearPendingIntents(Table table) {
    for (Seat seat : table.seats()) {
      seat.pendingIntent(null);
    }
  }
}
