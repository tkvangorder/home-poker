import dayjs from 'dayjs';
import customParseFormat from 'dayjs/plugin/customParseFormat';

export class CalendarUtils {
  static {
    dayjs.extend(customParseFormat);
  }
  public static readonly DATE_FORMAT = 'YYYY-MM-DD';
  public static readonly TIME_FORMAT = 'HH:mm';

  public static combineDateTime(
    dateString: string | undefined,
    time: string | undefined
  ): Date {
    if (!dateString || !time) {
      throw new Error('Invalid date or time');
    }

    return dayjs(
      `${dateString} ${time}`,
      `${CalendarUtils.DATE_FORMAT} ${CalendarUtils.TIME_FORMAT}`
    ).toDate();
  }
}
