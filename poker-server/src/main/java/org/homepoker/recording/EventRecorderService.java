package org.homepoker.recording;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.homepoker.game.cash.CashGameRepository;
import org.homepoker.model.game.GameStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Owns the async write pipeline for {@link EventRecorder} captures. The recorder calls
 * {@link #offer(PendingRecording)} on the game-loop thread; that call is non-blocking and
 * returns false on overflow. A single virtual-thread worker drains the queue, converts each
 * pending recording's payload, and writes a {@link RecordedEvent} via
 * {@link EventRecorderRepository}.
 *
 * <p>The queue carries {@link PendingRecording} (metadata + raw event reference) rather than
 * fully-materialized {@link RecordedEvent} instances, so the heavy
 * {@code ObjectMapper.convertValue(...)} call stays off the game-loop thread.
 *
 * <p>Per the spec: a backed-up Mongo cannot stall the game tick. On overflow the dropped
 * event is counted in {@link #droppedEventCount()} and logged once per N drops.
 */
@Slf4j
@Service
public class EventRecorderService {

  private static final int LOG_DROP_EVERY_N = 100;
  private static final TypeReference<Map<String, Object>> PAYLOAD_TYPE_REF =
      new TypeReference<>() {};

  private final EventRecorderRepository repository;
  private final CashGameRepository cashGameRepository;
  private final ObjectMapper objectMapper;
  private final LinkedBlockingQueue<PendingRecording> queue;
  private final int queueCapacity;

  private final AtomicLong droppedEventCount = new AtomicLong();
  private final AtomicLong writtenEventCount = new AtomicLong();
  private final AtomicLong shutdownLossCount = new AtomicLong();

  private volatile boolean running;
  private Thread worker;

  public EventRecorderService(
      EventRecorderRepository repository,
      CashGameRepository cashGameRepository,
      ObjectMapper webSocketObjectMapper,
      @Value("${poker.recording.queue-capacity:10000}") int queueCapacity) {
    this.repository = repository;
    this.cashGameRepository = cashGameRepository;
    this.objectMapper = webSocketObjectMapper;
    this.queueCapacity = queueCapacity;
    this.queue = new LinkedBlockingQueue<>(queueCapacity);
  }

  @PostConstruct
  public void start() {
    running = true;
    worker = Thread.ofVirtual()
        .name("event-recorder-worker")
        .start(this::drainLoop);
  }

  @PreDestroy
  public void stop() {
    running = false;
    if (worker != null) {
      worker.interrupt();
      try {
        worker.join(5_000);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }
    int leftAtStart = queue.size();
    drainRemaining();
    if (leftAtStart > 0) {
      long lost = shutdownLossCount.get();
      if (lost > 0) {
        log.warn("event-recorder shutdown drained {} pending events; {} write failures (likely Mongo client already closed) — those events are lost",
            leftAtStart, lost);
      } else {
        log.info("event-recorder shutdown drained {} pending events successfully", leftAtStart);
      }
    }
  }

  /**
   * Convert the event to a {@code Map<String, Object>} payload using the polymorphic
   * Jackson module configured on {@code webSocketObjectMapper}. This preserves the
   * {@code eventType} discriminator inside the nested document.
   *
   * <p>Called from the worker thread, NOT the game-loop thread.
   */
  Map<String, Object> toPayload(Object event) {
    return objectMapper.convertValue(event, PAYLOAD_TYPE_REF);
  }

  /**
   * Non-blocking enqueue. Returns false on overflow. The game loop has paid the cost of
   * computing the metadata fields, but conversion of the event payload to BSON happens on
   * the worker thread. On overflow we increment the dropped-event counter and log every
   * {@value #LOG_DROP_EVERY_N} drops.
   */
  public boolean offer(PendingRecording pending) {
    if (!queue.offer(pending)) {
      long dropped = droppedEventCount.incrementAndGet();
      if (dropped % LOG_DROP_EVERY_N == 0) {
        log.warn("event-recorder queue overflowed; total dropped = {} (capacity = {}, written = {})",
            dropped, queueCapacity, writtenEventCount.get());
      }
      return false;
    }
    return true;
  }

  public long droppedEventCount() {
    return droppedEventCount.get();
  }

  public long writtenEventCount() {
    return writtenEventCount.get();
  }

  /**
   * Seed {@code currentHandByTable} for tables that were mid-hand at server restart.
   * Intended for <strong>startup-only</strong> invocation (single-shot, before any commands
   * flow). For each non-{@code COMPLETED} game whose tables are {@code PLAYING}, look up
   * the most recently recorded {@code handNumber} for that table.
   */
  public Map<String, Integer> seedHandTracker() {
    Map<String, Integer> seed = new HashMap<>();
    cashGameRepository.findAll().forEach(game -> {
      if (game.status() == GameStatus.COMPLETED) return;
      game.tables().values().stream()
          .filter(table -> table.status() == org.homepoker.model.game.Table.Status.PLAYING)
          .forEach(table -> repository
              .findTopByGameIdAndTableIdOrderByHandNumberDesc(game.id(), table.id())
              .ifPresent(rec -> {
                if (rec.handNumber() != null) {
                  seed.put(table.id(), rec.handNumber());
                }
              }));
    });
    return seed;
  }

  private void drainLoop() {
    while (running) {
      PendingRecording next;
      try {
        next = queue.poll(1, TimeUnit.SECONDS);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return;
      }
      if (next == null) continue;
      writeOne(next);
    }
  }

  private void drainRemaining() {
    PendingRecording next;
    while ((next = queue.poll()) != null) {
      writeOne(next);
    }
  }

  private void writeOne(PendingRecording pending) {
    RecordedEvent recorded;
    try {
      Map<String, Object> payload = toPayload(pending.event());
      recorded = new RecordedEvent(
          null,
          pending.gameId(),
          pending.tableId(),
          pending.handNumber(),
          pending.userId(),
          pending.event().eventType(),
          pending.sequenceNumber(),
          pending.eventTimestamp(),
          pending.recordedAt(),
          payload
      );
    } catch (RuntimeException e) {
      log.error("event-recorder failed to convert event to payload eventType={} gameId={} (worker continuing)",
          pending.event().eventType(), pending.gameId(), e);
      if (!running) shutdownLossCount.incrementAndGet();
      return;
    }
    try {
      repository.save(recorded);
      writtenEventCount.incrementAndGet();
    } catch (RuntimeException e) {
      if (running) {
        log.error("event-recorder worker failed to persist event eventType={} gameId={} tableId={} (worker continuing)",
            recorded.eventType(), recorded.gameId(), recorded.tableId(), e);
      } else {
        // Shutdown — Mongo client may already be closing. Don't flood logs; aggregate.
        shutdownLossCount.incrementAndGet();
      }
    }
  }
}
