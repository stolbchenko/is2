import { useCallback, useEffect, useState } from 'react';
import { api, connectUpdates, getUsername } from '../api.js';
import PersonForm from './PersonForm.jsx';
import PersonDetails from './PersonDetails.jsx';
import DeleteDialog from './DeleteDialog.jsx';
import ImportDialog from './ImportDialog.jsx';

const COLUMNS = [
  { key: 'id', label: 'ID' },
  { key: 'name', label: 'Имя' },
  { key: 'coordinates', label: 'Координаты', sortable: false },
  { key: 'creationDate', label: 'Дата создания' },
  { key: 'eyeColor', label: 'Цвет глаз' },
  { key: 'hairColor', label: 'Цвет волос' },
  { key: 'location', label: 'Локация', sortable: false },
  { key: 'height', label: 'Рост' },
  { key: 'nationality', label: 'Гражданство' },
  { key: 'owner', label: 'Владелец', sortable: false },
];

const COLORS = ['BLACK', 'BLUE', 'YELLOW', 'ORANGE'];
const COUNTRIES = ['RUSSIA', 'FRANCE', 'VATICAN', 'SOUTH_KOREA', 'NORTH_KOREA'];

const VIEW_KEY = 'is1_table_view';

const DEFAULT_VIEW = {
  page: 0, size: 10, sort: 'id', order: 'asc',
  nameFilter: '', eyeColor: '', hairColor: '', nationality: '',
};

function loadView() {
  try {
    const saved = JSON.parse(sessionStorage.getItem(VIEW_KEY));
    return { ...DEFAULT_VIEW, ...saved };
  } catch {
    return { ...DEFAULT_VIEW };
  }
}

export default function PersonTable() {
  const [view, setView] = useState(loadView);
  const [data, setData] = useState({ items: [], total: 0, page: 0, size: 10 });
  const [error, setError] = useState(null);

  const [editing, setEditing] = useState(null);
  const [viewing, setViewing] = useState(null);
  const [deleting, setDeleting] = useState(null);
  const [importing, setImporting] = useState(false);

  const { page, size, sort, order, nameFilter, eyeColor, hairColor, nationality } = view;

  useEffect(() => {
    sessionStorage.setItem(VIEW_KEY, JSON.stringify(view));
  }, [view]);

  const patchView = (patch) => setView((prev) => ({ ...prev, ...patch }));

  const load = useCallback(async () => {
    try {
      const params = { page, size, sort, order };
      if (nameFilter.trim()) {
        params.name = nameFilter.trim();
      }
      if (eyeColor) params.eyeColor = eyeColor;
      if (hairColor) params.hairColor = hairColor;
      if (nationality) params.nationality = nationality;
      setData(await api.listPersons(params));
      setError(null);
    } catch (e) {
      setError(e.message);
    }
  }, [page, size, sort, order, nameFilter, eyeColor, hairColor, nationality]);

  useEffect(() => {
    load();
  }, [load]);

  useEffect(() => {
    const socket = connectUpdates((event) => {
      load();
      if (event.action === 'DELETED') {
        setViewing((current) => (current === event.id ? null : current));
        setEditing((current) => (current && current.id === event.id ? null : current));
        setDeleting((current) => (current && current.id === event.id ? null : current));
      }
    }, load);
    return () => socket.close();
  }, [load]);

  const toggleSort = (column) => {
    if (column.sortable === false) return;
    if (sort === column.key) {
      patchView({ order: order === 'asc' ? 'desc' : 'asc', page: 0 });
    } else {
      patchView({ sort: column.key, order: 'asc', page: 0 });
    }
  };

  const totalPages = Math.max(Math.ceil(data.total / size), 1);

  return (
    <div>
      <div className="toolbar">
        <input
          placeholder="Фильтр по имени (неполное совпадение)"
          value={nameFilter}
          onChange={(e) => patchView({ nameFilter: e.target.value, page: 0 })}
          style={{ flex: '1 1 200px', minWidth: 160 }}
        />
        <select
          value={eyeColor}
          onChange={(e) => patchView({ eyeColor: e.target.value, page: 0 })}
        >
          <option value="">Цвет глаз: любой</option>
          {COLORS.map((c) => <option key={c} value={c}>{c}</option>)}
        </select>
        <select
          value={hairColor}
          onChange={(e) => patchView({ hairColor: e.target.value, page: 0 })}
        >
          <option value="">Цвет волос: любой</option>
          {COLORS.map((c) => <option key={c} value={c}>{c}</option>)}
        </select>
        <select
          value={nationality}
          onChange={(e) => patchView({ nationality: e.target.value, page: 0 })}
        >
          <option value="">Гражданство: любое</option>
          {COUNTRIES.map((c) => <option key={c} value={c}>{c}</option>)}
        </select>
        <select
          value={size}
          onChange={(e) => patchView({ size: Number(e.target.value), page: 0 })}
        >
          {[5, 10, 20, 50].map((n) => (
            <option key={n} value={n}>{n} на странице</option>
          ))}
        </select>
        <span className="toolbar-group">
          <button onClick={() => setImporting(true)}>Импорт из файла</button>
          <button className="primary" onClick={() => setEditing({})}>
            Создать объект
          </button>
        </span>
      </div>

      {error && <div className="banner">{error}</div>}

      <div className="table-wrap">
        <table>
          <thead>
            <tr>
              {COLUMNS.map((column) => (
                <th key={column.key} onClick={() => toggleSort(column)}>
                  {column.label}
                  {sort === column.key ? (order === 'asc' ? ' ▲' : ' ▼') : ''}
                </th>
              ))}
              <th>Действия</th>
            </tr>
          </thead>
          <tbody>
            {data.items.length === 0 && (
              <tr>
                <td colSpan={COLUMNS.length + 1}>Объекты не найдены</td>
              </tr>
            )}
            {data.items.map((person) => (
              <tr key={person.id}>
                <td>{person.id}</td>
                <td>{person.name}</td>
                <td>({person.coordinates.x}; {person.coordinates.y})</td>
                <td>{new Date(person.creationDate).toLocaleString('ru-RU')}</td>
                <td>{person.eyeColor ?? '—'}</td>
                <td>{person.hairColor}</td>
                <td>({person.location.x}; {person.location.y}; {person.location.z})</td>
                <td>{person.height}</td>
                <td>{person.nationality ?? '—'}</td>
                <td>
                  {person.owner}
                  {person.owner === getUsername() && <span className="own-badge">вы</span>}
                </td>
                <td>
                  <button onClick={() => setViewing(person.id)}>Просмотр</button>{' '}
                  <button
                    onClick={() => setEditing(person)}
                    disabled={!person.editable}
                    title={person.editable ? '' : `Объект принадлежит «${person.owner}»`}
                  >
                    Изменить
                  </button>{' '}
                  <button
                    className="danger"
                    onClick={() => setDeleting(person)}
                    disabled={!person.editable}
                    title={person.editable ? '' : `Объект принадлежит «${person.owner}»`}
                  >
                    Удалить
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <div className="pagination">
        <button disabled={page === 0} onClick={() => patchView({ page: page - 1 })}>
          Назад
        </button>
        <span>Страница {page + 1} из {totalPages} (всего {data.total})</span>
        <button
          disabled={page + 1 >= totalPages}
          onClick={() => patchView({ page: page + 1 })}
        >
          Вперёд
        </button>
      </div>

      {editing && (
        <PersonForm
          person={editing.id ? editing : null}
          onClose={() => setEditing(null)}
          onSaved={() => { setEditing(null); load(); }}
        />
      )}

      {viewing && (
        <PersonDetails
          id={viewing}
          onClose={() => setViewing(null)}
          onEdit={(person) => { setViewing(null); setEditing(person); }}
        />
      )}

      {deleting && (
        <DeleteDialog
          person={deleting}
          onClose={() => setDeleting(null)}
          onDeleted={() => { setDeleting(null); load(); }}
        />
      )}

      {importing && (
        <ImportDialog onClose={() => setImporting(false)} onImported={load} />
      )}
    </div>
  );
}
