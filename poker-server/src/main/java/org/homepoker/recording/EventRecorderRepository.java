package org.homepoker.recording;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EventRecorderRepository extends MongoRepository<RecordedEvent, String> {

  /**
   * Primary replay query — events for one hand at one table on one game, ordered first by
   * the per-table sequence number (deterministic for stamped broadcast events) and secondarily
   * by {@code recordedAt} (stable for {@code UserEvent}s whose seq is always 0).
   */
  List<RecordedEvent> findByGameIdAndTableIdAndHandNumberOrderBySequenceNumberAscRecordedAtAsc(
      String gameId, String tableId, Integer handNumber);

  /**
   * Latest hand on a table — used by {@link EventRecorder}'s startup recovery to seed
   * {@code currentHandByTable} for tables that are still in a hand at server restart.
   */
  Optional<RecordedEvent> findTopByGameIdAndTableIdOrderByHandNumberDesc(
      String gameId, String tableId);
}
