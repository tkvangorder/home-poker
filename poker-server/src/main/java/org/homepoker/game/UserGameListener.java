package org.homepoker.game;

import org.homepoker.model.user.User;

public abstract class UserGameListener implements GameListener {

  final User user;

  public UserGameListener(User user) {
    this.user = user;
  }

  public final User user() {
    return user;
  }

  @Override
  public final String userId() {
    return user.id();
  }

}
