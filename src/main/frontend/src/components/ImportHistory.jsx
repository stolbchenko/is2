import { useCallback, useEffect, useState } from 'react';
import { api, connectUpdates } from '../api.js';

const STATUS_LABELS = {
  SUCCESS: 'Успешно',
  FAILED: 'Ошибка',
};

function ErrorDetails({ message }) {
  if (!message) {
    return '—';
  }
  const [summary, ...details] = message.split('\n');
  if (details.length === 0) {
    return summary;
  }
  return (
    <details>
      <summary>{summary}</summary>
      <ul className="import-errors">
        {details.map((line, i) => <li key={i}>{line}</li>)}
      </ul>
    </details>
  );
}

export default function ImportHistory() {
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(10);
  const [data, setData] = useState(null);
  const [error, setError] = useState(null);

  const load = useCallback(async () => {
    try {
      setData(await api.importHistory({ page, size }));
      setError(null);
    } catch (e) {
      setError(e.message);
    }
  }, [page, size]);

  useEffect(() => {
    load();
  }, [load]);

  useEffect(() => {
    const socket = connectUpdates((event) => {
      if (event.action === 'IMPORTED' || event.action === 'IMPORT_FAILED') {
        load();
      }
    }, load);
    return () => socket.close();
  }, [load]);

  const totalPages = data ? Math.max(Math.ceil(data.total / size), 1) : 1;

  return (
    <div>
      <h2>История импорта</h2>
      {data && (
        <p className="hint">
          {data.admin
            ? 'Вы администратор: показаны операции всех пользователей.'
            : 'Показаны только операции, запущенные вами.'}
        </p>
      )}

      <div className="toolbar">
        <select value={size} onChange={(e) => { setSize(Number(e.target.value)); setPage(0); }}>
          {[5, 10, 20, 50].map((n) => <option key={n} value={n}>{n} на странице</option>)}
        </select>
        <button onClick={load}>Обновить</button>
      </div>

      {error && <div className="banner">{error}</div>}

      <div className="table-wrap">
        <table>
          <thead>
            <tr>
              <th>ID операции</th>
              <th>Статус</th>
              <th>Пользователь</th>
              <th>Добавлено объектов</th>
              <th>Время запуска</th>
              <th>Файл</th>
              <th>Причина ошибки</th>
            </tr>
          </thead>
          <tbody>
            {data && data.items.length === 0 && (
              <tr><td colSpan={7}>Операций импорта ещё не было</td></tr>
            )}
            {data?.items.map((op) => (
              <tr key={op.id}>
                <td>{op.id}</td>
                <td>
                  <span className={`status status-${op.status.toLowerCase()}`}>
                    {STATUS_LABELS[op.status] ?? op.status}
                  </span>
                </td>
                <td>{op.username}</td>
                <td>{op.status === 'SUCCESS' ? op.addedCount : '—'}</td>
                <td>{new Date(op.startedAt).toLocaleString('ru-RU')}</td>
                <td>{op.fileName ?? '—'}</td>
                <td className="wrap"><ErrorDetails message={op.errorMessage} /></td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <div className="pagination">
        <button disabled={page === 0} onClick={() => setPage(page - 1)}>Назад</button>
        <span>Страница {page + 1} из {totalPages} (всего {data?.total ?? 0})</span>
        <button disabled={page + 1 >= totalPages} onClick={() => setPage(page + 1)}>Вперёд</button>
      </div>
    </div>
  );
}
