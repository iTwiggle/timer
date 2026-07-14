import './styles.css';
import { generateRandomTimestamps, formatClock } from './timer.js';

// --- Audio -----------------------------------------------------------------
// Uses the Web Audio API to synthesize a short alert chime with no asset files.
let audioCtx = null;

function getAudioContext() {
  if (!audioCtx) {
    const Ctx = window.AudioContext || window.webkitAudioContext;
    audioCtx = new Ctx();
  }
  return audioCtx;
}

export function playAlertSound() {
  const ctx = getAudioContext();
  if (ctx.state === 'suspended') ctx.resume();

  const now = ctx.currentTime;
  [880, 1174.66].forEach((freq, i) => {
    const osc = ctx.createOscillator();
    const gain = ctx.createGain();
    osc.type = 'sine';
    osc.frequency.value = freq;
    const start = now + i * 0.18;
    gain.gain.setValueAtTime(0.0001, start);
    gain.gain.exponentialRampToValueAtTime(0.4, start + 0.02);
    gain.gain.exponentialRampToValueAtTime(0.0001, start + 0.35);
    osc.connect(gain).connect(ctx.destination);
    osc.start(start);
    osc.stop(start + 0.4);
  });
}

// --- Notifications ---------------------------------------------------------
async function ensureNotificationPermission() {
  if (!('Notification' in window)) return 'unsupported';
  if (Notification.permission === 'default') {
    try {
      return await Notification.requestPermission();
    } catch {
      return Notification.permission;
    }
  }
  return Notification.permission;
}

function fireNotification() {
  if ('Notification' in window && Notification.permission === 'granted') {
    new Notification('⏰ Randomizer Timer', {
      body: 'Drop and give it 20! 💪',
    });
  }
}

// --- App state -------------------------------------------------------------
const state = {
  schedule: [],
  fired: new Set(),
  timers: [],
  armed: false,
};

function els() {
  return {
    count: document.getElementById('count'),
    windowMin: document.getElementById('windowMin'),
    start: document.getElementById('startBtn'),
    stop: document.getElementById('stopBtn'),
    test: document.getElementById('testBtn'),
    status: document.getElementById('status'),
    statusText: document.getElementById('statusText'),
    schedule: document.getElementById('schedule'),
    scheduleTitle: document.getElementById('scheduleTitle'),
  };
}

function clearTimers() {
  state.timers.forEach((t) => clearTimeout(t));
  state.timers = [];
}

function renderSchedule() {
  const { schedule } = els();
  schedule.innerHTML = '';
  state.schedule.forEach((ts, idx) => {
    const li = document.createElement('li');
    li.textContent = formatClock(ts);
    if (state.fired.has(idx)) li.classList.add('done');
    schedule.appendChild(li);
  });
}

function setStatus(mode, text) {
  const { status, statusText } = els();
  status.classList.remove('armed', 'firing');
  if (mode) status.classList.add(mode);
  statusText.textContent = text;
}

function nextPendingTimestamp() {
  // Alerts fire in schedule order, so the next one is the first unfired index.
  for (let i = 0; i < state.schedule.length; i += 1) {
    if (!state.fired.has(i)) return state.schedule[i];
  }
  return null;
}

function triggerAlert(idx) {
  state.fired.add(idx);
  playAlertSound();
  fireNotification();
  setStatus('firing', `🔔 Alert! (${state.fired.size}/${state.schedule.length})`);
  renderSchedule();

  // Completion is based on how many alerts have actually fired, not on
  // wall-clock time — otherwise closely-spaced alerts could end the session
  // (and cancel pending timers) before they get a chance to fire.
  if (state.fired.size >= state.schedule.length) {
    setTimeout(() => stop('All alerts fired for this window. 🎉'), 1200);
  } else {
    setTimeout(() => {
      const next = nextPendingTimestamp();
      if (state.armed && next != null) setStatus('armed', `Armed — next at ${formatClock(next)}`);
    }, 1200);
  }
}

async function start() {
  const { count, windowMin } = els();
  const n = Number(count.value);
  const windowMs = Number(windowMin.value) * 60_000;

  // Validate before prompting so bad input fails fast.
  try {
    generateRandomTimestamps({ count: n, windowMs, startMs: 0 });
  } catch (err) {
    setStatus(null, `⚠️ ${err.message}`);
    return;
  }

  await ensureNotificationPermission();

  // Anchor the schedule to *after* the permission prompt so the full window is
  // still ahead of us and alerts don't bunch up at the start.
  const schedule = generateRandomTimestamps({ count: n, windowMs });
  state.schedule = schedule;
  state.fired = new Set();
  state.armed = true;
  clearTimers();

  const now = Date.now();
  schedule.forEach((ts, idx) => {
    const delay = Math.max(0, ts - now);
    state.timers.push(setTimeout(() => triggerAlert(idx), delay));
  });

  renderSchedule();
  els().scheduleTitle.style.display = schedule.length ? 'block' : 'none';
  toggleControls(true);
  const next = schedule[0];
  setStatus('armed', next ? `Armed — next at ${formatClock(next)}` : 'Armed — no alerts scheduled');
}

function stop(message = 'Stopped.') {
  clearTimers();
  state.armed = false;
  toggleControls(false);
  setStatus(null, message);
}

function toggleControls(running) {
  const { start: startBtn, stop: stopBtn, count, windowMin } = els();
  startBtn.disabled = running;
  stopBtn.disabled = !running;
  count.disabled = running;
  windowMin.disabled = running;
}

function init() {
  const { start: startBtn, stop: stopBtn, test } = els();
  startBtn.addEventListener('click', start);
  stopBtn.addEventListener('click', () => stop());
  test.addEventListener('click', async () => {
    await ensureNotificationPermission();
    playAlertSound();
    fireNotification();
    setStatus('firing', '🔔 Test alert fired!');
    setTimeout(() => {
      if (!state.armed) setStatus(null, 'Idle — set your parameters and hit Start.');
    }, 1200);
  });
  setStatus(null, 'Idle — set your parameters and hit Start.');
  toggleControls(false);
}

if (typeof document !== 'undefined') {
  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }
}
