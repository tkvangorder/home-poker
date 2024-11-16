package org.homepoker.user;

import org.homepoker.model.user.User;
import org.jspecify.annotations.Nullable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

/**
 * A repository for persisting and retrieving users.
 *
 * @author tyler.vangorder
 */
@Repository
public interface UserRepository extends MongoRepository<User, String> {

  @Nullable
  User findByLoginId(String loginId);
}
