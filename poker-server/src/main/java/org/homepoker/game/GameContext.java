package org.homepoker.game;

import lombok.With;
import org.homepoker.event.GameEvent;
import org.homepoker.model.game.GameStatus;

import java.util.ArrayList;
import java.util.List;

public record GameContext<T extends Game>(@With T game, List<GameEvent> events) {
  public GameContext(T game) {
    this(game, List.of());
  }

  public GameStatus gameStatus() {
    return game.status();
  }

  public GameContext<T> queueEvent(GameEvent event) {
    List<GameEvent> events = new ArrayList<>(this.events);
    events.add(event);
    return this.withEvents(events);
  }

  private GameContext<T> withEvents(List<GameEvent> events) {
    return new GameContext<>(game, events);
  }
}
