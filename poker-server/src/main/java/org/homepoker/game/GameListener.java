package org.homepoker.game;

import org.homepoker.event.PokerEvent;

public interface GameListener {
  String id();
  void onEvent(PokerEvent event);
  boolean acceptsEvent(PokerEvent event);
}
