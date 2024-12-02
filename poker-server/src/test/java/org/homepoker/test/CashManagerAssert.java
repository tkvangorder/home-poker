package org.homepoker.test;

import org.assertj.core.api.Assertions;
import org.assertj.core.api.ListAssert;
import org.homepoker.model.event.PokerEvent;
import org.homepoker.game.GameListener;
import org.homepoker.game.cash.CashGameManager;
import org.homepoker.model.command.GameCommand;

import java.util.ArrayList;
import java.util.List;

public class CashManagerAssert {

  private final CashGameManager cashGameManager;
  private final List<PokerEvent> events = new ArrayList<>();
  public CashManagerAssert(CashGameManager cashGameManager) {
    this.cashGameManager = cashGameManager.copy();
  }

  public CashManagerAssert processGameTick() {
    cashGameManager.processGameTick();
    return this;
  }

  public CashManagerAssert submitCommand(GameCommand command) {
    cashGameManager.submitCommand(command);
    return this;
  }

  public ListAssert<PokerEvent> events() {
    return Assertions.assertThat(events);
  }

  private class GameListenerAssert implements GameListener {

    @Override
    public String id() {
      return "test-listener";
    }

    @Override
    public void onEvent(PokerEvent event) {
      events.add(event);
    }

    @Override
    public boolean acceptsEvent(PokerEvent event) {
      return true;
    }
  }

}
