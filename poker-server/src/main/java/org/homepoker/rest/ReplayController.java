package org.homepoker.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.homepoker.game.cash.CashGameManager;
import org.homepoker.game.cash.CashGameService;
import org.homepoker.model.command.AdminViewingReplayCommand;
import org.homepoker.model.game.GameStatus;
import org.homepoker.recording.EventRecorderRepository;
import org.homepoker.recording.RecordedEvent;
import org.homepoker.security.PokerUserDetails;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Admin-only replay endpoint. Returns the captured event stream for a single hand on a
 * single table. If the game is not yet {@code COMPLETED}, the live {@code CashGameManager}
 * receives an {@link AdminViewingReplayCommand} that emits {@code AdminViewingReplay} to
 * connected players — see the 2026-04-25 event-store-and-replay spec.
 */
@Slf4j
@RestController
@RequestMapping("/admin/replay")
@Tag(name = "Replay", description = "Admin-only event-stream replay")
public class ReplayController {

  private final EventRecorderRepository repository;
  private final CashGameService cashGameService;

  public ReplayController(EventRecorderRepository repository, CashGameService cashGameService) {
    this.repository = repository;
    this.cashGameService = cashGameService;
  }

  @GetMapping("/games/{gameId}/tables/{tableId}/hands/{handNumber}")
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(summary = "Replay one hand", description =
      "Returns all events captured for the given (gameId, tableId, handNumber) tuple, "
          + "ordered by sequenceNumber then recordedAt. Empty array if no events are found "
          + "(no distinction between 'never happened' and 'no events captured').")
  public ResponseEntity<List<RecordedEvent>> replayHand(
      @PathVariable String gameId,
      @PathVariable String tableId,
      @PathVariable Integer handNumber,
      @AuthenticationPrincipal PokerUserDetails admin) {

    notifyIfGameIsLive(gameId, tableId, handNumber, admin);

    List<RecordedEvent> events = repository
        .findByGameIdAndTableIdAndHandNumberOrderBySequenceNumberAscRecordedAtAsc(
            gameId, tableId, handNumber);

    return ResponseEntity.ok(events);
  }

  /**
   * Best-effort: if the game is in memory and not COMPLETED, submit the warning command.
   * The event itself is published on the next tick — the HTTP response does not wait for
   * delivery. If the {@code CashGameManager} cannot be looked up (e.g., the game does not
   * exist), the request still succeeds with an empty array.
   */
  private void notifyIfGameIsLive(String gameId, String tableId, int handNumber, PokerUserDetails admin) {
    CashGameManager manager;
    try {
      manager = cashGameService.getGameManger(gameId);
    } catch (RuntimeException e) {
      log.debug("AdminViewingReplay warning skipped — no live manager for [{}]: {}", gameId, e.getMessage());
      return;
    }
    if (manager.gameStatus() == GameStatus.COMPLETED) {
      return;
    }
    manager.submitCommand(new AdminViewingReplayCommand(
        gameId, admin.toUser(), tableId, handNumber));
  }
}
