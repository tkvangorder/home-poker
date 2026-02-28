package org.homepoker.model.game;

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
