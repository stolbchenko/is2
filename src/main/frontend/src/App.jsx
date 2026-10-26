import { useState } from 'react';
import { getToken, getUsername, setSession } from './api.js';
import Login from './components/Login.jsx';
import PersonTable from './components/PersonTable.jsx';
import Stats from './components/Stats.jsx';
import ImportHistory from './components/ImportHistory.jsx';
import ChangeJournal from './components/ChangeJournal.jsx';

const SCREEN_KEY = 'is1_screen';

export default function App() {
  const [authed, setAuthed] = useState(Boolean(getToken()));
  const [user, setUser] = useState(getUsername());
  const [screen, setScreen] = useState(
    () => sessionStorage.getItem(SCREEN_KEY) || 'table'
  );

  const openScreen = (next) => {
    setScreen(next);
    sessionStorage.setItem(SCREEN_KEY, next);
  };

  if (!authed) {
    return (
      <Login
        onSuccess={(name) => {
          setUser(name);
          setAuthed(true);
        }}
      />
    );
  }

  const logout = () => {
    setSession(null, null);
    sessionStorage.removeItem(SCREEN_KEY);
    setAuthed(false);
    setUser(null);
  };

  return (
    <div className="app">
      <nav>
        <button
          className={screen === 'table' ? 'active' : ''}
          onClick={() => openScreen('table')}
        >
          Список объектов
        </button>
        <button
          className={screen === 'stats' ? 'active' : ''}
          onClick={() => openScreen('stats')}
        >
          Специальные операции
        </button>
        <button
          className={screen === 'imports' ? 'active' : ''}
          onClick={() => openScreen('imports')}
        >
          История импорта
        </button>
        <button
          className={screen === 'changes' ? 'active' : ''}
          onClick={() => openScreen('changes')}
        >
          Журнал изменений
        </button>
        <span className="spacer" />
        <span className="current-user">Вы вошли как <strong>{user}</strong></span>
        <button onClick={logout}>Выйти</button>
      </nav>

      {screen === 'table' && <PersonTable />}
      {screen === 'stats' && <Stats />}
      {screen === 'imports' && <ImportHistory />}
      {screen === 'changes' && <ChangeJournal />}
    </div>
  );
}
