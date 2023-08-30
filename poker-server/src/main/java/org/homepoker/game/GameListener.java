package org.homepoker.game;

import org.homepoker.model.event.GameEvent;
import org.homepoker.model.user.User;

public interface GameListener {
  void gameEventPublished(GameEvent event);
  User getUser();
}
