import { CalendarUtils } from './calendar-utils';

describe('combineDateTime tests', () => {
  it('Valid date and time', () => {
    const date = CalendarUtils.combineDateTime('2024-06-29', '08:00');
    expect(date).toBeInstanceOf(Date);
    expect(date.toISOString()).toBe('2024-06-29T15:00:00.000Z');
  });

  it('Invalid date', () => {
    expect(() => {
      CalendarUtils.combineDateTime(undefined, '12:00');
    }).toThrow('Invalid date or time');
  });
  it('Invalid time', () => {
    expect(() => {
      CalendarUtils.combineDateTime('2021-05-01', undefined);
    }).toThrow('Invalid date or time');
  });
});
