package org.homepoker.game.cash;

import org.homepoker.game.GameManager;
import org.homepoker.security.SecurityUtilities;
import org.homepoker.user.UserManager;

public class CashGameManager extends GameManager<CashGame> {

  private final CashGameService cashGameService;

  public CashGameManager(CashGame game, CashGameService cashGameService, UserManager userManager, SecurityUtilities securityUtilities) {
    super(game, userManager, securityUtilities);
    this.cashGameService = cashGameService;
  }

  @Override
  protected CashGame persistGameState(CashGame game) {
    return cashGameService.saveGame(game);
  }
}
