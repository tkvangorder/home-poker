import { PotResult } from '../../../game/game-events';
import { Card } from '../../../poker/poker-models';

/** One winning hand to present on the table. A split pot produces one stage per winner. */
export interface ShowdownStage {
  /** Server pot index (0 = main pot). */
  potIndex: number;
  /** '' when the hand had a single pot; otherwise 'Main Pot' / 'Side Pot N'. */
  potLabel: string;
  seatPosition: number;
  userId: string;
  /** Chips awarded to this winner from this pot (cents). */
  amount: number;
  handDescription: string;
  /** True when this pot had more than one winner. */
  isSplit: boolean;
  /** The 5 cards of the best hand; empty for a fold-win (no showdown). */
  winningCards: Card[];
}

const MAX_DESC_CHARS = 40;

function formatCents(cents: number): string {
  return '$' + (cents / 100).toFixed(2);
}

/**
 * Build the ordered list of stages from a showdown's pot results.
 * Order: side pots first (ascending by amount), then the main pot (potIndex 0) last.
 * Each winner of a pot becomes its own stage so only one hand is shown at a time.
 */
export function buildShowdownStages(potResults: PotResult[]): ShowdownStage[] {
  const multiPot = potResults.length > 1;
  const main = potResults.filter((p) => p.potIndex === 0);
  const side = potResults
    .filter((p) => p.potIndex !== 0)
    .sort((a, b) => a.potAmount - b.potAmount);
  const ordered = [...side, ...main];

  const stages: ShowdownStage[] = [];
  for (const pot of ordered) {
    const isSplit = pot.winners.length > 1;
    const potLabel = multiPot
      ? pot.potIndex === 0
        ? 'Main Pot'
        : `Side Pot ${pot.potIndex}`
      : '';
    for (const winner of pot.winners) {
      stages.push({
        potIndex: pot.potIndex,
        potLabel,
        seatPosition: winner.seatPosition,
        userId: winner.userId,
        amount: winner.amount,
        handDescription: winner.handDescription,
        isSplit,
        winningCards: winner.winningCards ?? [],
      });
    }
  }
  return stages;
}

/** Banner line 1: "<label> — <name> wins $X.XX (split)". */
export function formatStageLine1(stage: ShowdownStage, displayName: string): string {
  let s = `${displayName} wins ${formatCents(stage.amount)}`;
  if (stage.isSplit) s += ' (split)';
  return stage.potLabel ? `${stage.potLabel} — ${s}` : s;
}

/** Banner line 2: the hand description, truncated. Empty for fold-wins. */
export function formatStageLine2(stage: ShowdownStage): string {
  const d = stage.handDescription ?? '';
  if (d.length <= MAX_DESC_CHARS) return d;
  return d.substring(0, MAX_DESC_CHARS - 1) + '…';
}
