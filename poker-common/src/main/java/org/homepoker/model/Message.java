package org.homepoker.model;

import static org.homepoker.model.MessageSeverity.*;

public record Message(MessageSeverity severity, String message) {

  public static Message info(String message) {
    return new Message(INFO, message);
  }
  public static Message warn(String message) {
    return new Message(WARNING, message);
  }
  public static Message error(String message) {
    return new Message(ERROR, message);
  }
}
