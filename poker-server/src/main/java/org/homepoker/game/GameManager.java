package org.homepoker.game;

import org.homepoker.command.GameCommand;
import org.homepoker.user.User;
import org.jctools.maps.NonBlockingHashMap;
import org.jctools.queues.MessagePassingQueue;
import org.jctools.queues.MpscLinkedQueue;

import java.util.Map;
import java.util.Optional;

public abstract class GameManager<T extends Game> {

  /**
   * This is a non-blocking queue that allows multiple threads to add commands to the queue and a single thread (the game loop thread) that will drain those commands.
   * The contract guarantee (multi-producers, one consumer) is defined by the implementation MpscLinkedQueue and the capacity is unbounded.
   * <p>
   * The game loop thread is the only thread that will drain the commands and manipulate the game state.
   */
  MessagePassingQueue<GameCommand> pendingCommands = new MpscLinkedQueue<>();

  /**
   * This is a map of user ID -> game listener registered for that user.
   */
  private final Map<String, GameListener> gameListeners = new NonBlockingHashMap<>();

  public Optional<GameListener> getGameListener(User user) {
    return Optional.ofNullable(gameListeners.get(user.getLoginId()));
  }

  public void addGameListener(GameListener listener) {
  }

  public void removeGameListener(GameListener listener) {
    // TODO Auto-generated method stub
  }

  public void submitCommand(GameCommand command) {
    pendingCommands.offer(command);
  }

  public void processGameTick() {
    //Process queued Commands
    pendingCommands.drain(this::applyCommand);

    //Top Level Game Processing
    //ProcessEachTable
    //Optionally Save Game/State
  }

  protected final void applyCommand(GameCommand command) {

    switch (command.commandId()) {
      case REGISTER_USER:
      case CONFIRM_USER:
      case LEAVE_GAME:
      case START_GAME:
      case PAUSE_GAME:
      case END_GAME:
    }
  }


  protected abstract T getGame();

  //protected abstract void do();
}
