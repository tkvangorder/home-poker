package org.homepoker.rest;

import org.homepoker.game.cash.CashGameDetails;
import org.homepoker.game.cash.CashGameServer;
import org.homepoker.game.cash.CashGameConfiguration;
import org.homepoker.model.command.GameCommand;
import org.homepoker.model.game.GameCriteria;
import org.homepoker.security.PokerUserDetails;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/cash-games")
public class CashGameController {
  private final CashGameServer gameServer;

  public CashGameController(CashGameServer gameServer) {
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
            .id(request.id())
            .name(request.name())
            .gameType(request.gameType())
            .startTimestamp(request.startTimestamp())
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
        .id(configuration.id() == null ? gameId : configuration.id())
        .name(configuration.name())
        .gameType(configuration.gameType())
        .startTimestamp(configuration.startTimestamp())
        .smallBlind(configuration.smallBlind())
        .bigBlind(configuration.bigBlind())
        .maxBuyIn(configuration.maxBuyIn())
        .owner(user.toUser())
        .build());
  }

  @PostMapping("/{gameId}/register")
  void registerForGame(@RequestParam String gameId, @AuthenticationPrincipal PokerUserDetails user) {
    gameServer.getGameManger(gameId).submitCommand(GameCommand.asRegisterUser(user.toUser(), gameId));
  }

}
