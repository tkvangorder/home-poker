export class BaseError extends Error {
  constructor(private _errorCode: string, message: string) {
    super(message);
    this.name = 'BaseException';
  }
  get errorCode() {
    return this._errorCode;
  }
}

export class ValidationError extends BaseError {
  constructor(code: string, message: string) {
    super(code, message);
    this.name = 'ValidationException';
  }
}

export class SystemError extends BaseError {
  constructor(code: string, message: string, private original: Error) {
    super(code, message);
    this.name = 'SystemException';
  }

  get originalError() {
    return this.original;
  }
}
