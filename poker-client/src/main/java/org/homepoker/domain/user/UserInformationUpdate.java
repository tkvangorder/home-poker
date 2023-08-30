package org.homepoker.domain.user;

import lombok.Builder;
import lombok.Value;

/**
 * Domain object used to update information about the user. The loginId is used to
 * retrieve the existing user and cannot be changed.
 * 
 * NOTE: This domain object cannot be used to alter the user's ID or password. 
 * 
 * @author tyler.vangorder
 */
@Value
@Builder
public class UserInformationUpdate {

	/**
	 * Existing user's loginID
	 */
	String loginId;
	
	/**
	 * User's email.
	 */
	String email;

	/**
	 * User's preferred alias when in a game or at a table.
	 */
	String alias;

	/**
	 * User's "real" name.
	 */
	String name;

	/**
	 * Phone number can be useful when organizing a remote game.
	 */
	String phone;
	
}
