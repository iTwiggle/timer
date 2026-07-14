import { describe, it, expect } from 'vitest';
import { generateRandomTimestamps, upcomingAlerts, formatClock } from './timer.js';

describe('generateRandomTimestamps', () => {
  it('generates the requested number of alerts within the window', () => {
    const start = 1_000_000;
    const windowMs = 60_000;
    const result = generateRandomTimestamps({ count: 5, windowMs, startMs: start });

    expect(result).toHaveLength(5);
    for (const ts of result) {
      expect(ts).toBeGreaterThanOrEqual(start);
      expect(ts).toBeLessThan(start + windowMs);
    }
  });

  it('returns timestamps sorted ascending', () => {
    const result = generateRandomTimestamps({ count: 20, windowMs: 100_000, startMs: 0 });
    const sorted = [...result].sort((a, b) => a - b);
    expect(result).toEqual(sorted);
  });

  it('returns an empty array when count is 0', () => {
    expect(generateRandomTimestamps({ count: 0, windowMs: 1000 })).toEqual([]);
  });

  it('respects a minimum gap between alerts', () => {
    const result = generateRandomTimestamps({
      count: 4,
      windowMs: 100_000,
      startMs: 0,
      minGapMs: 10_000,
    });
    for (let i = 1; i < result.length; i += 1) {
      expect(result[i] - result[i - 1]).toBeGreaterThanOrEqual(10_000);
    }
  });

  it('is deterministic when supplied a seeded RNG', () => {
    const makeRng = () => {
      let seed = 42;
      return () => {
        seed = (seed * 9301 + 49297) % 233280;
        return seed / 233280;
      };
    };
    const a = generateRandomTimestamps({ count: 6, windowMs: 50_000, startMs: 0, rng: makeRng() });
    const b = generateRandomTimestamps({ count: 6, windowMs: 50_000, startMs: 0, rng: makeRng() });
    expect(a).toEqual(b);
  });

  it('throws on invalid input', () => {
    expect(() => generateRandomTimestamps({ count: -1, windowMs: 1000 })).toThrow(RangeError);
    expect(() => generateRandomTimestamps({ count: 1, windowMs: 0 })).toThrow(RangeError);
    expect(() =>
      generateRandomTimestamps({ count: 5, windowMs: 1000, minGapMs: 1000 }),
    ).toThrow(RangeError);
  });
});

describe('upcomingAlerts', () => {
  it('keeps only future timestamps', () => {
    const now = 5000;
    const schedule = [1000, 4000, 6000, 9000];
    expect(upcomingAlerts(schedule, now)).toEqual([6000, 9000]);
  });
});

describe('formatClock', () => {
  it('formats a timestamp as HH:MM:SS', () => {
    const d = new Date(2020, 0, 1, 9, 5, 3);
    expect(formatClock(d.getTime())).toBe('09:05:03');
  });
});
