package org.homepoker.client.util;

import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.ResolverStyle;
import java.time.temporal.ChronoField;
import java.time.temporal.ChronoUnit;
import java.util.Date;

public class DateUtils {
  private DateUtils() {
  }

  static DateTimeFormatter dateTimeFormatter = new DateTimeFormatterBuilder()
      .appendPattern("yyyy-MM-dd HH:mm")
      .parseDefaulting(ChronoField.HOUR_OF_DAY, 0)
      .parseDefaulting(ChronoField.MINUTE_OF_HOUR, 0)
      .parseDefaulting(ChronoField.SECOND_OF_MINUTE, 0)
      .parseDefaulting(ChronoField.MILLI_OF_SECOND, 0)
      .parseDefaulting(ChronoField.NANO_OF_SECOND, 0)
      .toFormatter()
      .withResolverStyle(ResolverStyle.SMART)
      .withZone(ZoneId.systemDefault());

  static DateTimeFormatter dateFormatter = new DateTimeFormatterBuilder()
      .appendPattern("yyyy-MM-dd")
      .parseDefaulting(ChronoField.HOUR_OF_DAY, 0)
      .parseDefaulting(ChronoField.MINUTE_OF_HOUR, 0)
      .parseDefaulting(ChronoField.SECOND_OF_MINUTE, 0)
      .parseDefaulting(ChronoField.MILLI_OF_SECOND, 0)
      .parseDefaulting(ChronoField.NANO_OF_SECOND, 0)
      .toFormatter()
      .withResolverStyle(ResolverStyle.SMART)
      .withZone(ZoneId.systemDefault());

  static DateTimeFormatter timeFormatter = new DateTimeFormatterBuilder()
      .appendPattern("HH:mm")
      .parseDefaulting(ChronoField.HOUR_OF_DAY, 0)
      .parseDefaulting(ChronoField.MINUTE_OF_HOUR, 0)
      .parseDefaulting(ChronoField.SECOND_OF_MINUTE, 0)
      .parseDefaulting(ChronoField.MILLI_OF_SECOND, 0)
      .parseDefaulting(ChronoField.NANO_OF_SECOND, 0)
      .toFormatter()
      .withResolverStyle(ResolverStyle.SMART)
      .withZone(ZoneId.systemDefault());

  /**
   * Converts a string value to a date. This method expects the format of the string to be of the form
   * "yyyy-MM-dd" or you can use short hand values like "t" for today, "t+7" for today plus seven days.
   *
   * @param stringDate
   * @return A date representation of the string
   */
  public static LocalDate stringToDate(String stringDate) {

    if (stringDate == null) {
      return null;
    }
    String trimmedValue = StringUtils.trimAllWhitespace(stringDate).toLowerCase();
    if (trimmedValue.charAt(0) == 't') {
      LocalDate today = LocalDate.now();
      if (trimmedValue.length() == 1) {
        return today;
      } else if (trimmedValue.charAt(1) == '+') {
        int days = Integer.parseInt(trimmedValue.substring(2));
        return today.plusDays(days);
      } else if (trimmedValue.charAt(1) == '-') {
        int days = Integer.parseInt(trimmedValue.substring(2));
        return today.minusDays(days);
      }
    }

    return LocalDate.parse(stringDate, dateFormatter.withZone(ZoneId.systemDefault()));
  }

  /**
   * Converts a string value to a date. This method expects the format of the string to be of the form
   * "yyyy-MM-dd" or you can use short hand values like "t" for today, "t+7" for today plus seven days.
   *
   * @param stringTime A string representation of a time
   * @return A date representation of the string
   */
  public static LocalTime stringToTime(String stringTime) {

    if (stringTime == null) {
      return null;
    }

    return LocalTime.parse(stringTime, timeFormatter.withZone(ZoneId.systemDefault()));
  }

  /**
   * Converts a string value to a date. This method expects the format of the string to be of the form
   * "yyyy-MM-dd HH:mm" or you can use short hand values like "t" for today, "t+7" for today plus seven days.
   *
   * @param stringDate A string representation of a date
   * @return A date representation of the string
   */
  public static LocalDateTime stringToDateTime(String stringDate) {

    if (stringDate == null) {
      return null;
    }
    String trimmedValue = StringUtils.trimAllWhitespace(stringDate).toLowerCase();
    if (trimmedValue.charAt(0) == 't') {
      LocalDateTime today = LocalDateTime.now();
      if (trimmedValue.length() == 1) {
        return today;
      } else if (trimmedValue.charAt(1) == '+') {
        int days = Integer.parseInt(trimmedValue.substring(2));
        return today.plusDays(days);
      } else if (trimmedValue.charAt(1) == '-') {
        int days = Integer.parseInt(trimmedValue.substring(2));
        return today.minusDays(days);
      }
    }

    return LocalDateTime.parse(stringDate, dateTimeFormatter.withZone(ZoneId.systemDefault()));
  }


  /**
   * Truncate a date to a certain percision of the unit passed in.
   *
   * @param date The base date
   * @param unit The unit to truncate to.
   * @return A new date truncated to the given unit.
   */
  public static Date truncateDate(Date date, ChronoUnit unit) {
    if (date == null) {
      return null;
    }
    LocalDateTime dateTime = LocalDateTime.ofInstant(date.toInstant(), ZoneId.systemDefault());
    return Date.from(dateTime.truncatedTo(unit).atZone(ZoneId.systemDefault()).toInstant());
  }

  /**
   * Add a given unit to a date field.
   *
   * @param date   The base date
   * @param unit   The unit to add
   * @param amount The amount of that unit to add
   * @return A new date computed by adding the unit to the base date.
   */
  public static Date addUnitToDate(Date date, ChronoUnit unit, int amount) {
    if (date == null) {
      return null;
    }

    if (unit == ChronoUnit.MONTHS) {
      return Date.from(date.toInstant().atZone(ZoneId.systemDefault()).plusMonths(amount).toInstant());
    } else if (unit == ChronoUnit.WEEKS) {
      return Date.from(date.toInstant().atZone(ZoneId.systemDefault()).plusWeeks(amount).toInstant());
    } else if (unit == ChronoUnit.YEARS) {
      return Date.from(date.toInstant().atZone(ZoneId.systemDefault()).plusYears(amount).toInstant());
    } else {
      return Date.from(date.toInstant().plus(amount, unit));
    }
  }
}
