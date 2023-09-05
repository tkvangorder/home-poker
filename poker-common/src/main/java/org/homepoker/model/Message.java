package org.homepoker.model;


import org.homepoker.event.Event;

public record Message(Severity severity, String message) {

  public enum Severity {
    INFO,
    WARNING,
    ERROR
  }

  public static Message info(String message) {
    return new Message(Severity.INFO, message);
  }
  public static Message warn(String message) {
    return new Message(Severity.WARNING, message);
  }
  public static Message error(String message) {
    return new Message(Severity.ERROR, message);
  }
}
