package org.homepoker.game;

import org.homepoker.game.event.GameEvent;
import org.homepoker.user.User;

public interface GameListener {
  void gameEventPublished(GameEvent event);
  User getUser();
}
