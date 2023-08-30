package org.homepoker.lib.exception;

public class InvalidGameException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public InvalidGameException(String message) {
    super(message);
  }
}
