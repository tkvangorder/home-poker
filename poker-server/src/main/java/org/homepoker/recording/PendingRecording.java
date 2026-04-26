package org.homepoker.recording;

import org.homepoker.model.event.PokerEvent;

import java.time.Instant;

/**
 * Internal queue payload for the recorder pipeline. Carries the raw event plus the
 * metadata the recorder computed on the game-loop thread (gameId, tableId, handNumber,
 * etc.). The worker thread materializes the persisted {@link RecordedEvent} from this
 * wrapper by calling {@code EventRecorderService.toPayload(event)}.
 *
 * <p>Splitting metadata-computation from payload-conversion keeps the heavy
 * {@code ObjectMapper.convertValue(...)} call off the game-loop thread.
 *
 * <p>Package-private — never escapes the {@code recording} package.
 */
record PendingRecording(
    PokerEvent event,
    String gameId,
    String tableId,
    Integer handNumber,
    String userId,
    long sequenceNumber,
    Instant eventTimestamp,
    Instant recordedAt
) {
}
