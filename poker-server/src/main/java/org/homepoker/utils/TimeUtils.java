package org.homepoker.utils;

import java.time.Instant;
import java.time.ZonedDateTime;

public class TimeUtils {

  public static Instant computeNextWallMinute() {
    return ZonedDateTime.now().withSecond(0).withNano(0).plusMinutes(1).toInstant();
  }

  public static Instant computeNSecondsFromNow(int seconds) {
    return Instant.now().plusSeconds(seconds);
  }

  public static Instant getStartOfDayInCurrentZone() {
    return ZonedDateTime.now().withHour(0).withMinute(0).withSecond(0).withNano(0).toInstant();
  }
}
