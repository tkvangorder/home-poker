export interface User {
  id: string;
  token?: string;
  password?: string;
  email?: string;
  alias?: string;
  name?: string;
  phone?: string;
  roles?: string[];
}

export interface RegisterUserRequest {
  loginId?: string;
  password?: string;
  email?: string;
  alias?: string;
  name?: string;
  phone?: string;
  serverPasscode?: string;
}

export interface UserLogin {
  loginId?: string;
  password?: string;
}
