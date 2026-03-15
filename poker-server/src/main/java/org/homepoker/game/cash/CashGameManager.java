package org.homepoker.game.cash;

import org.homepoker.game.GameContext;
import org.homepoker.game.GameManager;
import org.homepoker.lib.exception.ValidationException;
import org.homepoker.model.command.GameCommand;
import org.homepoker.model.command.RegisterForGame;
import org.homepoker.model.command.UnregisterFromGame;
import org.homepoker.model.event.game.UserRegistered;
import org.homepoker.model.event.game.UserUnregistered;
import org.homepoker.model.game.GameStatus;
import org.homepoker.model.game.Player;
import org.homepoker.model.game.PlayerStatus;
import org.homepoker.model.game.cash.CashGame;
import org.homepoker.security.SecurityUtilities;
import org.homepoker.user.UserManager;

import java.time.Instant;

import static org.homepoker.game.GameUtils.assignPlayerToTableWithFewestPlayers;

public class CashGameManager extends GameManager<CashGame> {

  private final CashGameService cashGameService;

  public CashGameManager(CashGame game, CashGameService cashGameService, UserManager userManager, SecurityUtilities securityUtilities) {
    super(game, userManager, securityUtilities);
    this.cashGameService = cashGameService;
  }

  @Override
  protected void applyGameSpecificCommand(GameCommand command, CashGame game, GameContext gameContext) {
    switch (command) {
      case RegisterForGame gameCommand -> registerForGame(gameCommand, game, gameContext);
      case UnregisterFromGame gameCommand -> unregisterFromGame(gameCommand, game, gameContext);
      default -> throw new ValidationException("Unsupported command: " + command.getClass().getSimpleName());
    }
  }

  private void registerForGame(RegisterForGame registerForGame, CashGame game, GameContext gameContext) {

    if (game.status() == GameStatus.COMPLETED) {
      throw new ValidationException("This game has already completed.");
    }

    if (game.players().containsKey(registerForGame.user().id())) {
      throw new ValidationException("You are already registered for this game.");
    }
    Player player = Player.builder().user(registerForGame.user()).status(PlayerStatus.REGISTERED).build();
    game.addPlayer(player);

    // During SEATING or ACTIVE, assign the player to a seat on the table with the fewest players
    if (game.status() == GameStatus.SEATING || game.status() == GameStatus.ACTIVE) {
      assignPlayerToTableWithFewestPlayers(player, game, gameSettings().numberOfSeats());
    }
    gameContext.queueEvent(new UserRegistered(Instant.now(), game.id(), player.userId()));
    gameContext.forceUpdate(true);
  }

  private void unregisterFromGame(UnregisterFromGame unregisterForGame, CashGame game, GameContext gameContext) {

    if (game.status() != GameStatus.SCHEDULED) {
      throw new ValidationException("You can only unregister from the game prior to it starting.");
    }
    Player player = game.players().get(unregisterForGame.user().id());
    if (player == null) {
      throw new ValidationException("You are not registered for this game.");
    } else if (player.status() != PlayerStatus.REGISTERED) {
      throw new ValidationException("You cannot unregister from the game after you joined.");
    }

    game.players().remove(player.userId());
    gameContext.queueEvent(new UserUnregistered(Instant.now(), game.id(), player.userId()));
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
    return new CashGameManager(game().copy(), cashGameService, userManager(), securityUtilities());
  }

  @Override
  protected CashGame persistGameState(CashGame game) {
    return cashGameService.saveGame(game);
  }
}
