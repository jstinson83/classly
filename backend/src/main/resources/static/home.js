// Reads the events the calendar page stores in localStorage (see app.js).
const pad = n => String(n).padStart(2, '0');
const now = new Date();
const today = `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}`;

let events = [];
try { events = JSON.parse(localStorage.getItem('classly.events')) || []; } catch { /* no events */ }

document.getElementById('today-label').textContent =
  now.toLocaleDateString(undefined, { weekday: 'long', month: 'long', day: 'numeric' });

const todays = events.filter(e => e.date === today)
  .sort((a, b) => (a.time || '24:00').localeCompare(b.time || '24:00'));

function fill(id, items, emptyText, checkable = false) {
  const ul = document.getElementById(id);
  if (!items.length) {
    ul.innerHTML = '';
    const li = document.createElement('li');
    li.className = 'empty';
    li.textContent = emptyText;
    ul.append(li);
    return;
  }
  ul.replaceChildren(...items.map(e => {
    const li = document.createElement('li');
    const time = Object.assign(document.createElement('span'), { className: 'time', textContent: e.time });
    const title = Object.assign(document.createElement('span'), { textContent: e.title });
    if (checkable) {
      const box = Object.assign(document.createElement('input'), { type: 'checkbox', checked: !!e.done });
      box.setAttribute('aria-label', 'Done: ' + e.title);
      li.classList.toggle('done', !!e.done);
      box.addEventListener('change', () => {
        e.done = box.checked;
        li.classList.toggle('done', e.done);
        localStorage.setItem('classly.events', JSON.stringify(events));
      });
      li.append(box);
    }
    li.append(time, title);
    return li;
  }));
}

fill('classes', todays.filter(e => e.kind === 'class'), 'No classes today.');
fill('todos', todays.filter(e => e.kind !== 'class'), 'Nothing to do today.', true);
