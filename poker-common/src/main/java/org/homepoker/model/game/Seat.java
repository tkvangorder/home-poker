package org.homepoker.model.game;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;
import lombok.experimental.Accessors;
import org.homepoker.model.poker.Card;
import org.jspecify.annotations.Nullable;

import java.util.List;

@Data
@Builder
@Accessors(fluent = true)
public final class Seat {

  @JsonProperty
  private Status status;
  @Nullable
  @JsonProperty
  private Player player;
  @With
  @Nullable
  @JsonProperty
  private List<SeatCard> cards;
  @Nullable
  @JsonProperty
  private PlayerAction action;
  @JsonProperty
  private int currentBetAmount;
  @JsonProperty
  private boolean isAllIn;
  @JsonProperty
  private boolean mustPostBlind;
  @JsonProperty
  private boolean missedBigBlind;
  @With
  @Nullable
  @JsonProperty
  private PlayerAction pendingIntent;

  @Nullable
  public String userId() {
    return player == null ? null : player.userId();
  }

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
