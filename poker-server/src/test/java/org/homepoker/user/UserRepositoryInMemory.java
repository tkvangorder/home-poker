package org.homepoker.user;

import org.homepoker.lib.exception.ValidationException;
import org.homepoker.model.user.User;
import org.homepoker.test.AbstractInMemoryRepository;

import java.util.*;

@SuppressWarnings("unchecked")
public class UserRepositoryInMemory extends AbstractInMemoryRepository<User, String> implements UserRepository {

  Map<String, User> users = new HashMap<>();

  @Override
  public User findByLoginId(String loginId) {
    return users.get(loginId);
  }

  @Override
  public <S extends User> S insert(S user) {
    User saved = user.withId(UUID.randomUUID().toString());
    users.put(saved.loginId(), saved);
    return (S) saved;
  }

  @Override
  public <S extends User> S save(S entity) {
    if (!users.containsKey(entity.loginId())) {
      throw new ValidationException("User not found");
    }
    users.put(entity.loginId(), entity);

    return entity;
  }

  @Override
  public void deleteById(String id) {
    if (!users.containsKey(id)) {
      throw new ValidationException("User not found");
    }
    users.remove(id);
  }
}
