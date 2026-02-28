package org.homepoker.model.game;

import lombok.*;
import lombok.experimental.Accessors;
import org.homepoker.model.poker.Card;
import org.jspecify.annotations.Nullable;

import java.util.List;

@Data
@Builder
@Accessors(fluent = true)
public final class Seat {

  private Status status;
  private @Nullable Player player;
  private @Nullable List<SeatCard> cards;
  private @Nullable PlayerAction action;
  private int currentBetAmount;
  private boolean isAllIn;
  private boolean mustPostBlind;
  private boolean missedBigBlind;
  private @Nullable PlayerAction pendingIntent;

  public enum Status {
    ACTIVE,
    FOLDED,
    JOINED_WAITING,
    EMPTY
  }

  @SuppressWarnings("FieldMayBeFinal")
  public static class SeatBuilder {
    private Status status = Status.EMPTY;
  }

  /**
   * A seat card represents a card that is dealt to a player at a table.
   * @param card The card that is dealt to the player.
   * @param showCard Should the card be shown to other players after the hand is complete?
   */
  @With
  public record SeatCard(Card card, boolean showCard) {
  }

}
