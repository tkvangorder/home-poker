package org.homepoker.model.game;

import lombok.Builder;
import lombok.With;
import org.jspecify.annotations.Nullable;

import java.util.List;

@Builder
@With
public record Seat(Status status, @Nullable Player player, @Nullable List<Card> cards, @Nullable PlayerAction action) {

  public enum Status {
    ACTIVE,
    FOLDED,
    AWAY,
    EMPTY
  }

  public record Card(Card card, boolean visible) {
  }

}
