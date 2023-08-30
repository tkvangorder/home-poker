package org.homepoker.user;

import org.homepoker.model.user.User;
import org.homepoker.model.user.UserCriteria;
import org.homepoker.model.user.UserInformationUpdate;
import org.homepoker.model.user.UserPasswordChangeRequest;

import java.util.List;

public interface UserManager {

	/**
	 * Register a new user, validating the correct data has been supplied.
	 * @param user The user to register
	 * @return The registered user
	 */
	User registerUser(User user);

  /**
   * Update an existing user's password. The request consists of the user's existing
   * login ID, a user challenge, and the new password. The user challenge can either
   * be the user's existing password or it may be a time-sensitive code that was
   * generated by the server. A user with admin privileges can change a user's password
   * on their behalf.
   *
   * @param userPasswordChangeRequest The request change details.
   */
  void updateUserPassword(UserPasswordChangeRequest userPasswordChangeRequest);

  /**
   * Update an existing user's information. This method can be used to change
   * the user's name and contact details.
   *
   * @param userInformation The user's updated contact information
   * @return An updated User
   */
  User updateUserInformation(UserInformationUpdate userInformation);

  /**
   * Get a user from the underlying datastore.
   *
   * @param loginId User's login ID
   * @return User
   */
  User getUser(String loginId);

  /**
   * Find existing users
   *
   * @param criteria The criteria to use when searching for users.
   * @return A list of matching users or an empty list if no users are found that match the criteria.
   */
  List<User> findUsers(UserCriteria criteria);

  /**
   * Delete a user from the server.
   *
   * @param loginId The user's login ID
   */
  void deleteUser(String loginId);
}
