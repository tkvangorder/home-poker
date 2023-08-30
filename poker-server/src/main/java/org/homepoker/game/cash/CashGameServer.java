package org.homepoker.game.cash;

import org.homepoker.common.ValidationException;
import org.homepoker.game.GameCriteria;
import org.homepoker.game.GameManager;

import java.util.List;

public interface CashGameServer {

  /**
   * Find cash games that have been persisted.
   *
   * @param criteria The search criteria
   * @return A list of games that match the criteria.
   */
  List<CashGameDetails> findGames(GameCriteria criteria);

  /**
   * Get the game manager for a given gameId.
   *
   * @param gameId The game Id
   * @return A game manager for the game or an error if the game does not exist.
   */
  GameManager<CashGame> getGameManger(String gameId);

  /**
   * Create/schedule a new game.
   * <p>
   * The game state passed into this method will be validated.
   *
   * @param gameDetails The passed in game state is used to persist a new game.
   * @return The fully persisted game state. If the game's start date is past the current time-stamp, it will be marked as "PAUSED"
   * @throws ValidationException If a validation error occurs
   */
  CashGameDetails createGame(CashGameDetails gameDetails);

  /**
   * Update an existing game.
   *
   * @param gameDetails The game details to be applied to the game.
   * @return The updated game state.
   * @throws ValidationException If the game does not exist or a validation error occurs
   */
  CashGameDetails updateGameDetails(CashGameDetails gameDetails);

  /**
   * Retrieve the game details for an existing game.
   *
   * @param gameId The Id of the game.
   * @return The game details or an error if the game does not exist.
   * @throws ValidationException If the game does not exist or a validation error occurs
   */
  CashGameDetails getGameDetails(String gameId);

  /**
   * Delete an existing game.
   *
   * @param gameId The ID of the game
   * @throws ValidationException If the game cannot be deleted.
   */
  void deleteGame(String gameId);
}
