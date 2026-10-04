// Rotating day cycle (e.g. a 6-day schedule): a year-calendar photo says which cycle day each date is
// (and which are no-school days), a schedule photo says which classes run on each cycle day.
// Both are kept in localStorage and combined into calendar events (source 'cycle') by CycleStore.save().
(() => {
  let { days: cycleDays, classes: cycleClasses } = CycleStore.load();

  function rebuild() {
    CycleStore.save(cycleDays, cycleClasses);
    events = loadEvents();
    render();
  }

  const el = id => document.getElementById(id);
  const make = (tag, props = {}) => Object.assign(document.createElement(tag), props);
  const calendarInput = el('cycle-calendar-photo');
  const scheduleInput = el('cycle-schedule-photo');

  function dayCount() {
    const seen = [...Object.values(cycleDays).map(d => d.day), ...cycleClasses.map(c => c.day)].filter(Boolean);
    return Math.max(6, ...seen);
  }
  function daySelect(value, allowNone) {
    const select = make('select');
    if (allowNone) select.append(new Option('No school', ''));
    for (let d = 1; d <= dayCount(); d++) select.append(new Option('Day ' + d, d, false, d === value));
    if (allowNone && value == null) select.value = '';
    return select;
  }

  function showMenu() {
    review.hidden = false;
    reviewTitle.textContent = 'Rotating day schedule';
    importStatus.className = '';
    const days = Object.keys(cycleDays).length;
    importStatus.textContent = `Step 1: photograph the year calendar that shows which day (Day 1, Day 2, …) each date is, `
      + `plus PD days and holidays. Step 2: photograph the day schedule. Order doesn't matter. `
      + `Saved so far: ${days} dates, ${cycleClasses.length} classes.`;
    const menu = make('div', { className: 'menu' });
    const calBtn = make('button', { textContent: '1. Year calendar photo' });
    calBtn.onclick = () => calendarInput.click();
    const schedBtn = make('button', { textContent: '2. Day schedule photo' });
    schedBtn.onclick = () => scheduleInput.click();
    const clearBtn = make('button', { className: 'secondary', textContent: 'Remove saved day schedule' });
    clearBtn.onclick = () => {
      if (!confirm('Remove all dates and classes from the day schedule?')) return;
      cycleDays = {};
      cycleClasses = [];
      rebuild();
      showMenu();
    };
    menu.append(calBtn, schedBtn, clearBtn);
    reviewRows.replaceChildren(menu);
    reviewAdd.hidden = true;
  }

  async function readPhoto(input, url, what) {
    const file = input.files[0];
    input.value = '';
    if (!file) return null;
    reviewTitle.textContent = `Reading your ${what}…`;
    importStatus.className = '';
    importStatus.textContent = 'This can take a few seconds.';
    reviewRows.replaceChildren();
    reviewAdd.hidden = true;
    try {
      const { base64, mimeType } = await photoToBase64(file);
      const res = await fetch(url, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ image: base64, mimeType, today: todayIso() }),
      });
      const body = await res.json();
      if (!res.ok) throw new Error(body.error || 'Import failed');
      return body;
    } catch (e) {
      reviewTitle.textContent = 'Import failed';
      importStatus.className = 'error';
      importStatus.textContent = e.message;
      return null;
    }
  }

  function empty(what) {
    reviewTitle.textContent = 'Nothing found';
    importStatus.textContent = `Couldn't read any ${what}. Try a clearer, well-lit photo (a month or a term at a time works best).`;
  }

  calendarInput.addEventListener('change', async () => {
    const body = await readPhoto(calendarInput, '/api/import-cycle-calendar', 'calendar photo');
    if (!body) return;
    if (!body.days.length) return empty('dates');
    reviewTitle.textContent = `Found ${body.days.length} dates — check and edit`;
    importStatus.textContent = 'Fix any wrong day numbers or dates; untick anything that is wrong.';
    const rows = body.days.map(item => {
      const row = make('div', { className: 'row' });
      const check = make('input', { type: 'checkbox', checked: true });
      const date = make('input', { type: 'date', value: item.date });
      const day = daySelect(item.day, true);
      const note = make('input', { type: 'text', value: item.note || '', placeholder: 'Note (e.g. PD Day)' });
      row.append(check, date, day, note);
      row.get = () => check.checked && date.value
        ? { date: date.value, day: day.value ? Number(day.value) : null, note: note.value.trim() } : null;
      return row;
    });
    reviewRows.replaceChildren(...rows);
    reviewAdd.hidden = false;
    reviewAdd.textContent = 'Save dates';
    reviewAdd.onclick = () => {
      rows.map(r => r.get()).filter(Boolean).forEach(it => { cycleDays[it.date] = { day: it.day, note: it.note }; });
      rebuild();
      showMenu();
    };
  });

  scheduleInput.addEventListener('change', async () => {
    const body = await readPhoto(scheduleInput, '/api/import-cycle-schedule', 'schedule photo');
    if (!body) return;
    if (!body.classes.length) return empty('classes');
    reviewTitle.textContent = `Found ${body.classes.length} classes — check and edit`;
    importStatus.textContent = 'Classes replace what was saved for the days found in this photo.';
    const rows = body.classes.map(item => {
      const row = make('div', { className: 'row' });
      const check = make('input', { type: 'checkbox', checked: true });
      const day = daySelect(item.day, false);
      const time = make('input', { type: 'time', value: item.time || '' });
      const title = make('input', { type: 'text', value: item.title });
      row.append(check, day, time, title);
      row.get = () => check.checked && title.value.trim()
        ? { day: Number(day.value), title: title.value.trim(), time: time.value, period: item.period } : null;
      return row;
    });
    reviewRows.replaceChildren(...rows);
    reviewAdd.hidden = false;
    reviewAdd.textContent = 'Save classes';
    reviewAdd.onclick = () => {
      const added = rows.map(r => r.get()).filter(Boolean);
      cycleClasses = CycleStore.mergeClasses(cycleClasses, added, body.classes.map(c => c.day));
      rebuild();
      showMenu();
    };
  });

  el('cycle').addEventListener('click', showMenu);
})();
