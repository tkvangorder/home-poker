package org.homepoker.test;

import org.homepoker.game.cash.CashGameManager;

public class Assertions {
  public static CashManagerAssert assertThat(CashGameManager cashGameManager) {
    return new CashManagerAssert(cashGameManager);
  }
}
