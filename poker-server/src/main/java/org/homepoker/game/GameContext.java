package org.homepoker.game;

import org.homepoker.model.event.PokerEvent;

import java.util.ArrayList;
import java.util.List;

public class GameContext {
  private final GameSettings settings;
  private final List<PokerEvent> events = new ArrayList<>();
  private boolean forceUpdate;

  public GameContext(GameSettings settings) {
    this.settings = settings;
  }

  public GameSettings settings() {
    return settings;
  }

  public boolean forceUpdate() {
    return forceUpdate;
  }
  public List<PokerEvent> events() {
    return events;
  }

  public void forceUpdate(boolean forceUpdate) {
    this.forceUpdate = forceUpdate;
  }

  public void queueEvent(PokerEvent event) {

    if (!event.isValid()) {
      throw new IllegalArgumentException("Event did not pass validation. eventType [" + event.eventType() + "]");
    }
    events.add(event);
  }
}
