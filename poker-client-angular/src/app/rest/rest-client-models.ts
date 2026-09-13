import { User } from '../user/user-models';

export interface AuthenticatedUserReponse {
  user: User;
  token: string;
}
