package org.homepoker.game.cash;

import org.homepoker.game.GameManager;
import org.homepoker.model.game.cash.CashGame;
import org.homepoker.security.SecurityUtilities;
import org.homepoker.user.UserManager;

public class CashGameManager extends GameManager<CashGame> {

  private final CashGameService cashGameService;

  public CashGameManager(CashGame game, CashGameService cashGameService, UserManager userManager, SecurityUtilities securityUtilities) {
    super(game, userManager, securityUtilities);
    this.cashGameService = cashGameService;
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
