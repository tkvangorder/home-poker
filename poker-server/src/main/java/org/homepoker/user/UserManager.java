package org.homepoker.user;

import jakarta.annotation.PostConstruct;
import org.homepoker.lib.exception.ValidationException;
import org.homepoker.model.user.User;
import org.homepoker.model.user.UserCriteria;
import org.homepoker.model.user.UserInformationUpdate;
import org.homepoker.model.user.UserPasswordChangeRequest;
import org.homepoker.security.SecurityUtilities;
import org.jspecify.annotations.Nullable;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.util.List;

import static org.springframework.data.mongodb.core.query.Query.query;

@SuppressWarnings("ALL")
@Service
public class UserManager {

  private final UserRepository userRepository;
  private final MongoOperations mongoOperations;

  private final SecurityUtilities securityUtilities;

  public UserManager(UserRepository userRepository, MongoOperations mongoOperations, SecurityUtilities securityUtilities) {
    this.userRepository = userRepository;
    this.mongoOperations = mongoOperations;
    this.securityUtilities = securityUtilities;
  }

  @PostConstruct
  public void setup() {
    //Create a unique index on the email.
    mongoOperations
        .indexOps(User.class)
        .ensureIndex(
            new Index().on("loginId", Sort.Direction.ASC).unique()
        );
    mongoOperations
        .indexOps(User.class)
        .ensureIndex(
            new Index().on("email", Sort.Direction.ASC).unique()
        );
  }

	/**
	 * Register a new user, validating the correct data has been supplied.
	 * @param user The user to register
	 * @return The registered user
	 */
	public User registerUser(User user) {
    Assert.notNull(user, "The user information cannot be null");
    Assert.isTrue(!StringUtils.hasText(user.id()), "The ID must be null when registering a new user.");
    Assert.hasText(user.loginId(), "The user login ID is required");
    Assert.hasText(user.password(), "The user password is required.");
    Assert.hasText(user.email(), "The user email address is required.");
    Assert.hasText(user.name(), "The user name is required.");
    Assert.hasText(user.phone(), "The user phone is required.");

    if (StringUtils.hasText(user.alias())) {
      //If no alias is provided, default it to the user's name
      user = user.withAlias(user.name());
    }
    //Encode the user password (the default algorithm is Bcrypt)
    user = user.withPassword(securityUtilities.encodePassword(user.password()));

    //Assign the user a role based on the security settings.
    user = securityUtilities.assignRolesToUser(user);

    try {
      return filterPassword(userRepository.insert(user));
    } catch (DuplicateKeyException e) {
      throw new ValidationException("EXISTING_USER", "There is already a user registered with that loginId or email.");
    }
  }

  /**
   * Update an existing user's password. The request consists of the user's existing
   * login ID, a user challenge, and the new password. The user challenge can either
   * be the user's existing password or it may be a time-sensitive code that was
   * generated by the server. A user with admin privileges can change a user's password
   * on their behalf.
   *
   * @param userPasswordChangeRequest The request change details.
   */
  public void updateUserPassword(UserPasswordChangeRequest userPasswordChangeRequest) {
    User user = userRepository.findByLoginId(userPasswordChangeRequest.loginId());

    if (user == null || !securityUtilities.getPasswordEncoder().matches(userPasswordChangeRequest.userChallenge(), user.password())) {
      throw new ValidationException("Access Denied");
    } else {
      user = user.withPassword(securityUtilities.encodePassword(userPasswordChangeRequest.newPassword()));
      userRepository.save(user);
    }
  }

  /**
   * Update an existing user's information. This method can be used to change
   * the user's name and contact details.
   *
   * @param userInformation The user's updated contact information
   * @return An updated User
   */
  public User updateUserInformation(UserInformationUpdate userInformation) {
    Assert.notNull(userInformation, "User Information was not provided.");
    Assert.notNull(userInformation.loginId(), "The user login ID is required");
    Assert.hasText(userInformation.email(), "The user email address is required.");
    Assert.hasText(userInformation.name(), "The user name is required.");
    Assert.hasText(userInformation.phone(), "The user phone is required.");

    User user = userRepository.findByLoginId(userInformation.loginId());
    if (user == null) {
      throw new ValidationException("The user does not exist.");
    } else {

      user = user.withEmail(userInformation.email());
      user = user.withName(userInformation.name());
      user = user.withPhone(userInformation.phone());
      if (StringUtils.hasText(userInformation.alias())) {
        user = user.withAlias(userInformation.alias());
      } else {
        user = user.withAlias(userInformation.name());
      }
      return UserManager.filterPassword(userRepository.save(user));
    }
  }

  /**
   * Get a user from the underlying datastore.
   *
   * @param loginId User's login ID
   * @return User
   */
  public User getUser(String loginId) {
    User user = userRepository.findByLoginId(loginId);
    if (user == null) {
      throw new ValidationException("The user does not exist.");
    }
    return UserManager.filterPassword(user);
  }

  /**
   * Find existing users
   *
   * @param criteria The criteria to use when searching for users.
   * @return A list of matching users or an empty list if no users are found that match the criteria.
   */
 public List<User> findUsers(UserCriteria criteria) {
   if (criteria == null ||
       (!StringUtils.hasText(criteria.userEmail()) && !StringUtils.hasText(criteria.userLoginId()))) {
     //No criteria, return all users.
     return userRepository.findAll().stream().map(UserManager::filterPassword).toList();
   }

   User example = User.builder()
       .loginId(criteria.userLoginId())
       .email(criteria.userEmail())
       .build();
   Criteria mongoCriteria = new Criteria();

   if (StringUtils.hasText(criteria.userLoginId())) {
     mongoCriteria.and("loginId").regex(criteria.userLoginId());
   }
   if (StringUtils.hasText(criteria.userEmail())) {
     mongoCriteria.and("email").regex(criteria.userEmail());
   }


   return mongoOperations.query(User.class)
       .matching(query(mongoCriteria))
       .all().stream()
       .map(UserManager::filterPassword).toList();
 }

  /**
   * Delete a user from the server.
   *
   * @param loginId The user's login ID
   */
  public void deleteUser(String loginId) {
    User user = userRepository.findByLoginId(loginId);
    if (user == null) {
      throw new ValidationException("The user does not exist.");
    } else {
      userRepository.deleteById(loginId);
    }
  }

  /**
   * Helper method to clear the user password field prior to returning it to the caller.
   *
   * @param user A user object
   * @return user object with its password field cleared.
   */
  @Nullable
  private static User filterPassword(@Nullable User user) {
    if (user == null) {
      return null;
    }
    user = user.withPassword(null);
    return user;
  }

}
