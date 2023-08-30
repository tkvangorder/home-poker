package org.homepoker.command;

public enum CommandId {
  //Admin game commands
  START_GAME,
  PAUSE_GAME,
  CONFIRM_USER,
  END_GAME,

  //Generic game commands
  LEAVE_GAME,
  BROADCAST_MESSAGE,
  USER_MESSAGE,
  REGISTER_USER,
  SEAT_PLAYER,
  KICK_PLAYER,
  GET_GAME_STATE,
  REQUEST_LEADERBOARD,
  GET_BLINDS,

  //Table commands
  CHECK,
  CALL,
  RAISE,
  FOLD,
  SHOW_CARDS
}
