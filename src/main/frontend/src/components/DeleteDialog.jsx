import { useEffect, useState } from 'react';
import { api } from '../api.js';

export default function DeleteDialog({ person, onClose, onDeleted }) {
  const [coordinatesList, setCoordinatesList] = useState([]);
  const [locationsList, setLocationsList] = useState([]);
  const [reassignCoordinates, setReassignCoordinates] = useState('');
  const [reassignLocation, setReassignLocation] = useState('');
  const [error, setError] = useState(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    api.listCoordinates()
      .then((list) => setCoordinatesList(list.filter((c) => c.id !== person.coordinates.id)))
      .catch(() => {});
    api.listLocations()
      .then((list) => setLocationsList(list.filter((l) => l.id !== person.location.id)))
      .catch(() => {});
  }, [person]);

  const confirm = async () => {
    setBusy(true);
    setError(null);
    try {
      // Версия, которую видел пользователь: если объект успели изменить, сервер ответит 409
      const params = { version: person.version };
      if (reassignCoordinates) params.reassignCoordinatesTo = reassignCoordinates;
      if (reassignLocation) params.reassignLocationTo = reassignLocation;
      await api.deletePerson(person.id, params);
      onDeleted();
    } catch (e) {
      setError(e.message);
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="modal-backdrop" onClick={onClose}>
      <div className="modal" onClick={(e) => e.stopPropagation()}>
        <h3>Удаление объекта #{person.id}</h3>
        <p>Удалить объект «{person.name}»?</p>
        {error && <div className="banner">{error}</div>}

        <div className="field">
          <label>Перепривязать координаты #{person.coordinates.id} к:</label>
          <select
            value={reassignCoordinates}
            onChange={(e) => setReassignCoordinates(e.target.value)}
          >
            <option value="">— удалить вместе с объектом, если не используются —</option>
            {coordinatesList.map((c) => (
              <option key={c.id} value={c.id}>#{c.id}: ({c.x}; {c.y})</option>
            ))}
          </select>
        </div>

        <div className="field">
          <label>Перепривязать локацию #{person.location.id} к:</label>
          <select
            value={reassignLocation}
            onChange={(e) => setReassignLocation(e.target.value)}
          >
            <option value="">— удалить вместе с объектом, если не используется —</option>
            {locationsList.map((l) => (
              <option key={l.id} value={l.id}>#{l.id}: ({l.x}; {l.y}; {l.z})</option>
            ))}
          </select>
        </div>

        <div className="actions">
          <button onClick={onClose}>Отмена</button>
          <button className="danger" onClick={confirm} disabled={busy}>Удалить</button>
        </div>
      </div>
    </div>
  );
}
