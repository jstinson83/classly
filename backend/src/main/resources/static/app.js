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
