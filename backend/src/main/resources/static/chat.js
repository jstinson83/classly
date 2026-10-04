// AI chat planner: talks to /api/chat, and writes the study/homework blocks it plans to the calendar
// (events in localStorage, source 'chat'). The conversation is kept in localStorage too.
const CHAT_KEY = 'classly.chat';
const EVENTS_KEY = 'classly.events';
const PLAN_DAYS = 45;

const el = id => document.getElementById(id);
const make = (tag, props = {}) => Object.assign(document.createElement(tag), props);
const pad = n => String(n).padStart(2, '0');
const iso = d => `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
const read = (key, fallback) => { try { return JSON.parse(localStorage.getItem(key)) ?? fallback; } catch { return fallback; } };

// chat = [{ role: 'user' | 'assistant' | 'error', text, added?: [{ title, date, time }], batch?: string }]
let chat = read(CHAT_KEY, []);
const log = el('log');
const input = el('input');
const send = el('send');

const saveChat = () => localStorage.setItem(CHAT_KEY, JSON.stringify(chat));
const loadEvents = () => read(EVENTS_KEY, []);
const dateLabel = date => {
  const [y, m, d] = date.split('-').map(Number);
  return new Date(y, m - 1, d).toLocaleDateString(undefined, { weekday: 'short', month: 'short', day: 'numeric' });
};

function render() {
  if (!chat.length) {
    log.replaceChildren(make('p', { className: 'hint',
      textContent: 'Tell me about your homework, tests or projects and when they are due. I\'ll help you plan when to do them and put the sessions on your calendar.' }));
    return;
  }
  log.replaceChildren(...chat.flatMap(m => {
    const out = [make('div', { className: 'msg ' + m.role, textContent: m.text })];
    if (m.added?.length) {
      const box = make('div', { className: 'added' });
      box.append(make('strong', { textContent: m.undone ? 'Removed from calendar' : 'Added to calendar' }));
      const list = make('ul');
      m.added.forEach(e => list.append(make('li', { textContent: `${dateLabel(e.date)}${e.time ? ' ' + e.time : ''} — ${e.title}` })));
      box.append(list);
      if (!m.undone) {
        const undo = make('button', { className: 'secondary', textContent: 'Undo' });
        undo.addEventListener('click', () => {
          localStorage.setItem(EVENTS_KEY, JSON.stringify(loadEvents().filter(e => e.batch !== m.batch)));
          m.undone = true;
          saveChat();
          render();
        });
        box.append(undo);
      }
      out.push(box);
    }
    return out;
  }));
  log.scrollTop = log.scrollHeight;
}

// Upcoming non-class items, so the AI can plan around them (classes are left out: school hours are assumed).
function upcomingCalendar() {
  const from = iso(new Date());
  const to = iso(new Date(Date.now() + PLAN_DAYS * 864e5));
  return loadEvents().filter(e => e.date >= from && e.date <= to && e.kind !== 'class')
    .map(e => ({ date: e.date, time: e.time || '', title: e.title }));
}

async function sendMessage(text) {
  chat.push({ role: 'user', text });
  saveChat();
  render();
  send.disabled = true;
  const thinking = make('div', { className: 'msg assistant', textContent: 'Thinking…' });
  log.append(thinking);
  log.scrollTop = log.scrollHeight;
  try {
    const res = await fetch('/api/chat', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        messages: chat.filter(m => m.role !== 'error').map(m => ({ role: m.role, text: m.text })),
        today: iso(new Date()),
        calendar: upcomingCalendar(),
      }),
    });
    const body = await res.json();
    if (!res.ok) throw new Error(body.error || 'Chat failed');
    const reply = { role: 'assistant', text: body.reply };
    if (body.events.length) {
      reply.batch = crypto.randomUUID();
      reply.added = body.events;
      localStorage.setItem(EVENTS_KEY, JSON.stringify(loadEvents().concat(
        body.events.map(e => ({ id: crypto.randomUUID(), date: e.date, time: e.time, title: e.title, source: 'chat', batch: reply.batch })))));
    }
    chat.push(reply);
  } catch (e) {
    chat.push({ role: 'error', text: e.message + ' — your message was kept, try sending again.' });
  } finally {
    send.disabled = false;
    saveChat();
    render();
  }
}

el('form').addEventListener('submit', ev => {
  ev.preventDefault();
  const text = input.value.trim();
  if (!text || send.disabled) return;
  input.value = '';
  sendMessage(text);
});
input.addEventListener('keydown', ev => {
  if (ev.key === 'Enter' && !ev.shiftKey) { ev.preventDefault(); el('form').requestSubmit(); }
});
el('new').addEventListener('click', () => {
  if (chat.length && !confirm('Start a new chat? Sessions already added to the calendar stay there.')) return;
  chat = [];
  saveChat();
  render();
});
render();
