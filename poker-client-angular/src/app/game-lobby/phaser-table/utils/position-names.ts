// 9-max position names, clockwise from dealer:
// BTN, SB, BB, UTG, UTG+1, MP, LJ, HJ, CO
const POSITION_NAMES_9 = ['BTN', 'SB', 'BB', 'UTG', 'UTG+1', 'MP', 'LJ', 'HJ', 'CO'];

/**
 * Seat positions and dealer position are 1-indexed (range 1..totalSeats).
 */
export function getPositionName(
  seatPosition: number,
  dealerPosition: number,
  totalSeats: number
): string {
  if (dealerPosition < 1) return `Seat ${seatPosition}`;
  const offset = (seatPosition - dealerPosition + totalSeats) % totalSeats;
  return POSITION_NAMES_9[offset] ?? `Seat ${seatPosition}`;
}
