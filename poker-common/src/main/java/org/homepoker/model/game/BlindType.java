package org.homepoker.model.game;

/**
 * Type of blind being posted. Additively extensible: the wire format includes the
 * enum name, so adding {@code ANTE}, {@code STRADDLE}, or {@code DEAD_BLIND} later
 * is forward-compatible. Only {@link #SMALL} and {@link #BIG} are used today.
 */
public enum BlindType {
  SMALL,
  BIG
}
