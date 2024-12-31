package org.homepoker.model.game;

import lombok.*;
import lombok.experimental.Accessors;
import org.jspecify.annotations.Nullable;

import java.util.List;

@Data
@Builder
@Accessors(fluent = true)
public final class Seat {

  private Status status;
  private @Nullable Player player;
  private @Nullable List<Card> cards;
  private @Nullable PlayerAction action;

  public enum Status {
    ACTIVE,
    FOLDED,
    AWAY,
    EMPTY
  }

  public record Card(Card card, boolean visible) {
  }

}
