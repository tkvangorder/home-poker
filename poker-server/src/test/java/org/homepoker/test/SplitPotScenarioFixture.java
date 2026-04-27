package org.homepoker.test;

import org.homepoker.game.GameSettings;
import org.homepoker.game.cash.CashGameManager;
import org.homepoker.game.table.TableManager;
import org.homepoker.model.command.GameCommand;
import org.homepoker.model.command.StartGame;
import org.homepoker.model.event.PokerEvent;
import org.homepoker.model.game.GameStatus;
import org.homepoker.model.game.GameType;
import org.homepoker.model.game.Player;
import org.homepoker.model.game.PlayerStatus;
import org.homepoker.model.game.Seat;
import org.homepoker.model.game.Table;
import org.homepoker.model.game.cash.CashGame;
import org.homepoker.model.user.User;
import org.homepoker.poker.Deck;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Fixture for split-pot scenarios. Builds a single-table {@link CashGame} with caller-
 * specified per-seat stacks, installs a {@link Supplier} of a stacked {@link Deck}, and
 * advances the game to {@code PRE_FLOP_BETTING}.
 * <p>
 * The dealer is pre-set so seat 1 is the dealer after rotation: SB = 2, BB = 3, UTG = 4.
 */
public final class SplitPotScenarioFixture {

  private final FixtureGameManager manager;
  private final String tableId;
  private final int initialTotalChips;

  private SplitPotScenarioFixture(FixtureGameManager manager, String tableId, int initialTotalChips) {
    this.manager = manager;
    this.tableId = tableId;
    this.initialTotalChips = initialTotalChips;
  }

  public static Builder builder() {
    return new Builder();
  }

  public CashGame game() {
    return manager.getGame();
  }

  public Table table() {
    return game().tables().get(tableId);
  }

  public String tableId() {
    return tableId;
  }

  public Seat seatAt(int position) {
    return table().seatAt(position);
  }

  public List<PokerEvent> savedEvents() {
    return manager.savedEvents();
  }

  public int initialTotalChips() {
    return initialTotalChips;
  }

  public void submitCommand(GameCommand command) {
    manager.submitCommand(command);
  }

  public void tick() {
    manager.processGameTick();
  }

  public CashGameManager manager() {
    return manager;
  }

  public TableManager<CashGame> tableManager() {
    return manager.tableManagerFor(tableId);
  }

  public static final class Builder {
    private int[] stacks;
    private int smallBlind = 25;
    private int bigBlind = 50;
    private Deck deck;

    public Builder stacks(int... stacks) {
      this.stacks = stacks;
      return this;
    }

    public Builder smallBlind(int v) { this.smallBlind = v; return this; }
    public Builder bigBlind(int v) { this.bigBlind = v; return this; }
    public Builder deck(Deck deck) { this.deck = deck; return this; }

    public SplitPotScenarioFixture build() {
      if (stacks == null || stacks.length < 2) {
        throw new IllegalStateException("Specify at least two stacks");
      }
      if (deck == null) {
        throw new IllegalStateException("deck() is required");
      }

      User owner = TestDataHelper.adminUser();
      String tableId = "TABLE-0";
      CashGame game = CashGame.builder()
          .id("split-pot-test")
          .name("Split Pot Test Game")
          .type(GameType.TEXAS_HOLDEM)
          .status(GameStatus.SEATING)
          .startTime(Instant.now())
          .maxBuyIn(1_000_000)
          .smallBlind(smallBlind)
          .bigBlind(bigBlind)
          .owner(owner)
          .build();

      Table table = Table.builder()
          .id(tableId)
          .emptySeats(GameSettings.TEXAS_HOLDEM_SETTINGS.numberOfSeats())
          .status(Table.Status.PAUSED)
          .build();

      // Pre-set dealer so the first rotation places the dealer on seat 1.
      table.dealerPosition(stacks.length);
      game.tables().put(table.id(), table);

      int totalChips = 0;
      for (int i = 0; i < stacks.length; i++) {
        String uniqueId = "split-pot-player-" + (i + 1);
        User user = TestDataHelper.user(uniqueId, "password", "Player " + (i + 1));
        Player player = Player.builder()
            .user(user)
            .status(PlayerStatus.ACTIVE)
            .chipCount(stacks[i])
            .buyInTotal(stacks[i])
            .reBuys(0)
            .addOns(0)
            .build();
        game.addPlayer(player);

        Seat seat = table.seats().get(i);
        seat.status(Seat.Status.JOINED_WAITING);
        seat.player(player);
        player.tableId(tableId);
        totalChips += stacks[i];
      }

      // The supplier returns the same deck every time it is called. Tests for split pots
      // run a single hand, so the supplier is invoked exactly once.
      FixtureGameManager manager = new FixtureGameManager(game, () -> deck);
      manager.submitCommand(new StartGame(game.id(), owner));
      manager.processGameTick(); // SEATING -> ACTIVE
      manager.processGameTick(); // deals the first hand

      return new SplitPotScenarioFixture(manager, tableId, totalChips);
    }
  }

  /**
   * In-memory CashGameManager that captures all events and overrides the deck supplier.
   * <p>
   * The deck supplier is held in a {@link ThreadLocal} so the override works during the
   * super-constructor (which calls {@link #deckSupplier()} via
   * {@code createTableManagerForExistingTable} before any instance field of this subclass
   * has been initialized).
   */
  static final class FixtureGameManager extends CashGameManager {

    private static final ThreadLocal<Supplier<Deck>> PENDING_SUPPLIER = new ThreadLocal<>();

    private final List<PokerEvent> savedEvents = new ArrayList<>();
    private final Supplier<Deck> deckSupplier;

    FixtureGameManager(CashGame game, Supplier<Deck> deckSupplier) {
      super(initSupplier(game, deckSupplier), null, null, null, null);
      PENDING_SUPPLIER.remove();
      this.deckSupplier = deckSupplier;
      addGameListener(new org.homepoker.game.GameListener() {
        @Override public String userId() { return "split-pot-listener"; }
        @Override public boolean acceptsEvent(PokerEvent event) { return true; }
        @Override public void onEvent(PokerEvent event) { savedEvents.add(event); }
      });
    }

    /**
     * Stashes the deck supplier in a {@link ThreadLocal} before invoking {@code super(...)}
     * so {@link #deckSupplier()} can return it during the super constructor's call chain.
     */
    private static CashGame initSupplier(CashGame game, Supplier<Deck> supplier) {
      PENDING_SUPPLIER.set(supplier);
      return game;
    }

    @Override
    protected Supplier<Deck> deckSupplier() {
      Supplier<Deck> pending = PENDING_SUPPLIER.get();
      if (pending != null) return pending;
      return deckSupplier != null ? deckSupplier : Deck::new;
    }

    @Override
    protected CashGame persistGameState(CashGame game) {
      return game;
    }

    public CashGame getGame() { return game(); }

    public List<PokerEvent> savedEvents() { return savedEvents; }

    TableManager<CashGame> tableManagerFor(String tableId) {
      return tableManagers().get(tableId);
    }
  }
}
