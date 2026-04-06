package org.homepoker.model.game;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = PlayerAction.Fold.class, name = "fold"),
    @JsonSubTypes.Type(value = PlayerAction.Check.class, name = "check"),
    @JsonSubTypes.Type(value = PlayerAction.Call.class, name = "call"),
    @JsonSubTypes.Type(value = PlayerAction.Bet.class, name = "bet"),
    @JsonSubTypes.Type(value = PlayerAction.Raise.class, name = "raise"),
})
public sealed interface PlayerAction {

  record Fold() implements PlayerAction {
  }

  record Check() implements PlayerAction {
  }

  record Call(int amount) implements PlayerAction {
  }

  record Bet(int amount) implements PlayerAction {
  }

  record Raise(int amount) implements PlayerAction {
  }

}
