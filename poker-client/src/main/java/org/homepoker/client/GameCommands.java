package org.homepoker.client;

import lombok.extern.slf4j.Slf4j;
import org.homepoker.lib.exception.ValidationException;
import org.homepoker.lib.util.DateUtils;
import org.homepoker.lib.util.JsonUtils;
import org.homepoker.domain.game.CashGameDetails;
import org.homepoker.domain.game.GameStatus;
import org.homepoker.domain.game.TournamentGameDetails;
import org.springframework.shell.Availability;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellMethodAvailability;
import org.springframework.shell.standard.ShellOption;

import java.time.LocalDate;

@Slf4j
@ShellComponent
public class GameCommands {

  private final ClientConnectionManager connectionManager;

  public GameCommands(ClientConnectionManager connectionManager) {
    super();
    this.connectionManager = connectionManager;
  }

  @ShellMethod("Find matching cash games [name, start-date, end-date].")
  public void findCashGames(
      @ShellOption(defaultValue = "t", help = "Find all games on or after this given start date.") String startDate,
      @ShellOption(defaultValue = ShellOption.NULL, help = "Find all games on or before this given end date.") String endDate,
      @ShellOption(defaultValue = ShellOption.NULL, help = "The name of the game, supports regular expressions.") String name,
      @ShellOption(defaultValue = ShellOption.NULL, help = "The status of the game") String status) {

    GameStatus gameStatus = null;
    if (status != null) {
      gameStatus = GameStatus.valueOf(status);
    }
    LocalDate start = DateUtils.stringToDate(startDate);
    LocalDate end = DateUtils.stringToDate(endDate);


    log.info("Search Complete. Found [" + 0 + "] games.");
  }

  @ShellMethod("Register for a cash game.")
  public void registerForCashGame(String gameId) {

    if (connectionManager.getCurrentUser() == null) {
      throw new ValidationException("You cannot register for a game unless you are logged in.");
    }

    CashGameDetails gameDetails = null;

    log.info("You have registered for the game. :\n" + JsonUtils.toFormattedJson(gameDetails));
  }

  @ShellMethod("Find matching tournament games [start-date, end-date, name, status].")
  public void findTournamentGames(
      @ShellOption(defaultValue = "t", help = "Find all games on or after this given start date.") String startDate,
      @ShellOption(defaultValue = ShellOption.NULL, help = "Find all games on or before this given end date.") String endDate,
      @ShellOption(defaultValue = ShellOption.NULL, help = "The name of the game, supports regular expressions.") String name,
      @ShellOption(defaultValue = ShellOption.NULL, help = "The status of the game") String status) {

    GameStatus gameStatus = null;
    if (status != null) {
      gameStatus = GameStatus.valueOf(status);
    }
    LocalDate start = DateUtils.stringToDate(startDate);
    LocalDate end = DateUtils.stringToDate(endDate);

    log.info("Search Complete. Found [" + 0 + "] games.");
  }

  @ShellMethod("Register for a tournament.")
  public void registerForTournament(String gameId) {

    if (connectionManager.getCurrentUser() == null) {
      throw new ValidationException("You cannot register for a game unless you are logged in.");
    }

    TournamentGameDetails gameDetails = null;

    log.info("You have registered for the game. :\n" + JsonUtils.toFormattedJson(gameDetails));
  }

  @ShellMethodAvailability({"find-cash-games", "find-tournament-games", "register-for-tournament", "register-for-cash-game"})
  private Availability validConnection() {
    return (connectionManager.getCurrentUser() == null) ?
        Availability.unavailable("You must be logged in as a user.") :
        Availability.available();
  }
}
