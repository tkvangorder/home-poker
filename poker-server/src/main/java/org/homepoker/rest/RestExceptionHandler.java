package org.homepoker.rest;

import org.homepoker.lib.exception.ResourceNotFound;
import org.homepoker.lib.exception.SecurityException;
import org.homepoker.lib.exception.ValidationException;
import org.homepoker.model.Message;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class RestExceptionHandler {

  @ExceptionHandler(ResourceNotFound.class)
  @ResponseBody
  @ResponseStatus(HttpStatus.NOT_FOUND)
  public Message handleException(ResourceNotFound e) {
    return Message.error(e.getMessage());
  }

  @ExceptionHandler(IllegalArgumentException.class)
  @ResponseBody
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public Message handleException(IllegalArgumentException e) {
    return Message.error(e.getMessage());
  }

  @ExceptionHandler(ValidationException.class)
  @ResponseBody
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public Message handleException(ValidationException e) {
    return Message.error(e.getMessage());
  }

  @ExceptionHandler(AuthenticationException.class)
  @ResponseBody
  @ResponseStatus(HttpStatus.UNAUTHORIZED)
  public Message handleException(AuthenticationException e) {
    return Message.error(e.getMessage());
  }

  @ExceptionHandler(SecurityException.class)
  @ResponseBody
  @ResponseStatus(HttpStatus.FORBIDDEN)
  public Message handleException(SecurityException e) {
    return Message.error(e.getMessage());
  }

  @ExceptionHandler(Exception.class)
  @ResponseBody
  @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
  public Message handleException(Exception e) {
    return Message.error(e.getMessage());
  }
}
