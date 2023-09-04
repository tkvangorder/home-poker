package org.homepoker.client;

import lombok.extern.slf4j.Slf4j;
import org.homepoker.event.ApplicationError;
import org.homepoker.event.user.UserRegistered;
import org.homepoker.lib.util.JsonUtils;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

@Slf4j
public class NotificationService {
  public static final String ANSI_RESET = "\u001B[0m";
  public static final String ANSI_YELLOW = "\u001B[33m";
  public static void info(String message) {
    System.out.println();
    log.info(message);
    printPrompt();
  }
  public static void error(String message) {
    System.out.println();
    log.error(message);
    printPrompt();
  }

  public static void error(String message, Throwable t) {
    System.out.println();
    log.error(message);
    printPrompt();
  }

  private static void printPrompt() {
    System.out.print(ANSI_YELLOW + "shell:>" + ANSI_RESET);
  }


}
