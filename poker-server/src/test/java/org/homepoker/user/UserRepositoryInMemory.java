package org.homepoker.user;

import org.homepoker.lib.exception.ValidationException;
import org.homepoker.model.user.User;
import org.homepoker.test.AbstractInMemoryRepository;

import java.util.*;

public class UserRepositoryInMemory extends AbstractInMemoryRepository<User, String> implements UserRepository {

  Map<String, User> users = new HashMap<>();

  @Override
  public Optional<User> findById(String loginId) {
    return Optional.ofNullable(users.get(loginId));
  }

  @Override
  public <S extends User> S insert(S user) {
    users.put(user.id(), user);
    return user;
  }

  @Override
  public <S extends User> S save(S entity) {
    if (!users.containsKey(entity.id())) {
      throw new ValidationException("User not found");
    }
    users.put(entity.id(), entity);

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
