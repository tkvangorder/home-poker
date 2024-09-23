package org.homepoker.utils;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

public class DateTimeUtils {

  public static DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

  public static Instant computeNextWallMinute() {
    return ZonedDateTime.now().withSecond(0).withNano(0).plusMinutes(1).toInstant();
  }

  public static Instant computeNSecondsFromNow(int seconds) {
    return Instant.now().plusSeconds(seconds);
  }

  public static Instant getStartOfDayInCurrentZone() {
    return ZonedDateTime.now().withHour(0).withMinute(0).withSecond(0).withNano(0).toInstant();
  }

  /**
   * Parse a string date in the format of yyyy-MM-dd HH:mm, relative to the current time zone, into an Instant
   * @param dateTime The date time string to parse in the format of yyyy-MM-dd HH:mm
   * @return The Instant representation of the date time string
   */
  public static Instant stringToInstantInCurrentZone(String dateTime) {
    return ZonedDateTime.parse(dateTime).toInstant();
  }

}
