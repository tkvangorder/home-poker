package org.homepoker.game.cash;

import org.homepoker.game.Game;
import org.homepoker.model.game.GameStatus;
import org.homepoker.model.user.User;
import org.homepoker.test.BaseIntegrationTest;
import org.homepoker.test.TestDataHelper;
import org.homepoker.user.UserManager;
import org.homepoker.utils.DateTimeUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.List;

import static java.time.temporal.ChronoUnit.MINUTES;
import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("NotNullFieldNotInitialized")
public class CashGameServiceTest extends BaseIntegrationTest {

  @Autowired
  CashGameService cashGameService;

  @Autowired
  CashGameRepository cashGameRepository;

  @Autowired
  UserManager userManager;

  @Test
  public void createGame() {

    User user = userManager.registerUser(TestDataHelper.user("fred", "password", "Fred"));

    Instant game1Start = DateTimeUtils.computeNextWallMinute();
    Instant game2Start = game1Start.plus(1, MINUTES);
    Instant game3Start = game1Start.plus(2, MINUTES);

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

    assertThat(game3.players().get(user.id())).isNotNull();
    assertThat(game3.id()).isNotNull();
    assertThat(game3.status()).isEqualTo(GameStatus.SCHEDULED);
    assertThat(game3.startTime()).isEqualTo(game3Start);


  }
}
