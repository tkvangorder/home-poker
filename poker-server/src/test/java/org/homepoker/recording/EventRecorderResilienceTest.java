package org.homepoker.recording;

import org.homepoker.game.cash.CashGameRepository;
import org.homepoker.model.event.PokerEvent;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;

import static java.time.Duration.ofSeconds;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pure unit test (no Spring, no Mongo) for the bounded-queue + worker-error contract on
 * {@link EventRecorderService}. Lives in the {@code org.homepoker.recording} package so it
 * can construct the package-private {@link PendingRecording} record directly.
 */
class EventRecorderResilienceTest {

  private final ObjectMapper objectMapper = JsonMapper.builder().build();

  @Test
  void offerReturnsFalseOnOverflowAndIncrementsCounter() {
    EventRecorderRepository repo = mock(EventRecorderRepository.class);
    CashGameRepository gameRepo = mock(CashGameRepository.class);
    when(gameRepo.findAll()).thenReturn(Collections.emptyList());

    // Capacity 2, no worker started — the queue will fill up.
    EventRecorderService service = new EventRecorderService(repo, gameRepo, objectMapper, 2);
    // NOTE: do not call start(); we want offers to accumulate.

    assertThat(service.offer(pendingFor("e1"))).isTrue();
    assertThat(service.offer(pendingFor("e2"))).isTrue();
    // Queue is full. Subsequent offers must return false.
    assertThat(service.offer(pendingFor("e3"))).isFalse();
    assertThat(service.offer(pendingFor("e4"))).isFalse();

    assertThat(service.droppedEventCount()).isEqualTo(2L);
  }

  @Test
  void workerSurvivesPerEventRepositoryFailures() {
    AtomicInteger callCount = new AtomicInteger();
    EventRecorderRepository repo = mock(EventRecorderRepository.class);
    when(repo.save(any(RecordedEvent.class))).thenAnswer(invocation -> {
      int n = callCount.incrementAndGet();
      if (n == 2) {
        throw new RuntimeException("simulated mongo failure on event 2");
      }
      return invocation.getArgument(0);
    });
    CashGameRepository gameRepo = mock(CashGameRepository.class);
    when(gameRepo.findAll()).thenReturn(Collections.emptyList());

    EventRecorderService service = new EventRecorderService(repo, gameRepo, objectMapper, 100);
    service.start();
    try {
      service.offer(pendingFor("e1"));
      service.offer(pendingFor("e2"));   // worker throws here
      service.offer(pendingFor("e3"));
      service.offer(pendingFor("e4"));

      // Wait for the worker to process all four. Three should succeed, one threw — the
      // counter should reach 3.
      await().atMost(ofSeconds(5)).until(() -> service.writtenEventCount() == 3L);
    } finally {
      service.stop();
    }
  }

  /**
   * Builds a {@link PendingRecording} with a synthetic {@link StubEvent} payload. The
   * {@code marker} parameter is unused — included only so each call site reads as a
   * "named" event for clarity.
   */
  private PendingRecording pendingFor(String marker) {
    Instant now = Instant.now();
    return new PendingRecording(
        new StubEvent(now),
        "g1",
        "t1",
        1,
        null,
        0L,
        now,
        now
    );
  }

  /**
   * Minimal {@link PokerEvent} implementation. Lets us construct {@link PendingRecording}
   * without pulling in any real game-event types. {@code eventType()} defaults to
   * {@code "stub-event"} (kebab-case of the simple class name); {@code isValid()} defaults
   * to {@code timestamp() != null}.
   */
  private record StubEvent(Instant timestamp) implements PokerEvent {
  }
}
