package org.homepoker.model.game;

import lombok.*;
import org.jspecify.annotations.Nullable;

import java.util.List;

@Builder
@Setter
@EqualsAndHashCode
@ToString
public final class Seat {
  private final Status status;
  private final @Nullable Player player;
  private final @Nullable List<Card> cards;
  private final @Nullable PlayerAction action;

  public Seat(Status status, @Nullable Player player, @Nullable List<Card> cards, @Nullable PlayerAction action) {
    this.status = status;
    this.player = player;
    this.cards = cards;
    this.action = action;
  }

  public Status status() {
    return status;
  }

  public @Nullable Player player() {
    return player;
  }

  public @Nullable List<Card> cards() {
    return cards;
  }

  public @Nullable PlayerAction action() {
    return action;
  }

  public enum Status {
    ACTIVE,
    FOLDED,
    AWAY,
    EMPTY
  }

  public record Card(Card card, boolean visible) {
  }

}
