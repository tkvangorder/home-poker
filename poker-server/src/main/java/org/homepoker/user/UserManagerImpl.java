package org.homepoker.user;

import org.homepoker.common.ValidationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.lang.Nullable;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import jakarta.annotation.PostConstruct;
import java.util.List;

import static org.springframework.data.mongodb.core.query.Query.query;

@Service
public class UserManagerImpl implements UserManager {

  private final UserRepository userRepository;
  private final MongoOperations mongoOperations;
  private final PasswordEncoder passwordEncoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();

  public UserManagerImpl(UserRepository userRepository, MongoOperations mongoOperations) {
    this.userRepository = userRepository;
    this.mongoOperations = mongoOperations;
  }

  @PostConstruct
  public void setup() {
    //Create a unique index on the email.
    mongoOperations
        .indexOps(User.class)
        .ensureIndex(
            new Index().on("loginId", Direction.ASC).unique()
        );
    mongoOperations
        .indexOps(User.class)
        .ensureIndex(
            new Index().on("email", Direction.ASC).unique()
        );
  }

  @Override
  public User registerUser(User user) {

    Assert.notNull(user, "The user information cannot be null");
    Assert.isTrue(!StringUtils.hasText(user.getId()), "The ID must be null when registering a new user.");
    Assert.notNull(user.getLoginId(), "The user login ID is required");
    Assert.hasText(user.getPassword(), "The user password is required.");
    Assert.hasText(user.getEmail(), "The user email address is required.");
    Assert.hasText(user.getName(), "The user name is required.");
    Assert.hasText(user.getPhone(), "The user phone is required.");

    if (StringUtils.hasText(user.getAlias())) {
      //If no alias is provided, default it to the user's name
      user.setAlias(user.getName());
    }
    //Encode the user password (the default algorithm is Bcrypt)
    user.setPassword(passwordEncoder.encode(user.getPassword()));

    try {
      return UserManagerImpl.filterPassword(userRepository.insert(user));
    } catch (DuplicateKeyException e) {
      throw new ValidationException("There is already a user registered with that loginId or email.");
    }
  }

  @Override
  public User updateUserInformation(UserInformationUpdate userInfo) {
    Assert.notNull(userInfo, "User Information was not provided.");
    Assert.notNull(userInfo.getLoginId(), "The user login ID is required");
    Assert.hasText(userInfo.getEmail(), "The user email address is required.");
    Assert.hasText(userInfo.getName(), "The user name is required.");
    Assert.hasText(userInfo.getPhone(), "The user phone is required.");

    User user = userRepository.findByLoginId(userInfo.getLoginId());
    if (user == null) {
      throw new ValidationException("The user does not exist.");
    } else {
      user.setEmail(userInfo.getEmail());
      user.setName(userInfo.getName());
      user.setPhone(userInfo.getPhone());
      if (StringUtils.hasText(userInfo.getAlias())) {
        user.setAlias(userInfo.getAlias());
      } else {
        user.setAlias(userInfo.getName());
      }
      return UserManagerImpl.filterPassword(userRepository.save(user));
    }
  }

  @Override
  public User getUser(String loginId) {
    User user = userRepository.findByLoginId(loginId);
    if (user == null) {
      throw new ValidationException("The user does not exist.");
    }
    return UserManagerImpl.filterPassword(user);
  }

  @Override
  public List<User> findUsers(UserCriteria criteria) {

    if (criteria == null ||
        (!StringUtils.hasText(criteria.getUserEmail()) && !StringUtils.hasText(criteria.getUserLoginId()))) {
      //No criteria, return all users.
      return userRepository.findAll().stream().map(UserManagerImpl::filterPassword).toList();
    }

    Criteria mongoCriteria = new Criteria();

    if (StringUtils.hasText(criteria.getUserLoginId())) {
      mongoCriteria.and("loginId").regex(criteria.getUserLoginId());
    }
    if (StringUtils.hasText(criteria.getUserEmail())) {
      mongoCriteria.and("email").regex(criteria.getUserEmail());
    }

    return mongoOperations.query(User.class)
        .matching(query(mongoCriteria))
        .all().stream()
        .map(UserManagerImpl::filterPassword).toList();
  }

  @Override
  public void deleteUser(String loginId) {

    User user = userRepository.findByLoginId(loginId);
    if (user == null) {
      throw new ValidationException("The user does not exist.");
    } else {
      userRepository.delete(user);
    }
  }

  @Override
  public void updateUserPassword(UserPasswordChangeRequest userPasswordChangeRequest) {
    User user = userRepository.findByLoginId(userPasswordChangeRequest.getLoginId());

    if (user == null || !passwordEncoder.matches(userPasswordChangeRequest.getUserChallenge(), user.getPassword())) {
      throw new ValidationException("Access Denied");
    } else {
      user.setPassword(passwordEncoder.encode(userPasswordChangeRequest.getNewPassword()));
      userRepository.save(user);
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
    user.setPassword(null);
    return user;
  }
}
