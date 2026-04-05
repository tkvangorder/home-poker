package org.homepoker.game.table;

import org.homepoker.game.GameContext;
import org.homepoker.game.GameSettings;
import org.homepoker.lib.exception.ValidationException;
import org.homepoker.model.command.*;
import org.homepoker.model.event.table.*;
import org.homepoker.model.game.*;
import org.homepoker.model.game.Seat.SeatCard;
import static org.homepoker.model.game.HandPlayerStatuses.potTotal;
import org.homepoker.model.poker.Card;
import org.homepoker.poker.BitwisePokerRanker;
import org.homepoker.poker.ClassicPokerRanker;
import org.homepoker.poker.Deck;
import org.homepoker.poker.HandResult;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.*;

public class TexasHoldemTableManager<T extends Game<T>> extends TableManager<T> {

  private final ClassicPokerRanker pokerRanker = new BitwisePokerRanker();

  @Nullable
  private Deck deck;

  private TexasHoldemTableManager(GameSettings gameSettings, Table table) {
    super(gameSettings, table);
  }

  /**
   * Creates a new table manager for a brand-new table with empty seats.
   */
  public static <T extends Game<T>> TexasHoldemTableManager<T> forNewTable(String tableId, GameSettings settings) {
    List<Seat> seats = new ArrayList<>();
    for (int i = 0; i < settings.numberOfSeats(); i++) {
      seats.add(Seat.builder().build());
    }
    Table table = Table.builder()
        .id(tableId)
        .seats(seats)
        .build();
    return new TexasHoldemTableManager<>(settings, table);
  }

  /**
   * Creates a table manager for an existing (persisted) table. If the table is mid-hand,
   * the deck is recovered from the dealt cards.
   */
  public static <T extends Game<T>> TexasHoldemTableManager<T> forExistingTable(Table table, GameSettings settings) {
    TexasHoldemTableManager<T> manager = new TexasHoldemTableManager<>(settings, table);
    manager.recoverDeck();
    return manager;
  }

  /**
   * If the table is mid-hand, reconstructs the deck from dealt cards (hole cards + community cards).
   */
  private void recoverDeck() {
    if (table.handPhase() == HandPhase.WAITING_FOR_PLAYERS ||
        table.handPhase() == HandPhase.PREDEAL ||
        table.handPhase() == HandPhase.DEAL) {
      return;
    }
    List<Card> dealt = new ArrayList<>();
    for (Seat seat : table.seats()) {
      if (seat.cards() != null) {
        for (SeatCard sc : seat.cards()) {
          dealt.add(sc.card());
        }
      }
    }
    dealt.addAll(table.communityCards());
    this.deck = Deck.fromRemainingCards(dealt);
  }

  @Override
  public void transitionTable(Game<T> game, GameContext gameContext) {
    if (table.status() != Table.Status.PLAYING && table.status() != Table.Status.PAUSE_AFTER_HAND) {
      return;
    }

    switch (table.handPhase()) {
      case WAITING_FOR_PLAYERS -> transitionFromWaitingForPlayers(game, gameContext);
      case PREDEAL -> transitionFromPredeal(game, gameContext);
      case DEAL -> transitionFromDeal(game, gameContext);
      case PRE_FLOP_BETTING -> transitionFromBetting(game, gameContext, HandPhase.FLOP);
      case FLOP -> transitionFromCommunityCard(game, gameContext, 3, HandPhase.FLOP, HandPhase.FLOP_BETTING);
      case FLOP_BETTING -> transitionFromBetting(game, gameContext, HandPhase.TURN);
      case TURN -> transitionFromCommunityCard(game, gameContext, 1, HandPhase.TURN, HandPhase.TURN_BETTING);
      case TURN_BETTING -> transitionFromBetting(game, gameContext, HandPhase.RIVER);
      case RIVER -> transitionFromCommunityCard(game, gameContext, 1, HandPhase.RIVER, HandPhase.RIVER_BETTING);
      case RIVER_BETTING -> transitionFromBetting(game, gameContext, HandPhase.SHOWDOWN);
      case SHOWDOWN -> transitionFromShowdown(game, gameContext);
      case HAND_COMPLETE -> transitionFromHandComplete(game, gameContext);
    }
  }

  @Override
  protected void applySubcommand(GameCommand command, Game<T> game, GameContext gameContext) {
    switch (command) {
      case PlayerActionCommand pac -> applyPlayerAction(pac, game, gameContext);
      case PlayerIntent pi -> applyPlayerIntent(pi);
      case ShowCards sc -> applyShowCards(sc);
      case PostBlind pb -> applyPostBlind(pb);
      default -> throw new ValidationException("Unknown table command: " + command.commandId());
    }
  }

  // ========== State Transition Methods ==========

  private void transitionFromWaitingForPlayers(Game<T> game, GameContext gameContext) {
    // If PAUSE_AFTER_HAND, transition to PAUSED since no hand is in progress
    if (table.status() == Table.Status.PAUSE_AFTER_HAND) {
      Table.Status oldStatus = table.status();
      table.status(Table.Status.PAUSED);
      gameContext.queueEvent(new TableStatusChanged(Instant.now(), game.id(), table.id(), oldStatus, Table.Status.PAUSED));
      return;
    }

    int activeCount = countActivePlayers();
    if (activeCount >= 2) {
      // Skip PREDEAL for now and go directly to DEAL
      setHandPhase(HandPhase.DEAL, game, gameContext);
      transitionFromDeal(game, gameContext);
    }
  }

  private void transitionFromPredeal(Game<T> game, GameContext gameContext) {
    Instant phaseStarted = table.phaseStartedAt();
    if (phaseStarted == null) {
      table.phaseStartedAt(Instant.now());
      return;
    }

    boolean timerExpired = phaseStarted.plusSeconds(gameSettings().predealTimeSeconds()).isBefore(Instant.now());
    boolean noBuyingInPlayers = noBuyingInPlayers();

    if (timerExpired || noBuyingInPlayers) {
      // Activate JOINED_WAITING players
      activateWaitingPlayers();

      int activeCount = countActivePlayers();
      if (activeCount >= 2) {
        table.phaseStartedAt(null);
        setHandPhase(HandPhase.DEAL, game, gameContext);
        transitionFromDeal(game, gameContext);
      } else {
        table.phaseStartedAt(null);
        setHandPhase(HandPhase.WAITING_FOR_PLAYERS, game, gameContext);
        gameContext.queueEvent(new WaitingForPlayers(
            Instant.now(), game.id(), table.id(), activeCount, countSeatedPlayers()));
      }
    }
  }

  private void transitionFromDeal(Game<T> game, GameContext gameContext) {
    int smallBlind = game.smallBlind();
    int bigBlind = game.bigBlind();

    // Activate JOINED_WAITING players for this hand
    activateWaitingPlayers();

    int activeCount = countActivePlayers();
    if (activeCount < 2) {
      setHandPhase(HandPhase.WAITING_FOR_PLAYERS, game, gameContext);
      gameContext.queueEvent(new WaitingForPlayers(
          Instant.now(), game.id(), table.id(), activeCount, countSeatedPlayers()));
      return;
    }

    // Rotate dealer button
    int dealerPosition = rotateDealerButton();
    table.dealerPosition(dealerPosition);

    // Compute blind positions
    boolean headsUp = activeCount == 2;
    int sbPosition;
    int bbPosition;

    if (headsUp) {
      // Heads-up: dealer is small blind
      sbPosition = dealerPosition;
      bbPosition = nextActivePosition(dealerPosition);
    } else {
      sbPosition = nextActivePosition(dealerPosition);
      bbPosition = nextActivePosition(sbPosition);
    }

    table.smallBlindPosition(sbPosition);
    table.bigBlindPosition(bbPosition);

    // Create a new deck
    this.deck = new Deck();

    // Post blinds
    postBlind(sbPosition, smallBlind);
    postBlind(bbPosition, bigBlind);

    // Set current bet and minimum raise
    table.currentBet(bigBlind);
    table.minimumRaise(bigBlind);

    // Deal 2 hole cards to each ACTIVE seat
    int numberOfSeats = table.seats().size();
    for (int pos = 1; pos <= numberOfSeats; pos++) {
      Seat seat = table.seatAt(pos);
      if (seat.status() == Seat.Status.ACTIVE) {
        List<Card> cards = deck.drawCards(2);
        List<SeatCard> seatCards = cards.stream()
            .map(c -> new SeatCard(c, false))
            .toList();
        seat.cards(new ArrayList<>(seatCards));
      }
    }

    // Set action position (UTG = first active seat after BB)
    int utgPosition = nextActiveNonAllInPosition(bbPosition);
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
        sbPosition, bbPosition, smallBlind, bigBlind,
        table.currentBet(), table.minimumRaise(),
        HandPlayerStatuses.snapshot(table)
    ));

    // Emit HoleCardsDealt per player
    for (int pos = 1; pos <= numberOfSeats; pos++) {
      Seat seat = table.seatAt(pos);
      if (seat.status() == Seat.Status.ACTIVE && seat.cards() != null && seat.player() != null) {
        gameContext.queueEvent(new HoleCardsDealt(
            Instant.now(), game.id(), table.id(),
            seat.player().userId(), pos, seat.cards()
        ));
      }
    }

    // Advance to PRE_FLOP_BETTING
    setHandPhase(HandPhase.PRE_FLOP_BETTING, game, gameContext);
    table.phaseStartedAt(Instant.now());

    // Emit action-on event for UTG
    emitActionOnPlayer(game, gameContext);

    gameContext.forceUpdate(true);
  }

  private void transitionFromBetting(Game<T> game, GameContext gameContext, HandPhase nextPhase) {
    // Check player timeout
    if (table.actionDeadline() != null && table.actionDeadline().isBefore(Instant.now())) {
      Integer actionPos = table.actionPosition();
      if (actionPos != null) {
        Seat actionSeat = table.seatAt(actionPos);
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

          executePlayerAction(actionPos, actionSeat, defaultAction, game, gameContext);
        }
      }
    }

    // Check if all but one have folded
    int activeSeatCount = countNonFoldedPlayers();
    if (activeSeatCount <= 1) {
      // Award pot to the last remaining player
      awardPotToLastPlayer(game, gameContext);
      return;
    }

    // Check if betting round is complete
    if (isBettingRoundComplete()) {
      collectBetsIntoPots();

      gameContext.queueEvent(new BettingRoundComplete(
          Instant.now(), game.id(), table.id(),
          table.handPhase(), table.pots(),
          HandPlayerStatuses.snapshot(table), potTotal(table)
      ));

      // Check for all-in shortcut: if all non-folded players are all-in, deal remaining cards
      if (allNonFoldedAreAllIn() || countActiveNonAllInPlayers() <= 1) {
        // Deal remaining community cards and go to showdown
        dealRemainingCommunityCards(game, gameContext);
        setHandPhase(HandPhase.SHOWDOWN, game, gameContext);
        transitionFromShowdown(game, gameContext);
        return;
      }

      // Advance to next phase
      setHandPhase(nextPhase, game, gameContext);

      if (nextPhase == HandPhase.SHOWDOWN) {
        transitionFromShowdown(game, gameContext);
      }
      // FLOP/TURN/RIVER are transient - they'll be handled on the next tick
    }
  }

  private void transitionFromCommunityCard(Game<T> game, GameContext gameContext,
                                           int cardCount, HandPhase phase, HandPhase nextBettingPhase) {
    if (deck == null) {
      throw new IllegalStateException("No deck found for table " + table.id());
    }

    List<Card> newCards = deck.drawCards(cardCount);
    table.communityCards().addAll(newCards);

    gameContext.queueEvent(new CommunityCardsDealt(
        Instant.now(), game.id(), table.id(), newCards, phase, List.copyOf(table.communityCards())
    ));

    // Clear actions and intents from previous betting round
    for (Seat seat : table.seats()) {
      seat.action(null);
      seat.pendingIntent(null);
    }

    // Reset for new betting round
    table.currentBet(0);
    table.minimumRaise(game.bigBlind());

    // Set action position to first active non-all-in seat left of dealer
    Integer firstActor = firstActiveNonAllInAfterDealer();
    if (firstActor == null) {
      // All remaining players are all-in, skip to showdown
      setHandPhase(HandPhase.SHOWDOWN, game, gameContext);
      transitionFromShowdown(game, gameContext);
      return;
    }

    table.actionPosition(firstActor);
    table.lastRaiserPosition(firstActor); // Post-flop: first actor determines round completion
    table.actionDeadline(Instant.now().plusSeconds(gameSettings().actionTimeSeconds()));

    setHandPhase(nextBettingPhase, game, gameContext);
    table.phaseStartedAt(Instant.now());

    // Emit action-on event for the first actor in this betting round
    emitActionOnPlayer(game, gameContext);

    // Apply pending intent if the current player has one
    tryApplyPendingIntent(game, gameContext);
  }

  private void transitionFromShowdown(Game<T> game, GameContext gameContext) {
    // Evaluate hands and award pots
    List<ShowdownResult.PotResult> potResults = new ArrayList<>();

    for (int potIndex = 0; potIndex < table.pots().size(); potIndex++) {
      Table.Pot pot = table.pots().get(potIndex);
      List<ShowdownResult.Winner> winners = evaluatePotWinners(pot, potIndex);
      potResults.add(new ShowdownResult.PotResult(potIndex, pot.amount(), winners));
    }

    gameContext.queueEvent(new ShowdownResult(
        Instant.now(), game.id(), table.id(), potResults
    ));

    // Mark winning cards as showCard = true
    markShowdownCards(potResults);

    setHandPhase(HandPhase.HAND_COMPLETE, game, gameContext);
    table.phaseStartedAt(Instant.now());
    gameContext.forceUpdate(true);
  }

  private void transitionFromHandComplete(Game<T> game, GameContext gameContext) {
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
    handlePostHandPlayerStatus(game);

    // Clear hand state
    clearHandState();

    // Clean up deck
    this.deck = null;

    // Check for PAUSE_AFTER_HAND
    if (table.status() == Table.Status.PAUSE_AFTER_HAND) {
      Table.Status oldStatus = table.status();
      table.status(Table.Status.PAUSED);
      setHandPhase(HandPhase.WAITING_FOR_PLAYERS, game, gameContext);
      gameContext.queueEvent(new TableStatusChanged(Instant.now(), game.id(), table.id(), oldStatus, Table.Status.PAUSED));
      gameContext.forceUpdate(true);
      return;
    }

    // Determine next phase
    if (hasBuyingInPlayers()) {
      setHandPhase(HandPhase.PREDEAL, game, gameContext);
      table.phaseStartedAt(Instant.now());
    } else {
      int activeCount = countActivePlayers();
      if (activeCount >= 2) {
        setHandPhase(HandPhase.DEAL, game, gameContext);
        transitionFromDeal(game, gameContext);
      } else {
        setHandPhase(HandPhase.WAITING_FOR_PLAYERS, game, gameContext);
        gameContext.queueEvent(new WaitingForPlayers(
            Instant.now(), game.id(), table.id(), activeCount, countSeatedPlayers()));
      }
    }
    gameContext.forceUpdate(true);
  }

  // ========== Command Handlers ==========

  private void applyPlayerAction(PlayerActionCommand command, Game<T> game, GameContext gameContext) {
    // Validate it's a betting phase
    if (!isBettingPhase(table.handPhase())) {
      throw new ValidationException("Player actions are only valid during a betting phase.");
    }

    // Find the player's seat
    int seatPosition = findPlayerSeat(command.user().id());
    if (seatPosition < 0) {
      throw new ValidationException("You are not seated at this table.");
    }

    // Validate it's the player's turn
    if (table.actionPosition() == null || table.actionPosition() != seatPosition) {
      throw new ValidationException("It is not your turn to act.");
    }

    Seat seat = table.seatAt(seatPosition);
    if (seat.status() != Seat.Status.ACTIVE || seat.isAllIn()) {
      throw new ValidationException("You cannot act in your current state.");
    }

    // Validate and execute the action
    validateAction(command.action(), seat, game);
    executePlayerAction(seatPosition, seat, command.action(), game, gameContext);
  }

  private void applyPlayerIntent(PlayerIntent command) {
    if (!isBettingPhase(table.handPhase())) {
      throw new ValidationException("Intents are only valid during a betting phase.");
    }

    int seatPosition = findPlayerSeat(command.user().id());
    if (seatPosition < 0) {
      throw new ValidationException("You are not seated at this table.");
    }

    Seat seat = table.seatAt(seatPosition);
    if (seat.status() != Seat.Status.ACTIVE) {
      throw new ValidationException("You cannot set an intent in your current state.");
    }

    seat.pendingIntent(command.action());
  }

  private void applyShowCards(ShowCards command) {
    if (table.handPhase() != HandPhase.HAND_COMPLETE) {
      throw new ValidationException("You can only show cards during the hand review period.");
    }

    int seatPosition = findPlayerSeat(command.user().id());
    if (seatPosition < 0) {
      throw new ValidationException("You are not seated at this table.");
    }

    Seat seat = table.seatAt(seatPosition);
    if (seat.cards() != null) {
      List<SeatCard> shownCards = seat.cards().stream()
          .map(sc -> sc.withShowCard(true))
          .toList();
      seat.cards(new ArrayList<>(shownCards));
    }
  }

  private void applyPostBlind(PostBlind command) {
    if (table.handPhase() != HandPhase.PREDEAL && table.handPhase() != HandPhase.WAITING_FOR_PLAYERS) {
      throw new ValidationException("You can only post a blind during the predeal phase.");
    }

    int seatPosition = findPlayerSeat(command.user().id());
    if (seatPosition < 0) {
      throw new ValidationException("You are not seated at this table.");
    }

    Seat seat = table.seatAt(seatPosition);
    seat.mustPostBlind(false);
    seat.missedBigBlind(false);
  }

  // ========== Action Execution ==========

  private void executePlayerAction(int seatPosition, Seat seat, PlayerAction action, Game<T> game, GameContext gameContext) {
    Player player = seat.player();
    int chipsBefore = player != null ? player.chipCount() : 0;

    switch (action) {
      case PlayerAction.Fold fold -> {
        seat.status(Seat.Status.FOLDED);
        seat.action(fold);
      }
      case PlayerAction.Check check -> seat.action(check);
      case PlayerAction.Call _ -> {
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

    // If aggressive action (bet/raise), clear other active players' actions
    // so they must act again before the round can complete
    if (action instanceof PlayerAction.Bet || action instanceof PlayerAction.Raise) {
      int size = table.seats().size();
      for (int pos = 1; pos <= size; pos++) {
        if (pos != seatPosition) {
          Seat s = table.seatAt(pos);
          if (s.status() == Seat.Status.ACTIVE && !s.isAllIn()) {
            s.action(null);
          }
        }
      }
    }

    // Emit event
    int chipCount = player != null ? player.chipCount() : 0;
    HandPlayerStatus resultingStatus = HandPlayerStatuses.from(table, seat, seatPosition);
    gameContext.queueEvent(new PlayerActed(
        Instant.now(), game.id(), table.id(), seatPosition,
        player != null ? player.userId() : "unknown",
        action, chipCount, resultingStatus,
        table.currentBet(), table.minimumRaise(), potTotal(table)
    ));

    // Advance action position
    advanceActionPosition(seatPosition, game, gameContext);

    // Try to apply pending intent for the next player
    tryApplyPendingIntent(game, gameContext);
  }

  // ========== Event Helpers ==========

  /**
   * Emits an ActionOnPlayer event for the current action position, if set.
   */
  private void emitActionOnPlayer(Game<T> game, GameContext gameContext) {
    Integer actionPos = table.actionPosition();
    if (actionPos == null) return;

    Seat seat = table.seatAt(actionPos);
    String userId = seat.player() != null ? seat.player().userId() : "unknown";
    Instant deadline = table.actionDeadline() != null ? table.actionDeadline() : Instant.now();
    int playerChips = seat.player() != null ? seat.player().chipCount() : 0;
    int callAmount = Math.max(0, table.currentBet() - seat.currentBetAmount());

    gameContext.queueEvent(new ActionOnPlayer(
        Instant.now(), game.id(), table.id(), actionPos, userId, deadline,
        table.currentBet(), table.minimumRaise(), callAmount, playerChips, potTotal(table)
    ));
  }

  /**
   * Sets the table's hand phase and emits {@link HandPhaseChanged} if the phase changed.
   */
  private void setHandPhase(HandPhase newPhase, Game<T> game, GameContext gameContext) {
    HandPhase oldPhase = table.handPhase();
    if (oldPhase == newPhase) {
      return;
    }
    table.handPhase(newPhase);
    gameContext.queueEvent(new HandPhaseChanged(
        Instant.now(), game.id(), table.id(), oldPhase, newPhase
    ));
  }

  private int countSeatedPlayers() {
    int count = 0;
    for (Seat seat : table.seats()) {
      if (seat.status() != Seat.Status.EMPTY) count++;
    }
    return count;
  }

  // ========== Betting Logic ==========

  private void validateAction(PlayerAction action, Seat seat, Game<T> game) {
    int chipCount = seat.player() != null ? seat.player().chipCount() : 0;

    switch (action) {
      case PlayerAction.Fold _ -> {
        // Always valid
      }
      case PlayerAction.Check _ -> {
        if (seat.currentBetAmount() < table.currentBet()) {
          throw new ValidationException("You cannot check when there is a bet to call.");
        }
      }
      case PlayerAction.Call _ -> {
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

  private boolean isBettingRoundComplete() {
    // The round is complete when every active non-all-in player has acted.
    // When a bet/raise occurs, other players' actions are cleared so they must act again.
    for (Seat seat : table.seats()) {
      if (seat.status() == Seat.Status.ACTIVE && !seat.isAllIn() && seat.action() == null) {
        return false;
      }
    }
    return true;
  }

  private void advanceActionPosition(int currentPosition, Game<T> game, GameContext gameContext) {
    Integer nextPos = nextActiveNonAllInPositionOrNull(currentPosition);
    if (nextPos == null) {
      // No more players can act
      table.actionPosition(table.lastRaiserPosition());
      return;
    }

    // If we've come back to the last raiser and they've already acted, round is complete
    table.actionPosition(nextPos);
    table.actionDeadline(Instant.now().plusSeconds(gameSettings().actionTimeSeconds()));

    // Emit action-on event for the next player
    emitActionOnPlayer(game, gameContext);
  }

  private void tryApplyPendingIntent(Game<T> game, GameContext gameContext) {
    Integer actionPos = table.actionPosition();
    if (actionPos == null) return;

    Seat seat = table.seatAt(actionPos);
    if (seat.pendingIntent() == null || seat.status() != Seat.Status.ACTIVE || seat.isAllIn()) return;

    // Validate the pending intent is still legal
    try {
      validateAction(seat.pendingIntent(), seat, game);
      // Intent is valid, auto-execute it
      PlayerAction intent = seat.pendingIntent();
      seat.pendingIntent(null);
      executePlayerAction(actionPos, seat, intent, game, gameContext);
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
  private void collectBetsIntoPots() {
    // Gather all non-zero bets with their 1-indexed seat positions
    List<int[]> bets = new ArrayList<>(); // [seatPosition, betAmount]
    int size = table.seats().size();
    for (int pos = 1; pos <= size; pos++) {
      Seat seat = table.seatAt(pos);
      if (seat.currentBetAmount() > 0) {
        bets.add(new int[]{pos, seat.currentBetAmount()});
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
          Seat seat = table.seatAt(b[0]);
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

  private void awardPotToLastPlayer(Game<T> game, GameContext gameContext) {
    // Collect any outstanding bets first
    collectBetsIntoPots();

    // Find the last non-folded player (1-indexed position)
    int winnerPosition = -1;
    int numberOfSeats = table.seats().size();
    for (int pos = 1; pos <= numberOfSeats; pos++) {
      Seat seat = table.seatAt(pos);
      if (seat.status() == Seat.Status.ACTIVE) {
        winnerPosition = pos;
        break;
      }
    }

    if (winnerPosition < 0) return;

    Seat winnerSeat = table.seatAt(winnerPosition);
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

    setHandPhase(HandPhase.HAND_COMPLETE, game, gameContext);
    table.phaseStartedAt(Instant.now());
    gameContext.forceUpdate(true);
  }

  // ========== Showdown Logic ==========

  private List<ShowdownResult.Winner> evaluatePotWinners(Table.Pot pot, int potIndex) {
    List<ShowdownResult.Winner> winners = new ArrayList<>();

    // Build hand results for eligible seats
    record SeatHandResult(int position, HandResult result, Seat seat) {}
    List<SeatHandResult> results = new ArrayList<>();

    for (int position : pot.seatPositions()) {
      Seat seat = table.seatAt(position);
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

  private void markShowdownCards(List<ShowdownResult.PotResult> potResults) {
    Set<Integer> winnerPositions = new HashSet<>();
    for (ShowdownResult.PotResult pr : potResults) {
      for (ShowdownResult.Winner w : pr.winners()) {
        winnerPositions.add(w.seatPosition());
      }
    }

    for (int pos : winnerPositions) {
      Seat seat = table.seatAt(pos);
      if (seat.cards() != null) {
        List<SeatCard> shown = seat.cards().stream()
            .map(sc -> sc.withShowCard(true))
            .toList();
        seat.cards(new ArrayList<>(shown));
      }
    }
  }

  private void dealRemainingCommunityCards(Game<T> game, GameContext gameContext) {
    if (deck == null) return;

    int currentCount = table.communityCards().size();
    if (currentCount < 3) {
      List<Card> flop = deck.drawCards(3);
      table.communityCards().addAll(flop);
      gameContext.queueEvent(new CommunityCardsDealt(
          Instant.now(), game.id(), table.id(), flop, HandPhase.FLOP, List.copyOf(table.communityCards())));
      currentCount = 3;
    }
    if (currentCount < 4) {
      List<Card> turn = deck.drawCards(1);
      table.communityCards().addAll(turn);
      gameContext.queueEvent(new CommunityCardsDealt(
          Instant.now(), game.id(), table.id(), turn, HandPhase.TURN, List.copyOf(table.communityCards())));
      currentCount = 4;
    }
    if (currentCount < 5) {
      List<Card> river = deck.drawCards(1);
      table.communityCards().addAll(river);
      gameContext.queueEvent(new CommunityCardsDealt(
          Instant.now(), game.id(), table.id(), river, HandPhase.RIVER, List.copyOf(table.communityCards())));
    }
  }

  // ========== Hand Complete Helpers ==========

  private void handlePostHandPlayerStatus(Game<T> game) {
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
      if (player.chipCount() <= 0) {
        seat.status(Seat.Status.JOINED_WAITING);
        player.status(PlayerStatus.BUYING_IN);
      }
    }
  }

  private void clearHandState() {
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

  private void activateWaitingPlayers() {
    for (Seat seat : table.seats()) {
      if (seat.status() == Seat.Status.JOINED_WAITING && isPlayerReadyToPlay(seat.player())) {
        seat.status(Seat.Status.ACTIVE);
      }
    }
  }

  // ========== Position Helpers ==========

  private int rotateDealerButton() {
    Integer current = table.dealerPosition();
    if (current == null) {
      // First hand: pick a random active position
      List<Integer> activePositions = getActivePositions();
      return activePositions.get(new Random().nextInt(activePositions.size()));
    }
    return nextActivePosition(current);
  }

  /**
   * Finds the next ACTIVE seat position (1-indexed) clockwise from the given position.
   */
  private int nextActivePosition(int fromPosition) {
    int size = table.seats().size();
    for (int i = 1; i <= size; i++) {
      int pos = ((fromPosition - 1 + i) % size) + 1;
      Seat seat = table.seatAt(pos);
      if (seat.status() == Seat.Status.ACTIVE) {
        return pos;
      }
    }
    return fromPosition; // Shouldn't happen with 2+ active players
  }

  /**
   * Finds the next ACTIVE, non-all-in seat position (1-indexed) clockwise from the given position.
   */
  private int nextActiveNonAllInPosition(int fromPosition) {
    int size = table.seats().size();
    for (int i = 1; i <= size; i++) {
      int pos = ((fromPosition - 1 + i) % size) + 1;
      Seat seat = table.seatAt(pos);
      if (seat.status() == Seat.Status.ACTIVE && !seat.isAllIn()) {
        return pos;
      }
    }
    return fromPosition;
  }

  @Nullable
  private Integer nextActiveNonAllInPositionOrNull(int fromPosition) {
    int size = table.seats().size();
    for (int i = 1; i <= size; i++) {
      int pos = ((fromPosition - 1 + i) % size) + 1;
      if (pos == fromPosition) return null; // Wrapped around
      Seat seat = table.seatAt(pos);
      if (seat.status() == Seat.Status.ACTIVE && !seat.isAllIn()) {
        return pos;
      }
    }
    return null;
  }

  @Nullable
  private Integer firstActiveNonAllInAfterDealer() {
    Integer dealer = table.dealerPosition();
    if (dealer == null) return null;
    int size = table.seats().size();
    for (int i = 1; i <= size; i++) {
      int pos = ((dealer - 1 + i) % size) + 1;
      Seat seat = table.seatAt(pos);
      if (seat.status() == Seat.Status.ACTIVE && !seat.isAllIn()) {
        return pos;
      }
    }
    return null;
  }

  /** Returns all ACTIVE seat positions (1-indexed). */
  private List<Integer> getActivePositions() {
    List<Integer> positions = new ArrayList<>();
    int size = table.seats().size();
    for (int pos = 1; pos <= size; pos++) {
      if (table.seatAt(pos).status() == Seat.Status.ACTIVE) {
        positions.add(pos);
      }
    }
    return positions;
  }

  // ========== Counting Helpers ==========

  /**
   * Counts players who are eligible to play in a hand. This includes ACTIVE seats
   * and JOINED_WAITING seats with a player that has chips and is not OUT or BUYING_IN.
   */
  private int countActivePlayers() {
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

  private boolean isPlayerReadyToPlay(@Nullable Player player) {
    if (player == null) return false;
    if (player.chipCount() <= 0) return false;
    // Only ACTIVE and AWAY players can be dealt into a hand
    return player.status() == PlayerStatus.ACTIVE || player.status() == PlayerStatus.AWAY;
  }

  private int countNonFoldedPlayers() {
    int count = 0;
    for (Seat seat : table.seats()) {
      if (seat.status() == Seat.Status.ACTIVE) {
        count++;
      }
    }
    return count;
  }

  private int countActiveNonAllInPlayers() {
    int count = 0;
    for (Seat seat : table.seats()) {
      if (seat.status() == Seat.Status.ACTIVE && !seat.isAllIn()) {
        count++;
      }
    }
    return count;
  }

  private boolean allNonFoldedAreAllIn() {
    for (Seat seat : table.seats()) {
      if (seat.status() == Seat.Status.ACTIVE && !seat.isAllIn()) {
        return false;
      }
    }
    return true;
  }

  private boolean noBuyingInPlayers() {
    for (Seat seat : table.seats()) {
      if (seat.player() != null && seat.player().status() == PlayerStatus.BUYING_IN) {
        return false;
      }
    }
    return true;
  }

  private boolean hasBuyingInPlayers() {
    return !noBuyingInPlayers();
  }

  // ========== Blind / Chip Helpers ==========

  private void postBlind(int position, int blindAmount) {
    Seat seat = table.seatAt(position);
    Player player = seat.player();
    if (player == null) return;

    int chips = player.chipCount();
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
    player.chipCount(player.chipCount() - amount);
  }

  // ========== Utility Helpers ==========

  private boolean isBettingPhase(HandPhase phase) {
    return phase == HandPhase.PRE_FLOP_BETTING ||
           phase == HandPhase.FLOP_BETTING ||
           phase == HandPhase.TURN_BETTING ||
           phase == HandPhase.RIVER_BETTING;
  }

  /** Returns the 1-indexed seat position of the given user, or -1 if not seated. */
  private int findPlayerSeat(String userLoginId) {
    int size = table.seats().size();
    for (int pos = 1; pos <= size; pos++) {
      Seat seat = table.seatAt(pos);
      if (seat.player() != null && seat.player().userId().equals(userLoginId)) {
        return pos;
      }
    }
    return -1;
  }

}
