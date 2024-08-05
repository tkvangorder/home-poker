package org.homepoker.rest;

import org.homepoker.game.cash.CashGameDetails;
import org.homepoker.game.cash.CashGameService;
import org.homepoker.game.cash.CashGameConfiguration;
import org.homepoker.model.command.RegisterForGame;
import org.homepoker.model.command.UnregisterFromGame;
import org.homepoker.model.game.GameCriteria;
import org.homepoker.security.PokerUserDetails;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/cash-games")
public class CashGameController {
  private final CashGameService gameServer;

  public CashGameController(CashGameService gameServer) {
    this.gameServer = gameServer;
  }

  @PostMapping("/search")
  List<CashGameDetails> findGames(@RequestBody GameCriteria criteria) {
    return gameServer.findGames(criteria);
  }

  @PostMapping("")
  CashGameDetails createCashGame(@RequestBody CashGameConfiguration request, @AuthenticationPrincipal PokerUserDetails user) {

    return gameServer.createGame(
        CashGameDetails.builder()
            .name(request.name())
            .type(request.gameType())
            .startTime(request.startTime())
            .smallBlind(request.smallBlind())
            .bigBlind(request.bigBlind())
            .maxBuyIn(request.maxBuyIn())
            .owner(user.toUser())
            .build()
        );
  }

  @DeleteMapping("/{gameId}")
  void deleteGame(@RequestParam String gameId) {
    gameServer.deleteGame(gameId);
  }

  @PostMapping("/{gameId}/update")
  CashGameDetails updateGameDetails(@RequestBody CashGameConfiguration configuration, @RequestParam String gameId, @AuthenticationPrincipal PokerUserDetails user) {
    return gameServer.updateGameDetails(
        CashGameDetails.builder()
        .id(gameId)
        .name(configuration.name())
        .type(configuration.gameType())
        .startTime(configuration.startTime())
        .smallBlind(configuration.smallBlind())
        .bigBlind(configuration.bigBlind())
        .maxBuyIn(configuration.maxBuyIn())
        .owner(user.toUser())
        .build());
  }

  @PostMapping("/{gameId}/register")
  void registerForGame(@RequestParam String gameId, @AuthenticationPrincipal PokerUserDetails user) {

    gameServer.getGameManger(gameId).submitCommand(RegisterForGame.builder()
        .gameId(gameId)
        .user(user.toUser())
        .build()
    );
  }

  @PostMapping("/{gameId}/unregister")
  void unregisterFromGame(@RequestParam String gameId, @AuthenticationPrincipal PokerUserDetails user) {

    gameServer.getGameManger(gameId).submitCommand(UnregisterFromGame.builder()
        .gameId(gameId)
        .user(user.toUser())
        .build()
    );
  }

}
