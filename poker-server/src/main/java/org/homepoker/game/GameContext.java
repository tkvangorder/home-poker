package org.homepoker.game;

import lombok.With;
import org.homepoker.event.PokerEvent;
import org.homepoker.model.game.GameStatus;

import java.util.ArrayList;
import java.util.List;

public record GameContext<T extends Game<T>>(@With T game, GameSettings settings, List<PokerEvent> events, @With boolean forceUpdate) {
  public GameContext(T game, GameSettings settings) {
    this(game, settings, List.of(), false);
  }

  public GameStatus gameStatus() {
    return game.status();
  }

  public GameContext<T> queueEvent(PokerEvent event) {

    if (!event.isValid()) {
      throw new IllegalArgumentException("Event did not pass validation. eventType [" + event.eventType() + "]");
    }

    List<PokerEvent> events = new ArrayList<>(this.events);
    events.add(event);
    return this.withEvents(events);
  }

  private GameContext<T> withEvents(List<PokerEvent> events) {
    return new GameContext<>(game, settings, events, forceUpdate);
  }
}
