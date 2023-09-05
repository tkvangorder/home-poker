package org.homepoker;

public class PokerMessageRoutes {

  public static final String TOPIC_DESTINATION = "/topic/events";
  public static final String USER_QUEUE_DESTINATION = "/queue/events";

  //----------------------------------------------------------------------------
  // The routes are prefixed with either "/user" or "/admin" to make it easier
  // to secure the routes by role. The only exception is the "register-user"
  // route which must be accessed by an anonymous user.
  //----------------------------------------------------------------------------

  //----------------------------------------------------------------------
  // User Management
  //----------------------------------------------------------------------
  public static final String ROUTE_USER_MANAGER_REGISTER_USER = "/register-user";
  public static final String ROUTE_USER_MANAGER_GET_CURRENT_USER = "/user/current-user";
  public static final String ROUTE_USER_MANAGER_USER_SEARCH = "/user/user-search";
  public static final String ROUTE_USER_MANAGER_UPDATE_USER = "/user/update-user";
  public static final String ROUTE_USER_MANAGER_UPDATE_PASSWORD = "/user/update-password";

  //----------------------------------------------------------------------
  // Cash Game Admin Routes
  //----------------------------------------------------------------------
  public static final String ROUTE_CASH_CREATE_GAME = "/admin/cash/create-game";
  public static final String ROUTE_CASH_UPDATE_GAME = "/admin/cash/update-game";
  public static final String ROUTE_CASH_DELETE_GAME = "/admin/cash/delete-game";

  //----------------------------------------------------------------------
  // Tournament Game Admin Routes
  //----------------------------------------------------------------------
  public static final String ROUTE_TOURNAMENT_CREATE_GAME = "/admin/tournament/create-game";
  public static final String ROUTE_TOURNAMENT_UPDATE_GAME = "/admin/tournament/update-game";
  public static final String ROUTE_TOURNAMENT_DELETE_GAME = "/admin/tournament/delete-game";

  //----------------------------------------------------------------------
  // Cash Game Routes
  //----------------------------------------------------------------------
  public static final String ROUTE_CASH_FIND_GAMES = "/user/cash/find-games";
  public static final String ROUTE_CASH_REGISTER_FOR_GAME = "/user/cash/register-for-game";
  public static final String ROUTE_CASH_JOIN_GAME = "/user/cash/join-game";
  public static final String ROUTE_CASH_GAME_COMMAND = "/user/cash/game-command";

  //----------------------------------------------------------------------
  // Tournament Game Routes
  //----------------------------------------------------------------------
  public static final String ROUTE_TOURNAMENT_FIND_GAMES = "/user/tournament/find-games";
  public static final String ROUTE_TOURNAMENT_REGISTER_FOR_GAME = "/user/tournament/register-for-game";
  public static final String ROUTE_TOURNAMENT_JOIN_GAME = "/user/tournament/join-game";
  public static final String ROUTE_TOURNAMENT_GAME_COMMAND = "/user/tournament/game-command";

  private PokerMessageRoutes() {
  }
}
