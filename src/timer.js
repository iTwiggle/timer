// Core randomizer logic. Kept framework-agnostic so it can be ported to
// a native mobile app later (see README roadmap note).

/**
 * Generate `count` random timestamps (in ms since epoch) uniformly distributed
 * within a window that starts at `startMs` and lasts `windowMs`.
 *
 * The returned timestamps are sorted ascending and de-duplicated. When a
 * `minGapMs` is provided, alerts are spaced at least that far apart so two
 * alerts never bunch up on top of each other.
 *
 * @param {Object} options
 * @param {number} options.count       Number of alerts to schedule (>= 0).
 * @param {number} options.windowMs    Length of the window in milliseconds (> 0).
 * @param {number} [options.startMs]   Window start (defaults to now).
 * @param {number} [options.minGapMs]  Minimum spacing between alerts (default 0).
 * @param {() => number} [options.rng] RNG returning [0, 1) (default Math.random).
 * @returns {number[]} Sorted array of absolute timestamps in ms.
 */
export function generateRandomTimestamps({
  count,
  windowMs,
  startMs = Date.now(),
  minGapMs = 0,
  rng = Math.random,
} = {}) {
  if (!Number.isFinite(count) || count < 0) {
    throw new RangeError('count must be a non-negative number');
  }
  if (!Number.isFinite(windowMs) || windowMs <= 0) {
    throw new RangeError('windowMs must be a positive number');
  }
  if (minGapMs < 0) {
    throw new RangeError('minGapMs must be non-negative');
  }
  if (count > 0 && minGapMs * (count - 1) > windowMs) {
    throw new RangeError('window is too small to fit count alerts with minGapMs spacing');
  }

  const offsets = new Set();
  // Cap attempts to avoid an infinite loop if the caller asks for a near-impossible layout.
  const maxAttempts = count * 50 + 100;
  let attempts = 0;

  while (offsets.size < count && attempts < maxAttempts) {
    attempts += 1;
    const candidate = Math.floor(rng() * windowMs);
    const ok = [...offsets].every((existing) => Math.abs(existing - candidate) >= minGapMs);
    if (ok) {
      offsets.add(candidate);
    }
  }

  return [...offsets]
    .sort((a, b) => a - b)
    .map((offset) => startMs + offset);
}

/**
 * Given a schedule of absolute timestamps, return the ones that are still in
 * the future relative to `nowMs`.
 *
 * @param {number[]} schedule Absolute timestamps in ms.
 * @param {number} [nowMs]    Reference "now" (defaults to Date.now()).
 * @returns {number[]} Upcoming timestamps, sorted ascending.
 */
export function upcomingAlerts(schedule, nowMs = Date.now()) {
  return schedule.filter((ts) => ts > nowMs).sort((a, b) => a - b);
}

/**
 * Format a millisecond timestamp as a local HH:MM:SS string.
 *
 * @param {number} ts Timestamp in ms.
 * @returns {string}
 */
export function formatClock(ts) {
  const d = new Date(ts);
  const pad = (n) => String(n).padStart(2, '0');
  return `${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`;
}
