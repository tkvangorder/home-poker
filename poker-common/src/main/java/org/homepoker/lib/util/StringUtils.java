package org.homepoker.lib.util;

import org.jspecify.annotations.Nullable;

public class StringUtils {
  private StringUtils() {
  }

  /**
   * Convert a string value to a boolean.
   *
   * @param value the string value to convert
   * @return true if the string value is "true", "t", "yes", or "y" (case insensitive)
   */
  public static boolean stringToBoolean(@Nullable String value) {
    if (value == null) {
      return false;
    }
    return switch (value.toLowerCase()) {
      case "t", "true", "y", "yes" -> true;
      default -> false;
    };
  }

  /**
   * Convert a string using camel case to kabob case.
   *
   * @param value the string value to convert
   * @return the integer value of the string
   * @throws NumberFormatException if the string is not a valid integer
   */
  public static String camelToKabobCase(String value) {
    //noinspection ConstantValue
    assert value != null;
    return value.replaceAll("(.)(\\p{Upper})", "$1-$2").toLowerCase();
  }

}
