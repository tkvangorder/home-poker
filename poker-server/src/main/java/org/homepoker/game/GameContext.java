package org.homepoker.game;

import lombok.With;
import org.homepoker.event.GameEvent;
import org.homepoker.model.game.GameStatus;

import java.util.ArrayList;
import java.util.List;

public record GameContext<T extends Game<T>>(@With T game, GameSettings settings, List<GameEvent> events, @With boolean forceUpdate) {
  public GameContext(T game, GameSettings settings) {
    this(game, settings, List.of(), false);
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
    return new GameContext<>(game, settings, events, forceUpdate);
  }
}
