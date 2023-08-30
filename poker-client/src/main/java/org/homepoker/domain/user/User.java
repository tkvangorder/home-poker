package org.homepoker.domain.user;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class User {

	/**
	 * The user's login ID.
	 */
	String loginId;

	/**
	 * User's password, always encrypted.
	 */
	String password;

	/**
	 * User's email
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
