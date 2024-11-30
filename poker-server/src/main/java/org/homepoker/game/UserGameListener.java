package org.homepoker.game;

import org.homepoker.model.user.User;

public abstract class UserGameListener implements GameListener {

  final User user;

  public UserGameListener(User user) {
    //noinspection ConstantValue
    if (user == null || user.id() == null) {
      throw new IllegalArgumentException("User cannot be null");
    }
    this.user = user;
  }

  public final User getUser() {
    return user;
  }

  @Override
  public final String id() {
    assert user.id() != null;
    return user.id();
  }

}
