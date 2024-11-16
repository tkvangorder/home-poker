package org.homepoker.lib.exception;

public class ValidationException extends RuntimeException {

  private String code = "VALIDATION_ERROR";

  public ValidationException(String message) {
    super(message);
  }

  public ValidationException(String code, String message) {
    super(message);
    this.code = code;
  }

  /**
   * An optional codified value for a validation error when it is important to distinguish between different types of
   * validation errors. The default code is "VALIDATION_ERROR" if not specified.
   *
   * @return The code for the validation error
   */
  public String getCode() {
    return code;
  }
}
