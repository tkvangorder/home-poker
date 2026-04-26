package org.homepoker.recording;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Map;

/**
 * One event captured by {@link EventRecorder}. Stored in the {@code recordedEvents} Mongo
 * collection. Indexed by {@code (gameId, tableId, handNumber, sequenceNumber)} for replay.
 *
 * <p>Field nullability mirrors the spec:
 * <ul>
 *   <li>{@code gameId} — always present</li>
 *   <li>{@code tableId} — null for game-level events</li>
 *   <li>{@code handNumber} — null for events outside any hand window</li>
 *   <li>{@code userId} — <strong>recipient</strong> only: set when the event implements
 *       {@code UserEvent} (e.g., {@code HoleCardsDealt} addressed to a specific player).
 *       The <em>actor</em> userId for events like {@code PlayerActed} is NOT denormalized
 *       to this column; consumers querying by actor must crack open {@code payload.userId}
 *       (un-indexed). The replay endpoint in v1 only filters by
 *       {@code (gameId, tableId, handNumber)} so this is acceptable for now.</li>
 *   <li>{@code sequenceNumber} — 0 for {@code UserEvent}s; the real per-stream seq otherwise</li>
 * </ul>
 */
@Document(collection = "recordedEvents")
public record RecordedEvent(
    @Id String id,
    String gameId,
    String tableId,
    Integer handNumber,
    String userId,
    String eventType,
    long sequenceNumber,
    Instant eventTimestamp,
    Instant recordedAt,
    Map<String, Object> payload
) {
}
