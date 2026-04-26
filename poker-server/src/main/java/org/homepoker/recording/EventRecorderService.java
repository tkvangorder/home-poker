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

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Owns the async write pipeline for {@link EventRecorder} captures. The recorder calls
 * {@link #offer(RecordedEvent)} on the game-loop thread; that call is non-blocking and
 * returns false on overflow. A single virtual-thread worker drains the queue and writes
 * to {@link EventRecorderRepository}.
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
  private final LinkedBlockingQueue<RecordedEvent> queue;

  private final AtomicLong droppedEventCount = new AtomicLong();
  private final AtomicLong writtenEventCount = new AtomicLong();

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
    // Drain whatever is left synchronously.
    drainRemaining();
  }

  /**
   * Convert the event to a {@code Map<String, Object>} payload using the polymorphic
   * Jackson module configured on {@code webSocketObjectMapper}. This preserves the
   * {@code eventType} discriminator inside the nested document.
   */
  public Map<String, Object> toPayload(Object event) {
    return objectMapper.convertValue(event, PAYLOAD_TYPE_REF);
  }

  /**
   * Non-blocking enqueue. Returns false on overflow; caller has already paid the cost of
   * building the event but the game loop is not blocked. On overflow we increment the
   * dropped-event counter and log every {@value #LOG_DROP_EVERY_N} drops.
   */
  public boolean offer(RecordedEvent event) {
    if (!queue.offer(event)) {
      long dropped = droppedEventCount.incrementAndGet();
      if (dropped % LOG_DROP_EVERY_N == 0) {
        log.warn("event-recorder queue overflowed; total dropped = {} (capacity = {}, written = {})",
            dropped, queue.remainingCapacity() + queue.size(), writtenEventCount.get());
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
   * Seed {@code currentHandByTable} for tables that were mid-hand at server restart. For
   * each non-{@code COMPLETED} game whose tables are {@code PLAYING}, look up the most
   * recently recorded {@code handNumber} for that table.
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
      RecordedEvent next;
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
    RecordedEvent next;
    while ((next = queue.poll()) != null) {
      writeOne(next);
    }
  }

  private void writeOne(RecordedEvent event) {
    try {
      repository.save(event);
      writtenEventCount.incrementAndGet();
    } catch (RuntimeException e) {
      log.error("event-recorder worker failed to persist event eventType={} gameId={} tableId={} (worker continuing)",
          event.eventType(), event.gameId(), event.tableId(), e);
    }
  }
}
