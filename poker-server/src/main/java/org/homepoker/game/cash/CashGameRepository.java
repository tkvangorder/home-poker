package org.homepoker.game.cash;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

/**
 * A repository for persisting and retrieving games.
 *
 * @author tyler.vangorder
 */
@Repository
public interface CashGameRepository extends MongoRepository<CashGame, String> {
}
