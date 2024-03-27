package org.homepoker.rest;

import org.homepoker.game.cash.CashGameDetails;
import org.homepoker.game.cash.CashGameServer;
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
    CashGameDetails createCashGame(@RequestBody CashGameDetails configuration, @AuthenticationPrincipal PokerUserDetails user) {
        configuration.setOwnerLoginId(user.getUsername());
        return gameServer.createGame(configuration);
    }

    @DeleteMapping("/{gameId}")
    void deleteGame(@RequestParam String gameId) {
        gameServer.deleteGame(gameId);
    }

    @PostMapping("/{gameId}/update")
    CashGameDetails updateGameDetails(@RequestBody CashGameDetails configuration) {
        return gameServer.updateGameDetails(configuration);
    }

    @PostMapping("/{gameId}/register")
    void registerForGame(@RequestParam String gameId, @AuthenticationPrincipal PokerUserDetails user) {
        gameServer.getGameManger(gameId).submitCommand(GameCommand.asRegisterUser(user.toUser(), gameId));
    }

}
