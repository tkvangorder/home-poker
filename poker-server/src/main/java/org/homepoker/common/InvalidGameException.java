package org.homepoker.common;

public class InvalidGameException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public InvalidGameException(String message) {
    super(message);
  }
}
