const STORAGE_KEY = 'classly.events';
const DOW = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'];

const pad = n => String(n).padStart(2, '0');
const iso = d => `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
const todayIso = () => iso(new Date());

// Events are { id, date: 'YYYY-MM-DD', time: 'HH:MM' | '', title }.
// Stored in the browser for now; server-side storage is still undecided.
function loadEvents() {
  try { return JSON.parse(localStorage.getItem(STORAGE_KEY)) || []; } catch { return []; }
}
function saveEvents(events) {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(events));
}
let events = loadEvents();

const now = new Date();
let viewYear = now.getFullYear();
let viewMonth = now.getMonth();
let selected = todayIso();

const grid = document.getElementById('grid');
const monthLabel = document.getElementById('month-label');
const dayHeading = document.getElementById('day-heading');
const dayList = document.getElementById('day-events');

function eventsOn(date) {
  return events.filter(e => e.date === date)
    .sort((a, b) => (a.time || '24:00').localeCompare(b.time || '24:00'));
}

function renderGrid() {
  monthLabel.textContent = new Date(viewYear, viewMonth, 1)
    .toLocaleDateString(undefined, { month: 'long', year: 'numeric' });
  grid.replaceChildren(...DOW.map(d => Object.assign(document.createElement('div'), { className: 'dow', textContent: d })));
  const start = new Date(viewYear, viewMonth, 1 - new Date(viewYear, viewMonth, 1).getDay());
  const weeks = Math.ceil((new Date(viewYear, viewMonth, 1).getDay() + new Date(viewYear, viewMonth + 1, 0).getDate()) / 7);
  for (let i = 0; i < weeks * 7; i++) {
    const d = new Date(start.getFullYear(), start.getMonth(), start.getDate() + i);
    const date = iso(d);
    const cell = document.createElement('div');
    cell.className = 'day' + (d.getMonth() !== viewMonth ? ' other' : '')
      + (date === todayIso() ? ' today' : '') + (date === selected ? ' selected' : '');
    const num = document.createElement('span');
    num.className = 'num';
    num.textContent = d.getDate();
    cell.append(num);
    const evs = eventsOn(date);
    evs.slice(0, 3).forEach(e => {
      const chip = document.createElement('span');
      chip.className = 'chip';
      chip.textContent = (e.time ? e.time + ' ' : '') + e.title;
      cell.append(chip);
    });
    if (evs.length > 3) {
      const more = document.createElement('span');
      more.className = 'more';
      more.textContent = `+${evs.length - 3} more`;
      cell.append(more);
    }
    cell.addEventListener('click', () => {
      selected = date;
      if (d.getMonth() !== viewMonth) { viewYear = d.getFullYear(); viewMonth = d.getMonth(); }
      render();
    });
    grid.append(cell);
  }
}

function renderDay() {
  const [y, m, d] = selected.split('-').map(Number);
  dayHeading.textContent = new Date(y, m - 1, d)
    .toLocaleDateString(undefined, { weekday: 'long', month: 'long', day: 'numeric' });
  const evs = eventsOn(selected);
  if (!evs.length) {
    const li = document.createElement('li');
    li.className = 'empty';
    li.textContent = 'Nothing scheduled.';
    dayList.replaceChildren(li);
    return;
  }
  dayList.replaceChildren(...evs.map(e => {
    const li = document.createElement('li');
    const time = Object.assign(document.createElement('span'), { className: 'time', textContent: e.time || 'All day' });
    const title = Object.assign(document.createElement('span'), { className: 'title', textContent: e.title });
    const del = Object.assign(document.createElement('button'), { className: 'secondary', textContent: 'Delete' });
    del.addEventListener('click', () => {
      events = events.filter(x => x.id !== e.id);
      saveEvents(events);
      render();
    });
    li.append(time, title, del);
    return li;
  }));
}

function render() { renderGrid(); renderDay(); }

function shiftMonth(delta) {
  const d = new Date(viewYear, viewMonth + delta, 1);
  viewYear = d.getFullYear();
  viewMonth = d.getMonth();
  renderGrid();
}
document.getElementById('prev').addEventListener('click', () => shiftMonth(-1));
document.getElementById('next').addEventListener('click', () => shiftMonth(1));
document.getElementById('today').addEventListener('click', () => {
  const t = new Date();
  viewYear = t.getFullYear();
  viewMonth = t.getMonth();
  selected = todayIso();
  render();
});

document.getElementById('add-form').addEventListener('submit', ev => {
  ev.preventDefault();
  const form = ev.target;
  const title = form.title.value.trim();
  if (!title) return;
  events.push({ id: crypto.randomUUID(), date: selected, time: form.time.value, title });
  saveEvents(events);
  form.reset();
  render();
});

render();

// Gemini smoke test (deploy check).
const button = document.getElementById('hello');
const result = document.getElementById('result');
button.addEventListener('click', async () => {
  button.disabled = true;
  result.className = '';
  result.textContent = 'Asking Gemini…';
  try {
    const res = await fetch('/api/hello-gemini', { method: 'POST' });
    const body = await res.json();
    if (!res.ok) throw new Error(body.error || 'Request failed');
    result.textContent = body.reply;
  } catch (e) {
    result.className = 'error';
    result.textContent = e.message;
  } finally {
    button.disabled = false;
  }
});
