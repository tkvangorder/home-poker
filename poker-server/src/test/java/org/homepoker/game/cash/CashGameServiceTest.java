package org.homepoker.game.cash;

import org.homepoker.game.Table;
import org.homepoker.model.command.EndGame;
import org.homepoker.model.game.GameStatus;
import org.homepoker.model.game.GameType;
import org.homepoker.model.game.Player;
import org.homepoker.model.user.User;
import org.homepoker.test.BaseIntegrationTest;
import org.homepoker.test.TestDataHelper;
import org.homepoker.utils.DateTimeUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.Map;

import static java.time.temporal.ChronoUnit.MINUTES;
import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("NotNullFieldNotInitialized")
public class CashGameServiceTest extends BaseIntegrationTest {

  @Autowired
  CashGameService cashGameService;

  @Test
  public void createGame() {

    User user = createUser(TestDataHelper.fred());

    Instant game1Start = DateTimeUtils.computeNextWallMinute();
    Instant game2Start = game1Start.plus(1, MINUTES);
    Instant game3Start = game1Start.plus(-2, MINUTES);
    try {
      CashGameDetails details1 = cashGameService.createGame(TestDataHelper.cashGameDetails("Test Game 1", user)
          .withStartTime(game1Start)
      );
      CashGameDetails details2 = cashGameService.createGame(TestDataHelper.cashGameDetails("Test Game 2", user)
          .withStartTime(game2Start)
      );
      CashGameDetails details3 = cashGameService.createGame(TestDataHelper.cashGameDetails("Test Game 3", user)
          .withStartTime(game3Start)
      );

      CashGame game1 = cashGameRepository.findById(details1.id()).orElseThrow();
      CashGame game2 = cashGameRepository.findById(details2.id()).orElseThrow();
      CashGame game3 = cashGameRepository.findById(details3.id()).orElseThrow();

      assertThat(game1.players().get(user.id())).isNotNull();
      assertThat(game1.id()).isNotNull();
      assertThat(game1.status()).isEqualTo(GameStatus.SCHEDULED);
      assertThat(game1.startTime()).isEqualTo(game1Start);

      assertThat(game2.players().get(user.id())).isNotNull();
      assertThat(game2.id()).isNotNull();
      assertThat(game2.status()).isEqualTo(GameStatus.SCHEDULED);
      assertThat(game2.startTime()).isEqualTo(game2Start);

      // Games in the past are set to paused
      assertThat(game3.players().get(user.id())).isNotNull();
      assertThat(game3.id()).isNotNull();
      assertThat(game3.status()).isEqualTo(GameStatus.SCHEDULED);
      assertThat(game3.startTime()).isEqualTo(game3Start);
    } finally {
      // Clean up afterward.
      cashGameService.getGameManagerMap().clear();
      cashGameRepository.deleteAll();
    }
  }

  @Test
  public void loadGames() {
    User user = createUser(TestDataHelper.fred());

    Instant game1Start = DateTimeUtils.computeNextWallMinute();
    Instant game2Start = game1Start.plus(1, MINUTES);
    Instant game3Start = game1Start.plus(-2, MINUTES);

    try {
      CashGameDetails details1 = cashGameService.createGame(TestDataHelper.cashGameDetails("Test Game 1", user)
          .withStartTime(game1Start)
      );
      CashGameDetails details2 = cashGameService.createGame(TestDataHelper.cashGameDetails("Test Game 2", user)
          .withStartTime(game2Start)
      );
      CashGameDetails details3 = cashGameService.createGame(TestDataHelper.cashGameDetails("Test Game 3", user)
          .withStartTime(game3Start)
      );

      cashGameService.loadNewGames();

      Map<String, CashGameManager> gameManagerMap = cashGameService.getGameManagerMap();
      assertThat(gameManagerMap).hasSize(3);
      assertThat(gameManagerMap.get(details1.id())).isNotNull();
      assertThat(gameManagerMap.get(details2.id())).isNotNull();
      assertThat(gameManagerMap.get(details3.id())).isNotNull();

    } finally {

      // Clean up afterward.
      cashGameService.getGameManagerMap().clear();
      cashGameRepository.deleteAll();
    }
  }

  @Test
  public void loadGamesNoCompleted() {
    User user = createUser(TestDataHelper.fred());

    Instant game1Start = DateTimeUtils.computeNextWallMinute();

    try {

      CashGame game1 = cashGameRepository.save(
        CashGame.builder()
          .id("game1")
          .name("Test Game 1")
          .type(GameType.TEXAS_HOLDEM)
          .startTime(game1Start)
          .status(GameStatus.COMPLETED)
          .maxBuyIn(10000)
          .smallBlind(25)
          .bigBlind(50)
          .player(Player.builder().user(user).build())
          .table(Table.builder().build())
          .owner(user)
          .build());
      cashGameService.loadNewGames();

      Map<String, CashGameManager> gameManagerMap = cashGameService.getGameManagerMap();
      assertThat(gameManagerMap).hasSize(0);

    } finally {

      // Clean up afterward.
      cashGameService.getGameManagerMap().clear();
      cashGameRepository.deleteAll();
    }
  }

  @Test
  public void processGamesRemovesCompleted() {
    User user = createUser(TestDataHelper.adminUser());

    Instant game1Start = DateTimeUtils.computeNextWallMinute();

    try {
      CashGame game1 = cashGameRepository.save(
          CashGame.builder()
              .id("game1")
              .name("Test Game 1")
              .type(GameType.TEXAS_HOLDEM)
              .startTime(game1Start)
              .status(GameStatus.SCHEDULED)
              .maxBuyIn(10000)
              .smallBlind(25)
              .bigBlind(50)
              .player(Player.builder().user(user).build())
              .table(Table.builder().build())
              .owner(user)
              .build());
      cashGameService.loadNewGames();
      CashGameManager gameManager = cashGameService.getGameManagerMap().get("game1");
      gameManager.submitCommand(new EndGame("game1", user));
      // First process will execute the end game command
      cashGameService.processGames();
      // The second process will remove the game from the map
      cashGameService.processGames();
      assertThat(cashGameService.getGameManagerMap()).hasSize(0);

    } finally {

      // Clean up afterward.
      cashGameService.getGameManagerMap().clear();
      cashGameRepository.deleteAll();
    }
  }

  @Test
  public void processGames() {
    User user = createUser(TestDataHelper.fred());

    Instant game1Start = DateTimeUtils.computeNextWallMinute();
    Instant game2Start = game1Start.plus(1, MINUTES);
    Instant game3Start = game1Start.plus(-2, MINUTES);

    try {
      cashGameService.setLastGameCheck(Instant.now().minus(1, MINUTES));
      CashGameDetails details1 = cashGameService.createGame(TestDataHelper.cashGameDetails("Test Game 1", user)
          .withStartTime(game1Start)
      );
      CashGameDetails details2 = cashGameService.createGame(TestDataHelper.cashGameDetails("Test Game 2", user)
          .withStartTime(game2Start)
      );
      CashGameDetails details3 = cashGameService.createGame(TestDataHelper.cashGameDetails("Test Game 3", user)
          .withStartTime(game3Start)
      );

      cashGameService.processGames();

      Map<String, CashGameManager> gameManagerMap = cashGameService.getGameManagerMap();
      assertThat(gameManagerMap).hasSize(3);
      assertThat(gameManagerMap.get(details1.id())).isNotNull();
      assertThat(gameManagerMap.get(details2.id())).isNotNull();
      assertThat(gameManagerMap.get(details3.id())).isNotNull();

    } finally {

      // Clean up afterward.
      cashGameService.getGameManagerMap().clear();
      cashGameRepository.deleteAll();
    }
  }
}
