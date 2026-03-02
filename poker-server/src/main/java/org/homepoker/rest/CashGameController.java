package org.homepoker.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.homepoker.game.cash.*;
import org.homepoker.model.command.RegisterForGame;
import org.homepoker.model.command.UnregisterFromGame;
import org.homepoker.model.game.GameCriteria;
import org.homepoker.model.game.cash.CashGameDetails;
import org.homepoker.security.PokerUserDetails;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/cash-games")
@Tag(name = "Cash Games", description = "Cash game management and player registration")
public class CashGameController {
  private final CashGameService gameServer;


  public CashGameController(CashGameService gameServer) {
    this.gameServer = gameServer;
  }

  @PostMapping("/search")
  @Operation(summary = "Search cash games", description = "Search for cash games by name, status, or time range.")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "Games matching the criteria"),
      @ApiResponse(responseCode = "401", description = "Not authenticated")
  })
  List<CashGameDetails> findGames(@RequestBody GameCriteria criteria) {
    return gameServer.findGames(criteria == null ? GameCriteria.builder().build() : criteria);
  }

  @PostMapping("")
  @Operation(summary = "Create a cash game", description = "Create a new cash game. The authenticated user becomes the game owner.")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "Game created successfully"),
      @ApiResponse(responseCode = "400", description = "Invalid game configuration"),
      @ApiResponse(responseCode = "401", description = "Not authenticated")
  })
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

  @GetMapping("/{gameId}")
  @Operation(summary = "Get cash game details", description = "Retrieve the details of a specific cash game.")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "Game details returned successfully"),
      @ApiResponse(responseCode = "401", description = "Not authenticated"),
      @ApiResponse(responseCode = "404", description = "Game not found")
  })
  CashGameDetails getGameDetails(@Parameter(description = "ID of the game") @RequestParam String gameId) {
    return gameServer.getGameDetails(gameId);
  }

  @DeleteMapping("/{gameId}")
  @Operation(summary = "Delete a cash game", description = "Permanently delete a cash game.")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "Game deleted successfully"),
      @ApiResponse(responseCode = "401", description = "Not authenticated"),
      @ApiResponse(responseCode = "404", description = "Game not found")
  })
  void deleteGame(@Parameter(description = "ID of the game to delete") @RequestParam String gameId) {
    gameServer.deleteGame(gameId);
  }

  @PostMapping("/{gameId}/update")
  @Operation(summary = "Update cash game details", description = "Update the configuration of an existing cash game.")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "Game updated successfully"),
      @ApiResponse(responseCode = "400", description = "Invalid game configuration"),
      @ApiResponse(responseCode = "401", description = "Not authenticated"),
      @ApiResponse(responseCode = "404", description = "Game not found")
  })
  CashGameDetails updateGameDetails(
      @RequestBody CashGameConfiguration configuration,
      @Parameter(description = "ID of the game to update") @RequestParam String gameId,
      @AuthenticationPrincipal PokerUserDetails user) {
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
  @Operation(summary = "Register for a cash game", description = "Register the authenticated user as a player in the specified cash game.")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "Registered successfully"),
      @ApiResponse(responseCode = "401", description = "Not authenticated"),
      @ApiResponse(responseCode = "404", description = "Game not found")
  })
  void registerForGame(@Parameter(description = "ID of the game to join") @RequestParam String gameId, @AuthenticationPrincipal PokerUserDetails user) {
    gameServer.getGameManger(gameId).submitCommand(new RegisterForGame(gameId, user.toUser()));
  }

  @PostMapping("/{gameId}/unregister")
  @Operation(summary = "Unregister from a cash game", description = "Remove the authenticated user from the specified cash game.")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "Unregistered successfully"),
      @ApiResponse(responseCode = "401", description = "Not authenticated"),
      @ApiResponse(responseCode = "404", description = "Game not found")
  })
  void unregisterFromGame(@Parameter(description = "ID of the game to leave") @RequestParam String gameId, @AuthenticationPrincipal PokerUserDetails user) {

    gameServer.getGameManger(gameId).submitCommand(new UnregisterFromGame(gameId, user.toUser()));
  }

}