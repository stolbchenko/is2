import { useCallback, useEffect, useState } from 'react';
import { api, connectUpdates } from '../api.js';

const ACTION_LABELS = {
  CREATED: 'Создан',
  UPDATED: 'Изменён',
  DELETED: 'Удалён',
  ROLLED_BACK: 'Откат',
};

const show = (value) => (value === null || value === undefined ? '—' : value);

function Diff({ changes }) {
  if (changes.length === 0) {
    return '—';
  }
  return (
    <ul className="diff">
      {changes.map((c) => (
        <li key={c.field}>
          <span className="diff-field">{c.label}:</span>{' '}
          <span className="diff-before">{show(c.before)}</span>
          {' → '}
          <span className="diff-after">{show(c.after)}</span>
        </li>
      ))}
    </ul>
  );
}

/**
 * Журнал изменений. С personId — записи одного объекта (вкладка в окне объекта), иначе — все
 * доступные пользователю. Кнопки отката показываются только администратору и только внутри окна объекта.
 */
export default function ChangeList({ personId = null, allowRollback = false, onRolledBack = () => {} }) {
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(10);
  const [data, setData] = useState(null);
  const [error, setError] = useState(null);
  const [busy, setBusy] = useState(false);

  const load = useCallback(async () => {
    try {
      const params = { page, size };
      if (personId !== null) {
        params.personId = personId;
      }
      setData(await api.changeHistory(params));
      setError(null);
    } catch (e) {
      setError(e.message);
    }
  }, [page, size, personId]);

  useEffect(() => {
    load();
  }, [load]);

  useEffect(() => {
    const socket = connectUpdates(() => load(), load);
    return () => socket.close();
  }, [load]);

  const rollback = async (entry, to) => {
    const what = to === 'BEFORE' ? 'до изменения' : 'после изменения';
    if (!window.confirm(
      `Вернуть объект #${entry.personId} к состоянию ${what} №${entry.id}?\nОткат будет записан в журнал.`)) {
      return;
    }
    setBusy(true);
    setError(null);
    try {
      await api.rollbackPerson(entry.personId, entry.id, to);
      await load();
      onRolledBack();
    } catch (e) {
      setError(e.message);
    } finally {
      setBusy(false);
    }
  };

  const totalPages = data ? Math.max(Math.ceil(data.total / size), 1) : 1;
  const canRollback = allowRollback && data?.admin;
  const columns = 6 + (personId === null ? 1 : 0) + (canRollback ? 1 : 0);

  return (
    <div>
      {data && personId === null && (
        <p className="hint">
          {data.admin
            ? 'Вы администратор: показаны изменения всех объектов.'
            : 'Показаны изменения ваших объектов, в том числе сделанные администратором.'}
        </p>
      )}
      {personId !== null && data && !data.admin && (
        <p className="hint">Изменения вашего объекта, в том числе сделанные администратором.</p>
      )}

      {error && <div className="banner">{error}</div>}

      <div className="table-wrap">
        <table>
          <thead>
            <tr>
              <th>№</th>
              {personId === null && <th>Объект</th>}
              <th>Действие</th>
              <th>Кто</th>
              <th>Когда</th>
              <th>Что изменилось</th>
              <th>Примечание</th>
              {canRollback && <th>Откат</th>}
            </tr>
          </thead>
          <tbody>
            {data && data.items.length === 0 && (
              <tr><td colSpan={columns}>Записей пока нет</td></tr>
            )}
            {data?.items.map((entry, index) => (
              <tr key={entry.id}>
                <td>{entry.id}</td>
                {personId === null && <td>#{entry.personId}</td>}
                <td>
                  <span className={`action action-${entry.action.toLowerCase()}`}>
                    {ACTION_LABELS[entry.action] ?? entry.action}
                  </span>
                </td>
                <td>
                  {entry.actor}
                  {entry.actor !== entry.owner && <span className="hint"> (владелец: {entry.owner})</span>}
                </td>
                <td>{new Date(entry.createdAt).toLocaleString('ru-RU')}</td>
                <td className="wrap"><Diff changes={entry.changes} /></td>
                <td className="note">{entry.note ?? '—'}</td>
                {canRollback && (
                  <td className="rollback">
                    <div className="rollback-actions">
                      {entry.hasBefore && entry.action !== 'DELETED' && (
                        <button disabled={busy} onClick={() => rollback(entry, 'BEFORE')}>
                          Отменить изменение
                        </button>
                      )}
                      {entry.hasAfter && (
                        <button
                          disabled={busy || (index === 0 && page === 0)}
                          title={index === 0 && page === 0 ? 'Это текущее состояние объекта' : ''}
                          onClick={() => rollback(entry, 'AFTER')}
                        >
                          Вернуть эту версию
                        </button>
                      )}
                    </div>
                  </td>
                )}
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <div className="pagination">
        <button disabled={page === 0} onClick={() => setPage(page - 1)}>Назад</button>
        <span>Страница {page + 1} из {totalPages} (всего {data?.total ?? 0})</span>
        <button disabled={page + 1 >= totalPages} onClick={() => setPage(page + 1)}>Вперёд</button>
        <select value={size} onChange={(e) => { setSize(Number(e.target.value)); setPage(0); }}>
          {[5, 10, 20, 50].map((n) => <option key={n} value={n}>{n} на странице</option>)}
        </select>
      </div>
    </div>
  );
}
