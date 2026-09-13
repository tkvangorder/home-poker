import { LangUtils } from './lang.utils';

describe('parseCurrency tests', () => {
  it('Value "123.45"', () => {
    const result = LangUtils.parseCurrency('123.45');
    expect(result).toEqual('123.45');
  });
  it('Value "    123.45  "', () => {
    const result = LangUtils.parseCurrency('123.45');
    expect(result).toEqual('123.45');
  });

  it('Value "123.45", round to nearest dollar', () => {
    const result = LangUtils.parseCurrency('123.45', true);
    expect(result).toEqual('123');
  });

  it('Value "892.23423423464"', () => {
    const result = LangUtils.parseCurrency('892.23423423464');
    expect(result).toEqual('892.23');
  });
  it('Value "892.23423423464", round to nearest dollar', () => {
    const result = LangUtils.parseCurrency('892.23423423464', true);
    expect(result).toEqual('892');
  });

  it('Value "fred"', () => {
    expect(() => {
      LangUtils.parseCurrency('fred');
    }).toThrow('The value "fred" is not a valid number.');
  });
});
