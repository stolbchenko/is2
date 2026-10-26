import { useCallback, useEffect, useState } from 'react';
import { api, connectUpdates, getUsername } from '../api.js';
import ChangeList from './ChangeList.jsx';

export default function PersonDetails({ id, onClose, onEdit }) {
  const [person, setPerson] = useState(null);
  const [error, setError] = useState(null);
  const [tab, setTab] = useState('data');

  const loadPerson = useCallback(() => {
    api.getPerson(id).then((p) => { setPerson(p); setError(null); }).catch((e) => setError(e.message));
  }, [id]);

  useEffect(() => {
    loadPerson();
  }, [loadPerson]);

  // Объект могли изменить в другой вкладке или другие пользователи
  useEffect(() => {
    const socket = connectUpdates((event) => {
      if (event.action === 'UPDATED' && event.id === id) {
        loadPerson();
      }
    }, loadPerson);
    return () => socket.close();
  }, [id, loadPerson]);

  // Журнал виден владельцу и администратору — им объект и доступен для изменения
  const hasHistory = Boolean(person?.editable);

  return (
    <div className="modal-backdrop" onClick={onClose}>
      <div className={`modal${tab === 'history' ? ' modal-wide' : ''}`} onClick={(e) => e.stopPropagation()}>
        <h3>Объект #{id}</h3>
        {error && <div className="banner">{error}</div>}

        {person && hasHistory && (
          <div className="tabs">
            <button className={tab === 'data' ? 'active' : ''} onClick={() => setTab('data')}>Данные</button>
            <button className={tab === 'history' ? 'active' : ''} onClick={() => setTab('history')}>
              История изменений
            </button>
          </div>
        )}

        {person && tab === 'history' && hasHistory && (
          <>
            <ChangeList personId={id} allowRollback onRolledBack={loadPerson} />
            <div className="actions">
              <button onClick={onClose}>Закрыть</button>
            </div>
          </>
        )}

        {person && tab === 'data' && (
          <>
            <div className="card">
              <strong>Основные атрибуты</strong>
              <p>Имя: {person.name}</p>
              <p>Рост: {person.height}</p>
              <p>Цвет волос: {person.hairColor}</p>
              <p>Цвет глаз: {person.eyeColor ?? '—'}</p>
              <p>Гражданство: {person.nationality ?? '—'}</p>
              <p>Дата создания: {new Date(person.creationDate).toLocaleString('ru-RU')}</p>
              <p>
                Владелец: <strong>{person.owner}</strong>
                {person.owner === getUsername() && <span className="own-badge">вы</span>}
              </p>
            </div>

            <div className="card">
              <strong>Связанный объект: Coordinates #{person.coordinates.id}</strong>
              <p>X: {person.coordinates.x}</p>
              <p>Y: {person.coordinates.y}</p>
            </div>

            <div className="card">
              <strong>Связанный объект: Location #{person.location.id}</strong>
              <p>X: {person.location.x}</p>
              <p>Y: {person.location.y}</p>
              <p>Z: {person.location.z}</p>
            </div>

            {person.editable && person.owner !== getUsername() && (
              <div className="notice">
                Объект принадлежит пользователю «{person.owner}». Вы можете изменять и удалять его
                как администратор.
              </div>
            )}

            {!person.editable && (
              <div className="notice">
                Объект принадлежит пользователю «{person.owner}» — доступен только для просмотра.
              </div>
            )}

            <div className="actions">
              <button onClick={onClose}>Закрыть</button>
              <button
                className="primary"
                onClick={() => onEdit(person)}
                disabled={!person.editable}
              >
                Изменить
              </button>
            </div>
          </>
        )}
      </div>
    </div>
  );
}
