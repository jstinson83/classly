// Schedule page: photo of the rotating-day schedule -> review -> digital version (see cyclelib.js).
const el = id => document.getElementById(id);
const make = (tag, props = {}) => Object.assign(document.createElement(tag), props);
const photoInput = el('photo');
const status = el('status');
let { days: cycleDays, classes: cycleClasses } = CycleStore.load();

// Downscale to keep the upload small; returns { base64, mimeType }.
async function photoToBase64(file) {
  const bitmap = await createImageBitmap(file);
  const scale = Math.min(1, 1800 / Math.max(bitmap.width, bitmap.height));
  const canvas = document.createElement('canvas');
  canvas.width = Math.round(bitmap.width * scale);
  canvas.height = Math.round(bitmap.height * scale);
  canvas.getContext('2d').drawImage(bitmap, 0, 0, canvas.width, canvas.height);
  return { base64: canvas.toDataURL('image/jpeg', 0.85).split(',')[1], mimeType: 'image/jpeg' };
}

function renderSchedule() {
  el('start').hidden = cycleClasses.length > 0;
  el('schedule').hidden = cycleClasses.length === 0;
  const byDay = new Map();
  cycleClasses.forEach(c => byDay.set(c.day, [...(byDay.get(c.day) || []), c]));
  el('days').replaceChildren(...[...byDay.keys()].sort((a, b) => a - b).map(day => {
    const card = make('div', { className: 'day' });
    card.append(make('h2', { textContent: 'Day ' + day }));
    const list = make('ul');
    byDay.get(day).forEach(c => {
      const li = make('li');
      if (c.time) li.append(make('span', { className: 'time', textContent: c.time }));
      li.append(c.title);
      list.append(li);
    });
    card.append(list);
    return card;
  }));
}

function showReview(items) {
  el('review').hidden = false;
  el('review-title').textContent = `Found ${items.length} classes — check and edit`;
  const dayCount = Math.max(6, ...items.map(c => c.day));
  const rows = items.map(item => {
    const row = make('div', { className: 'row' });
    const check = make('input', { type: 'checkbox', checked: true });
    const day = make('select');
    for (let d = 1; d <= dayCount; d++) day.append(new Option('Day ' + d, d, false, d === item.day));
    const time = make('input', { type: 'time', value: item.time || '' });
    const title = make('input', { type: 'text', value: item.title });
    row.append(check, day, time, title);
    row.get = () => check.checked && title.value.trim()
      ? { day: Number(day.value), title: title.value.trim(), time: time.value, period: item.period } : null;
    return row;
  });
  el('review-rows').replaceChildren(...rows);
  el('review-save').onclick = () => {
    const added = rows.map(r => r.get()).filter(Boolean);
    cycleClasses = CycleStore.mergeClasses(cycleClasses, added, items.map(c => c.day));
    CycleStore.save(cycleDays, cycleClasses);
    el('review').hidden = true;
    status.textContent = '';
    renderSchedule();
  };
}

photoInput.addEventListener('change', async () => {
  const file = photoInput.files[0];
  photoInput.value = '';
  if (!file) return;
  status.className = '';
  status.textContent = 'Reading your schedule… this can take a few seconds.';
  try {
    const { base64, mimeType } = await photoToBase64(file);
    const res = await fetch('/api/import-cycle-schedule', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ image: base64, mimeType, today: new Date().toISOString().slice(0, 10) }),
    });
    const body = await res.json();
    if (!res.ok) throw new Error(body.error || 'Import failed');
    if (!body.classes.length) throw new Error("Couldn't read any classes. Try a clearer, well-lit photo of the whole schedule.");
    status.textContent = '';
    showReview(body.classes);
  } catch (e) {
    status.className = 'error';
    status.textContent = e.message;
  }
});

el('take').addEventListener('click', () => photoInput.click());
el('again').addEventListener('click', () => photoInput.click());
el('review-cancel').addEventListener('click', () => { el('review').hidden = true; });
renderSchedule();
