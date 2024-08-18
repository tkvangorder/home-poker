package org.homepoker.model.user;

import lombok.Builder;
import lombok.With;

/**
 * Domain object used to update information about the user. The loginId is used to
 * retrieve the existing user and cannot be changed.
 * <p>
 * NOTE: This domain object cannot be used to alter the user's ID or password.
 *
 * @param loginId The user's login ID.
 * @param email   The user's email address.
 * @param alias   The user's alias.
 * @param name    The user's name.
 * @param phone   The user's phone number.
 */
@Builder
@With
public record UserInformationUpdate(String loginId, String email, String alias, String name, String phone) {
}
