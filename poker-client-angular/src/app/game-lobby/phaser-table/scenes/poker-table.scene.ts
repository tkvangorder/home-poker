import Phaser from 'phaser';
import { Subscription } from 'rxjs';
import { PhaserBridgeService } from '../phaser-bridge.service';
import { TableState, PlayerState } from '../../../game/game-state.service';
import { PotResult } from '../../../game/game-events';
import { TableFelt } from '../game-objects/table-felt';
import { SeatDisplay } from '../game-objects/seat-display';
import { CommunityCards } from '../game-objects/community-cards';
import { PotDisplay } from '../game-objects/pot-display';
import { DealerButton } from '../game-objects/dealer-button';
import { BetChip } from '../game-objects/bet-chip';
import { WinnerBanner } from '../game-objects/winner-banner';
import { buildShowdownStages, formatStageLine1, formatStageLine2, ShowdownStage } from '../utils/showdown-sequence';
import { matchWinningCards } from '../utils/card-match';
import { computeBadgeState, badgeTextColor } from '../utils/action-badge-state';
import { getAllSeatPositions, MAX_SEATS, SeatPosition } from '../utils/seat-layout';

const ACTION_OVERLAY_MS = 2000;
const SHOWDOWN_STAGE_MS = 4000;

export class PokerTableScene extends Phaser.Scene {
  private bridge!: PhaserBridgeService;
  private tableFelt!: TableFelt;
  private seats: SeatDisplay[] = [];
  private betChips: BetChip[] = [];
  private communityCards!: CommunityCards;
  private potDisplay!: PotDisplay;
  private winnerBanner!: WinnerBanner;
  private dealerButton!: DealerButton;

  private subscriptions: Subscription[] = [];
  private currentTableState: TableState | null = null;
  private currentPlayers: PlayerState[] = [];
  private currentUserId = '';

  // Layout cache
  private cx = 0;
  private cy = 0;
  private tableRx = 0;
  private tableRy = 0;
  private seatPositions: SeatPosition[] = [];
  private dealerButtonRadius = 10;

  // Timer tracking
  private timerDeadlineMs: number | null = null;
  private timerTotalMs = 30000;

  // Per-seat action overlay (transient name-replacement for fold/check/call/etc).
  private actionOverlays: ({ text: string; color: string } | null)[] = [];
  private actionOverlayTimers: (Phaser.Time.TimerEvent | null)[] = [];
  private lastSeenActionSeq: number[] = [];

  // Showdown presentation: stages cycle one winning hand at a time.
  private showdownStages: ShowdownStage[] = [];
  private showdownStageIndex = 0;
  private showdownTimer: Phaser.Time.TimerEvent | null = null;
  /** Identity guard: the potResults object currently being presented, or null. */
  private presentedPotResults: PotResult[] | null = null;

  constructor() {
    super({ key: 'PokerTableScene' });
  }

  init(data: { bridge: PhaserBridgeService }): void {
    this.bridge = data.bridge;
  }

  create(): void {
    this.tableFelt = new TableFelt(this);

    for (let i = 0; i < MAX_SEATS; i++) {
      const seat = new SeatDisplay(this, 0, 0);
      seat.setDepth(5);
      this.seats.push(seat);
      const chip = new BetChip(this, 0, 0);
      chip.setDepth(4);
      this.betChips.push(chip);
      this.actionOverlays.push(null);
      this.actionOverlayTimers.push(null);
      this.lastSeenActionSeq.push(-1);
    }

    this.communityCards = new CommunityCards(this, 0, 0).setDepth(2);
    this.potDisplay = new PotDisplay(this, 0, 0).setDepth(2);
    this.winnerBanner = new WinnerBanner(this, 0, 0).setDepth(10);
    this.dealerButton = new DealerButton(this);
    // Above seat pods (depth 5) and bet chips (depth 4) — the dealer marker is
    // always drawn on top so it can sit beside the bet pod without being occluded.
    this.dealerButton.setDepth(7);

    this.layoutAll(this.scale.width, this.scale.height);

    this.scale.on('resize', (gameSize: Phaser.Structs.Size) => {
      this.layoutAll(gameSize.width, gameSize.height);
      this.renderState();
    });

    this.subscriptions.push(
      this.bridge.tableState$.subscribe((state) => {
        this.handleTableStateChange(state);
      }),
      this.bridge.players$.subscribe((players) => {
        this.currentPlayers = players;
        this.renderState();
      }),
      this.bridge.currentUserId$.subscribe((userId) => {
        this.currentUserId = userId;
        this.renderState();
      })
    );
  }

  override update(): void {
    if (this.timerDeadlineMs == null) return;
    const remainingMs = Math.max(0, this.timerDeadlineMs - Date.now());
    const frac = this.timerTotalMs > 0 ? remainingMs / this.timerTotalMs : 0;
    const pos = this.currentTableState?.actionPosition;
    if (pos != null && pos >= 1 && pos <= MAX_SEATS) {
      this.seats[pos - 1].setTimer(frac);
    }
  }

  private handleTableStateChange(state: TableState | null): void {
    this.currentTableState = state;

    if (state?.actionDeadline) {
      const deadlineMs = Date.parse(state.actionDeadline);
      if (deadlineMs > Date.now()) {
        const justObserved = deadlineMs - Date.now();
        if (this.timerDeadlineMs !== deadlineMs) {
          this.timerTotalMs = Math.max(1000, justObserved);
        }
        this.timerDeadlineMs = deadlineMs;
      } else {
        this.timerDeadlineMs = null;
      }
    } else {
      this.timerDeadlineMs = null;
    }

    this.updateShowdownPresentation(state);
    this.renderState();
  }

  /**
   * Start the showdown sequence when potResults first appears, and abort it the
   * moment they clear (which happens on the next hand-started). Uses object
   * identity so unrelated state updates within the same showdown don't re-trigger.
   */
  private updateShowdownPresentation(state: TableState | null): void {
    const potResults = state?.potResults ?? null;
    if (potResults && potResults !== this.presentedPotResults) {
      this.presentedPotResults = potResults;
      this.startShowdown(potResults);
    } else if (!potResults && this.presentedPotResults) {
      this.presentedPotResults = null;
      this.abortShowdown();
    }
  }

  private layoutAll(width: number, height: number): void {
    this.cx = width / 2;
    this.cy = height / 2;
    this.tableRx = width * 0.30;
    this.tableRy = this.tableRx / 2.4;

    const railWidth = Math.max(6, width * 0.01);
    this.tableFelt.draw(this.cx, this.cy, this.tableRx, this.tableRy, railWidth);

    const seatRx = this.tableRx * 1.22;
    const seatRy = this.tableRy * 1.35;
    this.seatPositions = getAllSeatPositions(this.cx, this.cy, seatRx, seatRy);

    const seatSizing = {
      podPadX: Math.max(6, width * 0.007),
      podPadY: Math.max(4, width * 0.004),
      podRadius: Math.max(8, width * 0.009),
      avatarSize: Math.max(28, width * 0.026),
      avatarRingWidth: 2,
      nameSize: Math.max(10, Math.round(width * 0.008)),
      stackSize: Math.max(9, Math.round(width * 0.007)),
      holeWidth: Math.max(32, width * 0.031),
      emptySize: Math.max(20, width * 0.02),
      timerHeight: Math.max(2, Math.round(width * 0.002)),
    };

    const betFontSize = Math.max(9, Math.round(width * 0.008));

    for (let i = 0; i < MAX_SEATS; i++) {
      this.seats[i].setPosition(this.seatPositions[i].x, this.seatPositions[i].y);
      this.seats[i].applySizing(seatSizing);
      const dx = this.cx - this.seatPositions[i].x;
      const dy = this.cy - this.seatPositions[i].y;
      const t = 0.25;
      this.betChips[i].setPosition(
        this.seatPositions[i].x + dx * t,
        this.seatPositions[i].y + dy * t
      );
      this.betChips[i].resize(betFontSize);
    }

    const communityCardWidth = Math.max(36, width * 0.051);
    this.communityCards.setPosition(this.cx, this.cy - communityCardWidth * 0.15);
    this.communityCards.resize(communityCardWidth);

    this.potDisplay.setPosition(this.cx, this.cy + communityCardWidth * 0.85);
    const chipSize = Math.max(10, width * 0.013);
    const labelSize = Math.max(8, Math.round(width * 0.007));
    const amountSize = Math.max(13, Math.round(width * 0.011));
    this.potDisplay.resize(chipSize, labelSize, amountSize);

    this.dealerButtonRadius = Math.max(8, width * 0.008);
    this.dealerButton.resize(this.dealerButtonRadius);

    this.winnerBanner.setPosition(this.cx, this.cy - this.tableRy * 0.62);
    const bannerLine1Size = Math.max(16, Math.round(width * 0.014));
    const bannerLine2Size = Math.max(12, Math.round(width * 0.01));
    this.winnerBanner.resize(bannerLine1Size, bannerLine2Size);
  }

  private renderState(): void {
    const ts = this.currentTableState;
    const players = this.currentPlayers;

    if (!ts) {
      this.clearAllActionOverlays();
      this.abortShowdown();
      for (const seat of this.seats) seat.updateSeat(null, null, null, false, false);
      for (const chip of this.betChips) chip.setAmount(0);
      this.communityCards.updateCards([]);
      this.potDisplay.updatePots([]);
      this.dealerButton.hide();
      return;
    }

    const seatPlayerMap = new Map<number, PlayerState>();
    for (const p of players) {
      if (p.seatPosition != null) seatPlayerMap.set(p.seatPosition, p);
    }

    for (let pos = 1; pos <= MAX_SEATS; pos++) {
      const idx = pos - 1;
      const player = seatPlayerMap.get(pos);
      const rawCards = ts.seatCards.get(pos) ?? null;
      const isActive = ts.actionPosition === pos;
      const summary = ts.seatSummaries.get(pos);
      const isFolded = summary?.status === 'FOLDED';

      this.betChips[idx].setAmount(summary?.currentBetAmount ?? 0);

      // Drive the transient action name-swap overlay from the pure mapping.
      const badgeState = computeBadgeState(pos, ts);

      // For transient action kinds (fold/check/call/bet/raise/all-in),
      // temporarily replace the seat's name with the action message.
      if (
        player &&
        badgeState.kind !== 'none' &&
        this.lastSeenActionSeq[idx] !== badgeState.actionSeq
      ) {
        this.lastSeenActionSeq[idx] = badgeState.actionSeq;
        this.actionOverlays[idx] = {
          text: badgeState.line1,
          color: badgeTextColor(badgeState.kind),
        };
        if (this.actionOverlayTimers[idx]) {
          this.actionOverlayTimers[idx]!.remove(false);
        }
        this.actionOverlayTimers[idx] = this.time.delayedCall(ACTION_OVERLAY_MS, () => {
          this.actionOverlays[idx] = null;
          this.actionOverlayTimers[idx] = null;
          this.renderState();
        });
      }

      if (player) {
        const isLocalUser = player.userId === this.currentUserId;
        const name = isLocalUser ? 'You' : player.displayName;
        // SeatCard.showCard is the post-hand reveal flag; the local user
        // always sees their own hole cards face up regardless.
        const cards = rawCards && isLocalUser
          ? rawCards.map((c) => ({ ...c, showCard: true }))
          : rawCards;
        this.seats[idx].updateSeat(
          name, player.chipCount, cards, isActive, isFolded, this.actionOverlays[idx],
        );
      } else {
        this.seats[idx].updateSeat(null, null, null, false, false);
      }
    }

    this.communityCards.updateCards(ts.communityCards);
    this.potDisplay.updatePots(ts.pots);

    if (ts.dealerPosition != null && ts.dealerPosition >= 1 && ts.dealerPosition <= MAX_SEATS) {
      const dealerIdx = ts.dealerPosition - 1;
      const seatPos = this.seatPositions[dealerIdx];
      const dx = this.cx - seatPos.x;
      const dy = this.cy - seatPos.y;
      const dist = Math.hypot(dx, dy);
      if (dist > 0) {
        // Anchor on the bet line (same fraction the bet chip uses), then push
        // further toward the table center — clear of the bet pod — so the button
        // always lands on the felt and never slides over the player's pod.
        const t = 0.25;
        const ax = seatPos.x + dx * t;
        const ay = seatPos.y + dy * t;
        const inwardX = dx / dist;
        const inwardY = dy / dist;
        const clearance =
          this.betChips[dealerIdx].getHalfWidth() + this.dealerButtonRadius + 4;
        this.dealerButton.show(ax + inwardX * clearance, ay + inwardY * clearance);
      }
    } else {
      this.dealerButton.hide();
    }
  }

  private startShowdown(potResults: PotResult[]): void {
    this.abortShowdown();
    this.showdownStages = buildShowdownStages(potResults);
    this.showdownStageIndex = 0;
    if (this.showdownStages.length === 0) return;
    this.presentStage(0);
    this.scheduleNextStage();
  }

  private scheduleNextStage(): void {
    this.showdownTimer = this.time.delayedCall(SHOWDOWN_STAGE_MS, () => {
      this.showdownStageIndex++;
      if (this.showdownStageIndex < this.showdownStages.length) {
        this.presentStage(this.showdownStageIndex);
        this.scheduleNextStage();
      } else {
        this.fadeOutShowdown();
      }
    });
  }

  private presentStage(index: number): void {
    const ts = this.currentTableState;
    if (!ts) return;
    const stage = this.showdownStages[index];
    if (!stage) return;

    const name = this.displayNameForSeat(stage.userId);
    this.winnerBanner.show(formatStageLine1(stage, name), formatStageLine2(stage));

    // Reset any prior stage's highlights before applying this one.
    this.clearShowdownHighlights();

    if (stage.winningCards.length === 0) {
      // Fold-win: no cards to highlight — banner + seat glow only.
      if (stage.seatPosition >= 1 && stage.seatPosition <= MAX_SEATS) {
        this.seats[stage.seatPosition - 1].setWinnerHighlight(true);
      }
      return;
    }

    const targets = matchWinningCards(
      stage.winningCards,
      ts.communityCards,
      ts.seatCards.get(stage.seatPosition) ?? null,
    );

    this.communityCards.applyShowdownHighlight(targets.communityIndices);
    for (let pos = 1; pos <= MAX_SEATS; pos++) {
      const seat = this.seats[pos - 1];
      if (pos === stage.seatPosition) {
        seat.applyShowdownHighlight(targets.holeSeatCardIndices);
        seat.setWinnerHighlight(true);
      } else {
        // Dim other seats' (revealed) hole cards for contrast.
        seat.applyShowdownHighlight([]);
      }
    }
  }

  private fadeOutShowdown(): void {
    this.showdownTimer = null;
    this.winnerBanner.fadeOut();
    this.clearShowdownHighlights();
  }

  private abortShowdown(): void {
    if (this.showdownTimer) {
      this.showdownTimer.remove(false);
      this.showdownTimer = null;
    }
    this.showdownStages = [];
    this.showdownStageIndex = 0;
    this.winnerBanner.hide();
    this.clearShowdownHighlights();
  }

  private clearShowdownHighlights(): void {
    this.communityCards.applyShowdownHighlight(null);
    for (const seat of this.seats) {
      seat.applyShowdownHighlight(null);
      seat.setWinnerHighlight(false);
    }
  }

  private displayNameForSeat(userId: string): string {
    if (userId === this.currentUserId) return 'You';
    const player = this.currentPlayers.find((p) => p.userId === userId);
    return player?.displayName ?? userId;
  }

  shutdown(): void {
    this.abortShowdown();
    this.presentedPotResults = null;
    this.clearAllActionOverlays();
    for (const sub of this.subscriptions) sub.unsubscribe();
    this.subscriptions = [];
  }

  private clearAllActionOverlays(): void {
    for (let i = 0; i < this.actionOverlayTimers.length; i++) {
      if (this.actionOverlayTimers[i]) {
        this.actionOverlayTimers[i]!.remove(false);
        this.actionOverlayTimers[i] = null;
      }
      this.actionOverlays[i] = null;
      this.lastSeenActionSeq[i] = -1;
    }
  }
}
