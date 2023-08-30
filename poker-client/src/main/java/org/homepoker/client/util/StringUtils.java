package org.homepoker.client.util;

public class StringUtils {
  private StringUtils() {
  }

  /**
   * Convert a string value to a boolean.
   *
   * @param value the string value to convert
   * @return true if the string value is "true", "t", "yes", or "y" (case insensitive)
   */
  public static boolean stringToBoolean(String value) {
    if (value == null) {
      return false;
    }
    return switch (value.toLowerCase()) {
      case "t", "true", "y", "yes" -> true;
      default -> false;
    };
  }
}
