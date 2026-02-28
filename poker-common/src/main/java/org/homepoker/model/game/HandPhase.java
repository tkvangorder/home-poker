package org.homepoker.model.game;

/**
 * Represents the phase of a hand being played at a table.
 */
public enum HandPhase {
  WAITING_FOR_PLAYERS,
  PREDEAL,
  DEAL,
  PRE_FLOP_BETTING,
  FLOP,
  FLOP_BETTING,
  TURN,
  TURN_BETTING,
  RIVER,
  RIVER_BETTING,
  SHOWDOWN,
  HAND_COMPLETE
}
