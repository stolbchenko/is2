import { useState } from 'react';
import { api } from '../api.js';
import { normalize, validateDecimal } from '../decimal.js';

const COLORS = ['BLACK', 'BLUE', 'YELLOW', 'ORANGE'];

export default function Stats() {
  const [results, setResults] = useState({});
  const [heightThreshold, setHeightThreshold] = useState('');
  const [thresholdError, setThresholdError] = useState(null);
  const [hairColor, setHairColor] = useState('BLACK');
  const [eyeColor, setEyeColor] = useState('BLACK');
  const [error, setError] = useState(null);

  const run = async (key, fn) => {
    setError(null);
    try {
      const value = await fn();
      setResults((prev) => ({ ...prev, [key]: value }));
    } catch (e) {
      setError(e.message);
    }
  };

  const runShorterThan = () => {
    const message = validateDecimal(heightThreshold, 'Пороговое значение');
    setThresholdError(message);
    if (message) {
      return;
    }
    run('shorter', () => api.shorterThan(normalize(heightThreshold)));
  };

  return (
    <div>
      <h2>Специальные операции</h2>
      {error && <div className="banner">{error}</div>}

      <div className="card">
        <strong>Сумма значений поля height для всех объектов</strong>
        <div className="actions" style={{ justifyContent: 'flex-start' }}>
          <button onClick={() => run('sum', api.heightSum)}>Рассчитать</button>
        </div>
        {results.sum && <p>Сумма: {results.sum.sum}</p>}
      </div>

      <div className="card">
        <strong>Объект с минимальным height</strong>
        <div className="actions" style={{ justifyContent: 'flex-start' }}>
          <button onClick={() => run('min', api.minHeight)}>Найти</button>
        </div>
        {results.min && (
          <p>
            #{results.min.id} — {results.min.name}, рост {results.min.height}
            {' '}(владелец: {results.min.owner})
          </p>
        )}
      </div>

      <div className="card">
        <strong>Объекты с height меньше заданного</strong>
        <div className="toolbar">
          <input
            inputMode="decimal"
            placeholder="Пороговое значение"
            value={heightThreshold}
            onChange={(e) => { setHeightThreshold(e.target.value); setThresholdError(null); }}
          />
          <button onClick={runShorterThan}>Найти</button>
        </div>
        {thresholdError && <div className="error">{thresholdError}</div>}
        {results.shorter && (
          results.shorter.length === 0
            ? <p>Подходящих объектов нет</p>
            : <ul>
                {results.shorter.map((p) => (
                  <li key={p.id}>
                    #{p.id} — {p.name}, рост {p.height} (владелец: {p.owner})
                  </li>
                ))}
              </ul>
        )}
      </div>

      <div className="card">
        <strong>Количество людей с заданным цветом волос</strong>
        <div className="toolbar">
          <select value={hairColor} onChange={(e) => setHairColor(e.target.value)}>
            {COLORS.map((c) => <option key={c} value={c}>{c}</option>)}
          </select>
          <button onClick={() => run('hair', () => api.hairCount(hairColor))}>Подсчитать</button>
        </div>
        {results.hair && <p>Цвет {results.hair.color}: {results.hair.count} чел.</p>}
      </div>

      <div className="card">
        <strong>Доля людей с заданным цветом глаз (в процентах)</strong>
        <div className="toolbar">
          <select value={eyeColor} onChange={(e) => setEyeColor(e.target.value)}>
            {COLORS.map((c) => <option key={c} value={c}>{c}</option>)}
          </select>
          <button onClick={() => run('eye', () => api.eyeShare(eyeColor))}>Рассчитать</button>
        </div>
        {results.eye && <p>Цвет {results.eye.color}: {results.eye.percentage}%</p>}
      </div>
    </div>
  );
}
