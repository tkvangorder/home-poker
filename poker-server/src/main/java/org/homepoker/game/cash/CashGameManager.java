package org.homepoker.game.cash;

import org.homepoker.game.GameManager;

public class CashGameManager extends GameManager<CashGame> {

  CashGameService cashGameService;

  public CashGameManager(CashGame game, CashGameService cashGameService) {
    super(game);
  }

  @Override
  protected CashGame persistGameState(CashGame game) {
    return cashGameService.saveGame(game);
  }
}
