const STORAGE_KEY = 'classly.events';
const DOW = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'];

const pad = n => String(n).padStart(2, '0');
const iso = d => `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
const todayIso = () => iso(new Date());

// Events are { id, date: 'YYYY-MM-DD', time: 'HH:MM' | '', title, kind?: 'class' }.
// Items from a weekly timetable get kind 'class'; everything else is a to-do.
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

// ---- Photo import: schedule/agenda photo -> review -> calendar ----
const WEEKS_FOR_WEEKLY_ITEMS = 16;
const cameraBtn = document.getElementById('camera');
const photoInput = document.getElementById('photo');
const review = document.getElementById('review');
const reviewTitle = document.getElementById('review-title');
const reviewRows = document.getElementById('review-rows');
const reviewAdd = document.getElementById('review-add');
const importStatus = document.getElementById('import-status');

cameraBtn.addEventListener('click', () => photoInput.click());
document.getElementById('review-cancel').addEventListener('click', () => { review.hidden = true; });

// Downscale to keep the upload small; returns { base64, mimeType }.
async function photoToBase64(file) {
  const bitmap = await createImageBitmap(file);
  const scale = Math.min(1, 1800 / Math.max(bitmap.width, bitmap.height));
  const canvas = document.createElement('canvas');
  canvas.width = Math.round(bitmap.width * scale);
  canvas.height = Math.round(bitmap.height * scale);
  canvas.getContext('2d').drawImage(bitmap, 0, 0, canvas.width, canvas.height);
  const dataUrl = canvas.toDataURL('image/jpeg', 0.85);
  return { base64: dataUrl.split(',')[1], mimeType: 'image/jpeg' };
}

photoInput.addEventListener('change', async () => {
  const file = photoInput.files[0];
  photoInput.value = '';
  if (!file) return;
  review.hidden = false;
  reviewTitle.textContent = 'Reading your photo…';
  importStatus.className = '';
  importStatus.textContent = 'This can take a few seconds.';
  reviewRows.replaceChildren();
  reviewAdd.hidden = true;
  try {
    const { base64, mimeType } = await photoToBase64(file);
    const res = await fetch('/api/import-photo', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ image: base64, mimeType, today: todayIso() }),
    });
    const body = await res.json();
    if (!res.ok) throw new Error(body.error || 'Import failed');
    showReview(body.events);
  } catch (e) {
    reviewTitle.textContent = 'Import failed';
    importStatus.className = 'error';
    importStatus.textContent = e.message;
  }
});

function showReview(items) {
  if (!items.length) {
    reviewTitle.textContent = 'Nothing found';
    importStatus.textContent = 'Try a clearer, well-lit photo of the whole page.';
    return;
  }
  reviewTitle.textContent = `Found ${items.length} item${items.length === 1 ? '' : 's'} — check and edit`;
  importStatus.textContent = 'Weekly classes are added for the next ' + WEEKS_FOR_WEEKLY_ITEMS + ' weeks.';
  const rows = items.map(item => {
    const row = document.createElement('div');
    row.className = 'row';
    const check = Object.assign(document.createElement('input'), { type: 'checkbox', checked: true });
    const title = Object.assign(document.createElement('input'), { type: 'text', value: item.title });
    row.append(check, title);
    let dateInput = null, weekdaySelect = null;
    if (item.date) {
      dateInput = Object.assign(document.createElement('input'), { type: 'date', value: item.date });
      row.append(dateInput);
    } else {
      weekdaySelect = document.createElement('select');
      DOW.forEach((d, i) => weekdaySelect.append(new Option('Every ' + d, i, false, i === item.weekday)));
      row.append(weekdaySelect);
    }
    const time = Object.assign(document.createElement('input'), { type: 'time', value: item.time || '' });
    row.append(time);
    row.get = () => check.checked && title.value.trim() ? {
      title: title.value.trim(), time: time.value,
      date: dateInput ? dateInput.value : null,
      weekday: weekdaySelect ? Number(weekdaySelect.value) : null,
    } : null;
    return row;
  });
  reviewRows.replaceChildren(...rows);
  reviewAdd.hidden = false;
  reviewAdd.onclick = () => {
    const added = [];
    rows.map(r => r.get()).filter(Boolean).forEach(it => {
      if (it.date) {
        added.push({ date: it.date, time: it.time, title: it.title });
      } else {
        const d = new Date();
        d.setDate(d.getDate() + ((it.weekday - d.getDay() + 7) % 7));
        for (let w = 0; w < WEEKS_FOR_WEEKLY_ITEMS; w++) {
          added.push({ date: iso(d), time: it.time, title: it.title, kind: 'class' });
          d.setDate(d.getDate() + 7);
        }
      }
    });
    added.forEach(e => events.push({ id: crypto.randomUUID(), ...e }));
    saveEvents(events);
    review.hidden = true;
    render();
  };
}

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
