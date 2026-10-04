// Notes page: free-form notes kept in localStorage (key classly.notes), autosaved while typing.
// A note is { id, title, body, date: 'YYYY-MM-DD' | '', updated: ms since epoch }.
const NOTES_KEY = 'classly.notes';
const el = id => document.getElementById(id);
const make = (tag, props = {}) => Object.assign(document.createElement(tag), props);
const pad = n => String(n).padStart(2, '0');
const iso = d => `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;

let notes = [];
try { notes = JSON.parse(localStorage.getItem(NOTES_KEY)) || []; } catch { /* start empty */ }
let current = null; // the note open in the editor

const save = () => localStorage.setItem(NOTES_KEY, JSON.stringify(notes));
const dateLabel = date => {
  const [y, m, d] = date.split('-').map(Number);
  return new Date(y, m - 1, d).toLocaleDateString(undefined, { weekday: 'short', month: 'short', day: 'numeric' });
};

function renderList() {
  const q = el('search').value.trim().toLowerCase();
  const shown = notes.filter(n => !q || (n.title + ' ' + n.body).toLowerCase().includes(q))
    .sort((a, b) => b.updated - a.updated);
  if (!shown.length) {
    el('list').replaceChildren(make('p', { className: 'empty',
      textContent: notes.length ? 'No notes match your search.' : 'No notes yet. Tap "New note" to start.' }));
    return;
  }
  el('list').replaceChildren(...shown.map(n => {
    const card = make('div', { className: 'card' });
    card.append(make('h2', { textContent: n.title || 'Untitled' }));
    card.append(make('div', { className: 'meta',
      textContent: n.date ? dateLabel(n.date) : new Date(n.updated).toLocaleDateString() }));
    if (n.body) card.append(make('p', { textContent: n.body.split('\n')[0] }));
    card.addEventListener('click', () => openNote(n));
    return card;
  }));
}

function openNote(note) {
  current = note;
  el('title').value = note.title;
  el('date').value = note.date;
  el('body').value = note.body;
  el('saved').textContent = '';
  el('list-view').hidden = true;
  el('editor-view').hidden = false;
  (note.title || note.body ? el('body') : el('title')).focus();
}

function closeNote() {
  // Don't keep notes that were created but left completely empty.
  if (current && !current.title.trim() && !current.body.trim()) {
    notes = notes.filter(n => n !== current);
    save();
  }
  current = null;
  el('editor-view').hidden = true;
  el('list-view').hidden = false;
  renderList();
}

function onEdit() {
  current.title = el('title').value;
  current.date = el('date').value;
  current.body = el('body').value;
  current.updated = Date.now();
  save();
  el('saved').textContent = 'Saved';
}

['title', 'date', 'body'].forEach(id => el(id).addEventListener('input', onEdit));
el('new').addEventListener('click', () => {
  const note = { id: crypto.randomUUID(), title: '', body: '', date: iso(new Date()), updated: Date.now() };
  notes.push(note);
  save();
  openNote(note);
});
el('back').addEventListener('click', closeNote);
el('delete').addEventListener('click', () => {
  if (!confirm('Delete this note?')) return;
  notes = notes.filter(n => n !== current);
  save();
  current = null;
  closeNote();
});
el('search').addEventListener('input', renderList);
renderList();
