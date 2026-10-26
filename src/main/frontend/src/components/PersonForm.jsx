import { useEffect, useRef, useState } from 'react';
import { api } from '../api.js';
import { compareDecimal, isPositive, normalize, validateDecimal, validateInteger } from '../decimal.js';

const COLORS = ['BLACK', 'BLUE', 'YELLOW', 'ORANGE'];
const COUNTRIES = ['RUSSIA', 'FRANCE', 'VATICAN', 'SOUTH_KOREA', 'NORTH_KOREA'];

const initialForm = (person) => ({
  name: person?.name ?? '',
  eyeColor: person?.eyeColor ?? '',
  hairColor: person?.hairColor ?? 'BLACK',
  height: person?.height ?? '',
  nationality: person?.nationality ?? '',
  coordinatesMode: person ? 'existing' : 'new',
  coordinatesId: person?.coordinates?.id ?? '',
  coordX: person?.coordinates?.x ?? '',
  coordY: person?.coordinates?.y ?? '',
  locationMode: person ? 'existing' : 'new',
  locationId: person?.location?.id ?? '',
  locX: person?.location?.x ?? '',
  locY: person?.location?.y ?? '',
  locZ: person?.location?.z ?? '',
});

export default function PersonForm({ person, onClose, onSaved }) {
  const [form, setForm] = useState(() => initialForm(person));
  // Версия на момент открытия формы: если объект успели изменить, сервер ответит 409
  const [version, setVersion] = useState(person?.version ?? null);
  const [stale, setStale] = useState(false);

  const [coordinatesList, setCoordinatesList] = useState([]);
  const [locationsList, setLocationsList] = useState([]);
  const [errors, setErrors] = useState({});
  const [banner, setBanner] = useState(null);
  const [busy, setBusy] = useState(false);
  const modalRef = useRef(null);

  // Ошибка выводится вверху длинной формы — прокручиваем к ней
  useEffect(() => {
    if (banner && modalRef.current) {
      modalRef.current.scrollTop = 0;
    }
  }, [banner]);

  useEffect(() => {
    api.listCoordinates().then(setCoordinatesList).catch(() => {});
    api.listLocations().then(setLocationsList).catch(() => {});
  }, []);

  const set = (key, value) => setForm((prev) => ({ ...prev, [key]: value }));

  const validate = () => {
    const found = {};

    if (!form.name.trim()) {
      found.name = 'Имя не может быть пустым';
    }

    const heightError = validateDecimal(form.height, 'Рост');
    if (heightError) {
      found.height = heightError;
    } else if (!isPositive(form.height)) {
      found.height = 'Рост должен быть больше 0';
    }

    if (form.coordinatesMode === 'new') {
      const xError = validateDecimal(form.coordX, 'Координата X');
      if (xError) {
        found.coordX = xError;
      } else if (compareDecimal(form.coordX, 91) > 0) {
        found.coordX = 'Координата X не может превышать 91';
      }

      const yError = validateDecimal(form.coordY, 'Координата Y');
      if (yError) {
        found.coordY = yError;
      } else if (compareDecimal(form.coordY, 599) > 0) {
        found.coordY = 'Координата Y не может превышать 599';
      }
    } else if (!form.coordinatesId) {
      found.coordinatesId = 'Выберите координаты';
    }

    if (form.locationMode === 'new') {
      const locXError = validateDecimal(form.locX, 'Координата X локации');
      if (locXError) found.locX = locXError;

      const locYError = validateInteger(form.locY, 'Координата Y локации');
      if (locYError) found.locY = locYError;

      const locZError = validateDecimal(form.locZ, 'Координата Z локации');
      if (locZError) found.locZ = locZError;
    } else if (!form.locationId) {
      found.locationId = 'Выберите локацию';
    }

    setErrors(found);
    return Object.keys(found).length === 0;
  };

  const submit = async (event) => {
    event.preventDefault();
    setBanner(null);
    if (!validate()) {
      setBanner('Проверьте правильность заполнения полей');
      return;
    }

    const payload = {
      name: form.name.trim(),
      eyeColor: form.eyeColor || null,
      hairColor: form.hairColor,
      height: normalize(form.height),
      nationality: form.nationality || null,
      coordinatesId: form.coordinatesMode === 'existing' ? Number(form.coordinatesId) : null,
      coordinates: form.coordinatesMode === 'new'
        ? { x: normalize(form.coordX), y: normalize(form.coordY) }
        : null,
      locationId: form.locationMode === 'existing' ? Number(form.locationId) : null,
      location: form.locationMode === 'new'
        ? { x: normalize(form.locX), y: normalize(form.locY), z: normalize(form.locZ) }
        : null,
      version: person ? version : null,
    };

    setBusy(true);
    try {
      if (person) {
        await api.updatePerson(person.id, payload);
      } else {
        await api.createPerson(payload);
      }
      onSaved();
    } catch (e) {
      const serverErrors = e.body?.errors;
      if (e.status === 409 && e.body?.field === 'version') {
        setStale(true);
      } else if (serverErrors) {
        setErrors(Object.fromEntries(serverErrors.map((x) => [x.field, x.message])));
      } else if (e.body?.field) {
        setErrors({ [e.body.field]: e.message });
      }
      setBanner(e.message);
    } finally {
      setBusy(false);
    }
  };

  // Объект изменил другой пользователь: показываем его актуальное состояние, правки вносятся заново
  const reload = async () => {
    setBusy(true);
    try {
      const fresh = await api.getPerson(person.id);
      const [coordinates, locations] = await Promise.all([api.listCoordinates(), api.listLocations()]);
      setCoordinatesList(coordinates);
      setLocationsList(locations);
      setForm(initialForm(fresh));
      setVersion(fresh.version);
      setErrors({});
      setStale(false);
      setBanner('Загружена актуальная версия объекта. Ваши изменения не сохранены — внесите их заново.');
    } catch (e) {
      setBanner(e.message);
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="modal-backdrop" onClick={onClose}>
      <div className="modal" ref={modalRef} onClick={(e) => e.stopPropagation()}>
        <h3>{person ? `Изменение объекта #${person.id}` : 'Создание объекта'}</h3>
        {banner && (
          <div className="banner">
            {banner}
            {stale && (
              <div>
                <button type="button" onClick={reload} disabled={busy}>Загрузить актуальную версию</button>
              </div>
            )}
          </div>
        )}

        <form onSubmit={submit} noValidate>
          <div className="field">
            <label>Имя *</label>
            <input value={form.name} onChange={(e) => set('name', e.target.value)} />
            {errors.name && <div className="error">{errors.name}</div>}
          </div>

          <div className="field">
            <label>Рост * <span className="hint">(точка или запятая)</span></label>
            <input
              inputMode="decimal"
              value={form.height}
              onChange={(e) => set('height', e.target.value)}
            />
            {errors.height && <div className="error">{errors.height}</div>}
          </div>

          <div className="field">
            <label>Цвет волос *</label>
            <select value={form.hairColor} onChange={(e) => set('hairColor', e.target.value)}>
              {COLORS.map((c) => <option key={c} value={c}>{c}</option>)}
            </select>
          </div>

          <div className="field">
            <label>Цвет глаз</label>
            <select value={form.eyeColor} onChange={(e) => set('eyeColor', e.target.value)}>
              <option value="">— не указан —</option>
              {COLORS.map((c) => <option key={c} value={c}>{c}</option>)}
            </select>
          </div>

          <div className="field">
            <label>Гражданство</label>
            <select value={form.nationality} onChange={(e) => set('nationality', e.target.value)}>
              <option value="">— не указано —</option>
              {COUNTRIES.map((c) => <option key={c} value={c}>{c}</option>)}
            </select>
          </div>

          <fieldset>
            <legend>Координаты *</legend>
            <div className="field">
              <label>
                <input
                  type="radio"
                  checked={form.coordinatesMode === 'existing'}
                  onChange={() => set('coordinatesMode', 'existing')}
                /> Выбрать существующие
              </label>
              <select
                value={form.coordinatesId}
                disabled={form.coordinatesMode !== 'existing'}
                onChange={(e) => set('coordinatesId', e.target.value)}
              >
                <option value="">— выберите —</option>
                {coordinatesList.map((c) => (
                  <option key={c.id} value={c.id}>#{c.id}: ({c.x}; {c.y})</option>
                ))}
              </select>
              {errors.coordinatesId && <div className="error">{errors.coordinatesId}</div>}
            </div>

            <div className="field">
              <label>
                <input
                  type="radio"
                  checked={form.coordinatesMode === 'new'}
                  onChange={() => set('coordinatesMode', 'new')}
                /> Создать новые
              </label>
              <input
                inputMode="decimal"
                placeholder="X (не больше 91)"
                disabled={form.coordinatesMode !== 'new'}
                value={form.coordX}
                onChange={(e) => set('coordX', e.target.value)}
              />
              {errors.coordX && <div className="error">{errors.coordX}</div>}
              <input
                inputMode="decimal"
                placeholder="Y (не больше 599)"
                disabled={form.coordinatesMode !== 'new'}
                value={form.coordY}
                onChange={(e) => set('coordY', e.target.value)}
                style={{ marginTop: 6 }}
              />
              {errors.coordY && <div className="error">{errors.coordY}</div>}
            </div>
          </fieldset>

          <fieldset>
            <legend>Локация *</legend>
            <div className="field">
              <label>
                <input
                  type="radio"
                  checked={form.locationMode === 'existing'}
                  onChange={() => set('locationMode', 'existing')}
                /> Выбрать существующую
              </label>
              <select
                value={form.locationId}
                disabled={form.locationMode !== 'existing'}
                onChange={(e) => set('locationId', e.target.value)}
              >
                <option value="">— выберите —</option>
                {locationsList.map((l) => (
                  <option key={l.id} value={l.id}>#{l.id}: ({l.x}; {l.y}; {l.z})</option>
                ))}
              </select>
              {errors.locationId && <div className="error">{errors.locationId}</div>}
            </div>

            <div className="field">
              <label>
                <input
                  type="radio"
                  checked={form.locationMode === 'new'}
                  onChange={() => set('locationMode', 'new')}
                /> Создать новую
              </label>
              <input
                inputMode="decimal"
                placeholder="X"
                disabled={form.locationMode !== 'new'}
                value={form.locX}
                onChange={(e) => set('locX', e.target.value)}
              />
              {errors.locX && <div className="error">{errors.locX}</div>}
              <input
                inputMode="numeric"
                placeholder="Y (целое)"
                disabled={form.locationMode !== 'new'}
                value={form.locY}
                onChange={(e) => set('locY', e.target.value)}
                style={{ marginTop: 6 }}
              />
              {errors.locY && <div className="error">{errors.locY}</div>}
              <input
                inputMode="decimal"
                placeholder="Z"
                disabled={form.locationMode !== 'new'}
                value={form.locZ}
                onChange={(e) => set('locZ', e.target.value)}
                style={{ marginTop: 6 }}
              />
              {errors.locZ && <div className="error">{errors.locZ}</div>}
            </div>
          </fieldset>

          <div className="actions">
            <button type="button" onClick={onClose}>Отмена</button>
            <button type="submit" className="primary" disabled={busy}>Сохранить</button>
          </div>
        </form>
      </div>
    </div>
  );
}
