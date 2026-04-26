package org.homepoker.game.cash;

import org.homepoker.game.GameContext;
import org.homepoker.game.GameManager;
import org.homepoker.lib.exception.ValidationException;
import org.homepoker.model.command.GameCommand;
import org.homepoker.model.command.JoinGame;
import org.homepoker.model.event.game.PlayerSeated;
import org.homepoker.model.event.game.PlayerJoined;
import org.homepoker.model.game.GameStatus;
import org.homepoker.model.game.Player;
import org.homepoker.model.game.PlayerStatus;
import org.homepoker.model.game.cash.CashGame;
import org.homepoker.recording.EventRecorder;
import org.homepoker.recording.EventRecorderService;
import org.homepoker.security.SecurityUtilities;
import org.homepoker.user.UserManager;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.Map;

import static org.homepoker.game.GameUtils.assignPlayerToTableWithFewestPlayers;

public class CashGameManager extends GameManager<CashGame> {

  private final CashGameService cashGameService;

  public CashGameManager(CashGame game,
                         CashGameService cashGameService,
                         UserManager userManager,
                         SecurityUtilities securityUtilities,
                         @Nullable EventRecorderService eventRecorderService) {
    super(game, userManager, securityUtilities);
    this.cashGameService = cashGameService;

    if (eventRecorderService != null) {
      Map<String, Integer> seed = eventRecorderService.seedHandTracker();
      addGameListener(new EventRecorder(eventRecorderService, seed));
    }
  }

  @Override
  protected void applyGameSpecificCommand(GameCommand command, CashGame game, GameContext gameContext) {
    switch (command) {
      case JoinGame gameCommand -> joinGame(gameCommand, game, gameContext);
      default -> throw new ValidationException("Unsupported command: " + command.getClass().getSimpleName());
    }
  }

  private void joinGame(JoinGame joinGame, CashGame game, GameContext gameContext) {

    if (game.status() == GameStatus.COMPLETED) {
      throw new ValidationException("This game has already completed.");
    }

    Player player = game.players().get(joinGame.user().id());
    if (player != null) {
      if (player.status() == PlayerStatus.OUT) {
        // Player is rejoining after previously leaving
        player.status(PlayerStatus.AWAY);
      }
      // Otherwise the player is already in the game — allow them to rejoin (reconnect)
    } else {
      player = Player.builder().user(joinGame.user()).status(PlayerStatus.AWAY).build();
      game.addPlayer(player);
    }

    // During SEATING or ACTIVE, assign the player to a seat only if they have chips and are not already seated.
    // Players without chips must buy in first before being seated.
    if (player.tableId() == null && player.chipCount() > 0 && (game.status() == GameStatus.SEATING || game.status() == GameStatus.ACTIVE)) {
      String tableId = assignPlayerToTableWithFewestPlayers(player, game, gameSettings().numberOfSeats());
      if (tableId != null) {
        gameContext.queueEvent(new PlayerSeated(Instant.now(), 0L, game.id(), player.userId(), tableId));
      }
    }
    gameContext.queueEvent(new PlayerJoined(Instant.now(), 0L, game.id(), player.userId()));
    gameContext.forceUpdate(true);
  }

  /**
   * This is used to create a copy of the game manager for the purpose of integration testing. This should not be used
   * outside of testing.
   *
   * @return A copy of the game manager
   *
   */
  public CashGameManager copy() {
    return new CashGameManager(game().copy(), cashGameService, userManager(), securityUtilities(), null);
  }

  /**
   * Test-only accessor — exposes the in-memory {@link CashGame} so integration tests
   * can mutate state directly (e.g., flipping {@code GameStatus} to drive rare branches).
   * Do not use outside test fixtures.
   */
  public CashGame getGameForTest() {
    return gameForTestOnly();
  }

  @Override
  protected CashGame persistGameState(CashGame game) {
    return cashGameService.saveGame(game);
  }
}
