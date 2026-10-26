import { useState } from 'react';
import { api, setSession } from '../api.js';

export default function Login({ onSuccess }) {
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [mode, setMode] = useState('login');
  const [error, setError] = useState(null);
  const [busy, setBusy] = useState(false);

  const submit = async (event) => {
    event.preventDefault();
    setError(null);
    setBusy(true);
    try {
      const result = mode === 'login'
        ? await api.login(username, password)
        : await api.register(username, password);
      setSession(result.token, result.username);
      onSuccess(result.username);
    } catch (e) {
      setError(e.message);
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="app">
      <div className="login-wrap card">
        <h2>{mode === 'login' ? 'Вход в систему' : 'Регистрация'}</h2>
        {error && <div className="banner">{error}</div>}
        <form onSubmit={submit}>
          <div className="field">
            <label htmlFor="username">Имя пользователя</label>
            <input
              id="username"
              value={username}
              onChange={(e) => setUsername(e.target.value)}
              required
            />
          </div>
          <div className="field">
            <label htmlFor="password">Пароль</label>
            <input
              id="password"
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              required
            />
          </div>
          <div className="actions">
            <button
              type="button"
              onClick={() => { setMode(mode === 'login' ? 'register' : 'login'); setError(null); }}
            >
              {mode === 'login' ? 'Регистрация' : 'У меня есть аккаунт'}
            </button>
            <button type="submit" className="primary" disabled={busy}>
              {mode === 'login' ? 'Войти' : 'Зарегистрироваться'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
