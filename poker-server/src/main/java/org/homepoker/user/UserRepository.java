package org.homepoker.user;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

/**
 * A repository for persisting and retrieving users.
 *
 * @author tyler.vangorder
 */
@Repository
public interface UserRepository extends MongoRepository<User, String> {

  User findByLoginId(String loginId);
}
