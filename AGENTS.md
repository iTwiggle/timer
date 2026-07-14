# AGENTS.md

## Project

Randomizer Timer — a lightweight vanilla-JS web app that fires custom audio
alerts (Web Audio API) plus browser notifications at random times inside a
configurable window. Core scheduling logic lives in `src/timer.js` and is kept
framework-agnostic so it can be ported to native mobile later.

- Entry HTML: `index.html`
- App wiring / DOM + audio + notifications: `src/main.js`
- Pure scheduling logic (unit-tested): `src/timer.js`
- Tests: `src/timer.test.js` (Vitest, jsdom)
- Tooling: Vite (dev server + build), ESLint 9 flat config, Vitest

## Commands

Standard scripts are defined in `package.json`:

- Dev server: `npm run dev` (Vite, serves on port 5173, `host: true`)
- Build: `npm run build` (output in `dist/`)
- Preview built output: `npm run preview`
- Lint: `npm run lint`
- Tests: `npm run test` (single run) / `npm run test:watch`

## Cursor Cloud specific instructions

- The update script runs `npm install`; no extra setup is required.
- The dev server (`npm run dev`) is a long-running process — start it in a
  background/tmux session, not a blocking foreground call. It listens on
  http://localhost:5173/.
- Audio and browser notifications only work in a real browser (they no-op or
  throw in Node/jsdom), so the alert-firing behavior can only be validated via
  GUI testing, not unit tests. Unit tests cover only the pure logic in
  `src/timer.js`.
- Vite HMR does a full page reload on edits to plain JS modules, but an
  already-open tab can keep running a stale bundle if the websocket didn't
  reconnect. When manually verifying a code change in the browser, force a hard
  reload (Ctrl+Shift+R) rather than trusting auto-refresh.
- Demo timing: the alert-firing flow plays out over the whole window (min 1
  minute via the UI). Use 1 minute for the shortest realistic end-to-end demo;
  alerts fire at random moments within it, so expect to watch for up to ~75s.
