package org.homepoker;

public class PokerMessageRoutes {

  public static final String TOPIC_DESTINATION = "/topic/events";
  public static final String USER_QUEUE_DESTINATION = "/queue/events";

  //----------------------------------------------------------------------
  // User Manager Routes
  //----------------------------------------------------------------------
  public static final String ROUTE_USER_MANAGER_REGISTER_USER = "/user/register-user";
  public static final String ROUTE_USER_MANAGER_GET_CURRENT_USER = "/user/current-user";
  public static final String ROUTE_USER_MANAGER_USER_SEARCH = "/user/user-search";
  public static final String ROUTE_USER_MANAGER_UPDATE_USER = "/user/update-user";
  public static final String ROUTE_USER_MANAGER_UPDATE_PASSWORD = "/user/update-password";

  //----------------------------------------------------------------------
  // Cash Game Admin Routes
  //----------------------------------------------------------------------
  public static final String ROUTE_CASH_CREATE_GAME = "/cash/create-game";
  public static final String ROUTE_CASH_UPDATE_GAME = "/cash/update-game";
  public static final String ROUTE_CASH_DELETE_GAME = "/cash/delete-game";

  //----------------------------------------------------------------------
  // Tournament Game Admin Routes
  //----------------------------------------------------------------------
  public static final String ROUTE_TOURNAMENT_CREATE_GAME = "/tournament/create-game";
  public static final String ROUTE_TOURNAMENT_UPDATE_GAME = "/tournament/update-game";
  public static final String ROUTE_TOURNAMENT_DELETE_GAME = "/tournament/delete-game";

  //----------------------------------------------------------------------
  // Cash Game Routes
  //----------------------------------------------------------------------
  public static final String ROUTE_CASH_FIND_GAMES = "/cash/find-games";
  public static final String ROUTE_CASH_REGISTER_FOR_GAME = "/cash/register-for-game";
  public static final String ROUTE_CASH_JOIN_GAME = "/cash/join-game";
  public static final String ROUTE_CASH_GAME_COMMAND = "/cash/game-command";

  //----------------------------------------------------------------------
  // Tournament Game Routes
  //----------------------------------------------------------------------
  public static final String ROUTE_TOURNAMENT_FIND_GAMES = "/tournament/find-games";
  public static final String ROUTE_TOURNAMENT_REGISTER_FOR_GAME = "/tournament/register-for-game";
  public static final String ROUTE_TOURNAMENT_JOIN_GAME = "/tournament/join-game";
  public static final String ROUTE_TOURNAMENT_GAME_COMMAND = "/tournament/game-command";

  private PokerMessageRoutes() {
  }
}
