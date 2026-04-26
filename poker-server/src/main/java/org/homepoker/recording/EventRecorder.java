package org.homepoker.recording;

import lombok.extern.slf4j.Slf4j;
import org.homepoker.game.UserGameListener;
import org.homepoker.model.event.GameEvent;
import org.homepoker.model.event.PokerEvent;
import org.homepoker.model.event.TableEvent;
import org.homepoker.model.event.UserEvent;
import org.homepoker.model.event.table.HandComplete;
import org.homepoker.model.event.table.HandStarted;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Captures every event passing through the {@code GameManager.processGameTick} fan-out and
 * forwards it to {@link EventRecorderService} for asynchronous persistence.
 *
 * <p>Hand windowing — the recorder maintains an in-memory {@code currentHandByTable} map.
 * Tagging precedence (per the 2026-04-25 spec):
 * <ol>
 *   <li>If event is {@code HandStarted}: tag with {@code event.handNumber()}, then update the tracker.</li>
 *   <li>If event is {@code HandComplete}: tag with {@code event.handNumber()}, then remove the tracker entry.</li>
 *   <li>Else if event is a {@code TableEvent}: tag with {@code currentHandByTable.get(tableId)} (may be null).</li>
 *   <li>Else: {@code handNumber = null} (game-level events never carry a hand number).</li>
 * </ol>
 *
 * <p>Errors thrown inside {@code onEvent} are caught and logged — a broken recorder must
 * not break the game loop.
 *
 * <p><strong>Threading:</strong> {@code currentHandByTable} is mutated only from the
 * game-loop thread (the sole caller of {@code onEvent}). Do not invoke {@code onEvent}
 * concurrently — the backing map is a plain {@link HashMap}.
 */
@Slf4j
public class EventRecorder extends UserGameListener {

  private final EventRecorderService service;
  private final Map<String, Integer> currentHandByTable;

  public EventRecorder(EventRecorderService service) {
    this(service, new HashMap<>());
  }

  /**
   * Constructor for tests — accepts a pre-seeded hand tracker (e.g., from
   * {@link EventRecorderService#seedHandTracker()} during server restart recovery).
   */
  public EventRecorder(EventRecorderService service, Map<String, Integer> initialHandByTable) {
    super(SystemUsers.EVENT_RECORDER);
    this.service = service;
    this.currentHandByTable = new HashMap<>(initialHandByTable);
  }

  @Override
  public boolean acceptsEvent(PokerEvent event) {
    return true;
  }

  @Override
  public void onEvent(PokerEvent event) {
    try {
      String gameId = (event instanceof GameEvent ge) ? ge.gameId() : null;
      String tableId = (event instanceof TableEvent te) ? te.tableId() : null;
      String userId = (event instanceof UserEvent ue) ? ue.userId() : null;

      Integer handNumber = computeHandNumber(event, tableId);

      long sequenceNumber = (event instanceof GameEvent ge) ? ge.sequenceNumber() : 0L;

      // Hand the raw event off to the worker thread; the heavy ObjectMapper.convertValue
      // call happens inside writeOne(), not here on the game-loop thread.
      PendingRecording pending = new PendingRecording(
          event,
          gameId,
          tableId,
          handNumber,
          userId,
          sequenceNumber,
          event.timestamp(),
          Instant.now()
      );

      service.offer(pending);

      // Update the tracker AFTER tagging so HandStarted is recorded with its own number
      // and HandComplete is recorded under the closing hand.
      updateHandTracker(event, tableId);
    } catch (RuntimeException e) {
      log.error("EventRecorder.onEvent threw on event {}; swallowed to protect the game loop.",
          event.eventType(), e);
    }
  }

  private Integer computeHandNumber(PokerEvent event, String tableId) {
    if (event instanceof HandStarted hs) return hs.handNumber();
    if (event instanceof HandComplete hc) return hc.handNumber();
    if (tableId == null) return null;
    return currentHandByTable.get(tableId);
  }

  private void updateHandTracker(PokerEvent event, String tableId) {
    if (tableId == null) return;
    if (event instanceof HandStarted hs) {
      currentHandByTable.put(tableId, hs.handNumber());
    } else if (event instanceof HandComplete) {
      currentHandByTable.remove(tableId);
    }
  }

  /** Test-only inspection. */
  Map<String, Integer> currentHandByTableSnapshot() {
    return Map.copyOf(currentHandByTable);
  }
}
