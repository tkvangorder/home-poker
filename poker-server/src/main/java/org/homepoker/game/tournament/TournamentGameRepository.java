package org.homepoker.game.tournament;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

/**
 * A repository for persisting and retrieving games.
 *
 * @author tyler.vangorder
 */
@Repository
public interface TournamentGameRepository extends MongoRepository<TournamentGame, String> {
}
