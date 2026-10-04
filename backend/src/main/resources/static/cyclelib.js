// Shared storage for the rotating day cycle (used by the calendar and schedule pages).
// Dates and classes live in localStorage; save() also regenerates the calendar events (source 'cycle').
const CycleStore = (() => {
  const DAYS_KEY = 'classly.cycleDays';       // { 'YYYY-MM-DD': { day: number | null, note: string } }
  const CLASSES_KEY = 'classly.cycleClasses'; // [{ day, title, time, period }]
  const EVENTS_KEY = 'classly.events';
  const load = (key, fallback) => {
    try { return JSON.parse(localStorage.getItem(key)) ?? fallback; } catch { return fallback; }
  };

  function save(days, classes) {
    localStorage.setItem(DAYS_KEY, JSON.stringify(days));
    localStorage.setItem(CLASSES_KEY, JSON.stringify(classes));
    const fresh = [];
    for (const [date, info] of Object.entries(days).sort(([a], [b]) => a.localeCompare(b))) {
      if (info.day == null) {
        fresh.push({ date, time: '', title: info.note || 'No school', kind: 'info' });
        continue;
      }
      fresh.push({ date, time: '', title: `Day ${info.day}`, kind: 'info' });
      classes.filter(c => c.day === info.day)
        .forEach(c => fresh.push({ date, time: c.time || '', title: c.title, kind: 'class' }));
    }
    const events = load(EVENTS_KEY, []).filter(e => e.source !== 'cycle')
      .concat(fresh.map(e => ({ id: crypto.randomUUID(), source: 'cycle', ...e })));
    localStorage.setItem(EVENTS_KEY, JSON.stringify(events));
  }

  // Merge newly read classes in, replacing the old ones for the cycle days they cover.
  function mergeClasses(existing, added, coveredDays) {
    const covered = new Set(coveredDays);
    return existing.filter(c => !covered.has(c.day)).concat(added)
      .sort((a, b) => a.day - b.day || a.period - b.period);
  }

  return { load: () => ({ days: load(DAYS_KEY, {}), classes: load(CLASSES_KEY, []) }), save, mergeClasses };
})();
