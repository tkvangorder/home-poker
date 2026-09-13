export interface SeatPosition {
  x: number;
  y: number;
}

export const MAX_SEATS = 9;

// Seats are distributed evenly around the ellipse starting at 90° (bottom center,
// where the current user sits) and stepping 40° clockwise.
const START_DEG = 90;
const STEP_DEG = 360 / MAX_SEATS;

export function getSeatPosition(
  index: number,
  cx: number,
  cy: number,
  rx: number,
  ry: number
): SeatPosition {
  const angleDeg = START_DEG + (index % MAX_SEATS) * STEP_DEG;
  const angleRad = (angleDeg * Math.PI) / 180;
  return {
    x: cx + rx * Math.cos(angleRad),
    y: cy + ry * Math.sin(angleRad),
  };
}

export function getAllSeatPositions(
  cx: number,
  cy: number,
  rx: number,
  ry: number
): SeatPosition[] {
  return Array.from({ length: MAX_SEATS }, (_, i) =>
    getSeatPosition(i, cx, cy, rx, ry)
  );
}
