const BASE = '/is1/api';

let token = sessionStorage.getItem('is1_token') || null;
let username = sessionStorage.getItem('is1_username') || null;

export function setSession(newToken, newUsername) {
  token = newToken;
  username = newUsername;
  if (newToken) {
    sessionStorage.setItem('is1_token', newToken);
    sessionStorage.setItem('is1_username', newUsername);
  } else {
    sessionStorage.removeItem('is1_token');
    sessionStorage.removeItem('is1_username');
  }
}

export function getToken() {
  return token;
}

export function getUsername() {
  return username;
}

async function request(path, options = {}) {
  // Для FormData Content-Type с boundary выставляет сам браузер
  const headers = options.body instanceof FormData
    ? { ...options.headers }
    : { 'Content-Type': 'application/json', ...options.headers };
  if (token) {
    headers.Authorization = `Bearer ${token}`;
  }

  const response = await fetch(`${BASE}${path}`, { ...options, headers });

  if (response.status === 204) {
    return null;
  }

  const body = await response.json().catch(() => ({}));

  if (!response.ok) {
    throw Object.assign(new Error(body.message || 'Ошибка запроса'), {
      body,
      status: response.status,
    });
  }

  return body;
}

export const api = {
  login: (user, password) =>
    request('/auth/login', { method: 'POST', body: JSON.stringify({ username: user, password }) }),
  register: (user, password) =>
    request('/auth/register', { method: 'POST', body: JSON.stringify({ username: user, password }) }),

  listPersons: (params) => request(`/persons?${new URLSearchParams(params)}`),
  getPerson: (id) => request(`/persons/${id}`),
  createPerson: (payload) =>
    request('/persons', { method: 'POST', body: JSON.stringify(payload) }),
  updatePerson: (id, payload) =>
    request(`/persons/${id}`, { method: 'PUT', body: JSON.stringify(payload) }),
  deletePerson: (id, params = {}) =>
    request(`/persons/${id}?${new URLSearchParams(params)}`, { method: 'DELETE' }),

  importPersons: (file) => {
    const form = new FormData();
    form.append('file', file);
    return request('/persons/import', { method: 'POST', body: form });
  },

  changeHistory: (params) => request(`/changes?${new URLSearchParams(params)}`),
  rollbackPerson: (id, changeId, to) =>
    request(`/persons/${id}/rollback`, { method: 'POST', body: JSON.stringify({ changeId, to }) }),

  importHistory: (params) => request(`/imports?${new URLSearchParams(params)}`),

  listCoordinates: () => request('/persons/coordinates'),
  listLocations: () => request('/persons/locations'),

  heightSum: () => request('/stats/height-sum'),
  minHeight: () => request('/stats/min-height'),
  shorterThan: (height) =>
    request(`/stats/shorter-than?height=${encodeURIComponent(height)}`),
  hairCount: (color) => request(`/stats/hair-count/${color}`),
  eyeShare: (color) => request(`/stats/eye-share/${color}`),
};

const RECONNECT_MAX_DELAY_MS = 10000;

/**
 * Подписка на изменения от сервера. При обрыве связи (перезапуск сервера, сбой сети, прокси)
 * переподключается с нарастающей задержкой; после восстановления вызывает onReconnect, чтобы экран
 * перечитал данные: события, пришедшие во время обрыва, потеряны. Возвращает объект с close().
 */
export function connectUpdates(onChange, onReconnect = () => {}) {
  const protocol = window.location.protocol === 'https:' ? 'wss' : 'ws';
  const url = `${protocol}://${window.location.host}/is1/ws/updates`;
  let socket = null;
  let timer = null;
  let closed = false;
  let failures = 0;
  let connectedBefore = false;

  const open = () => {
    socket = new WebSocket(url);
    socket.onopen = () => {
      if (connectedBefore || failures > 0) {
        onReconnect();
      }
      connectedBefore = true;
      failures = 0;
    };
    socket.onmessage = (event) => {
      try {
        onChange(JSON.parse(event.data));
      } catch {
        // некорректное сообщение не должно ломать подписку
      }
    };
    socket.onclose = () => {
      if (closed) {
        return;
      }
      failures += 1;
      timer = setTimeout(open, Math.min(1000 * 2 ** (failures - 1), RECONNECT_MAX_DELAY_MS));
    };
  };
  open();

  return {
    close() {
      closed = true;
      clearTimeout(timer);
      socket?.close();
    },
  };
}
