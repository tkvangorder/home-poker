export class LangUtils {
  /**
   * Given a string value that contains a number (either integer or decimal), this function will return the number as a currency string with the following rules:
   *
   * - If the number is an integer, the currency string will have no decimal places
   * - If the number is a decimal, the currency string will have two decimal places
   * - If the number is a decimal and the roundtoNearestDollar flag is set, the number will be rounded to the nearest dollar and will have no decimal places
   *
   * @param value The string value to parse as a currency string
   * @param roundtoNearestDollar Optional flag to round the currency to the nearest dollar
   * @returns The currency string
   */
  public static parseCurrency(
    value: string,
    roundtoNearestDollar?: boolean
  ): string {
    const currency = +value;
    if (isNaN(currency)) {
      throw new Error('The value "' + value + '" is not a valid number.');
    }
    return currency % 1 == 0 || roundtoNearestDollar
      ? currency.toFixed(0)
      : currency.toFixed(2);
  }

  public static asCents(value: number): number {
    return value * 100;
  }

  /**
   * Formats a value in cents as a dollar currency string.
   * Examples: 2500 -> "$25", 50 -> "$0.50", 125 -> "$1.25"
   */
  public static formatCurrency(cents: number): string {
    const dollars = cents / 100;
    return dollars % 1 === 0
      ? `$${dollars.toFixed(0)}`
      : `$${dollars.toFixed(2)}`;
  }
}
