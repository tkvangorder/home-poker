package org.homepoker.game.cash;

import org.homepoker.game.GameManager;

public class CashGameManager extends GameManager<CashGame> {

  private final CashGame game;

  public CashGameManager(CashGame game) {
    this.game = game;
  }

  @Override
  protected CashGame getGame() {
    return game;
  }
}
