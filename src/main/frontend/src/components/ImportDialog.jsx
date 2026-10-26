import { useEffect, useState } from 'react';
import { api } from '../api.js';

const SAMPLE = [
  {
    name: 'Иван Петров',
    coordinates: { x: 10.5, y: 200 },
    eyeColor: 'BLUE',
    hairColor: 'BLACK',
    location: { x: 1.5, y: 2, z: -3.25 },
    height: 180.5,
    nationality: 'RUSSIA',
  },
  {
    name: 'Мари Дюпон',
    coordinates: { x: '-4,2', y: '15' },
    eyeColor: null,
    hairColor: 'YELLOW',
    location: { x: '0', y: '7', z: '0,5' },
    height: '165,3',
    nationality: null,
  },
];

const MAX_SHOWN_ERRORS = 50;
const MAX_FILE_BYTES = 5 * 1024 * 1024; // как на сервере

const formatSize = (bytes) => (bytes < 1024 * 1024
  ? `${Math.max(bytes / 1024, 0.1).toFixed(1)} КБ`
  : `${(bytes / 1024 / 1024).toFixed(1)} МБ`);

function downloadSample() {
  const blob = new Blob([JSON.stringify(SAMPLE, null, 2)], { type: 'application/json' });
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = 'persons-sample.json';
  link.click();
  URL.revokeObjectURL(url);
}

export default function ImportDialog({ onClose, onImported }) {
  const [file, setFile] = useState(null);
  const [busy, setBusy] = useState(false);
  const [result, setResult] = useState(null);
  const [error, setError] = useState(null);
  const [dragging, setDragging] = useState(false);

  // Файл, брошенный мимо области загрузки, не должен открываться в браузере вместо приложения
  useEffect(() => {
    const ignore = (e) => e.preventDefault();
    window.addEventListener('dragover', ignore);
    window.addEventListener('drop', ignore);
    return () => {
      window.removeEventListener('dragover', ignore);
      window.removeEventListener('drop', ignore);
    };
  }, []);

  const pick = (picked) => {
    setResult(null);
    if (picked && picked.size > MAX_FILE_BYTES) {
      setFile(null);
      setError({ message: `Файл слишком большой (${formatSize(picked.size)}): допускается не более 5 МБ`, errors: [] });
      return;
    }
    setFile(picked ?? null);
    setError(null);
  };

  const onDrop = (e) => {
    e.preventDefault();
    setDragging(false);
    const dropped = Array.from(e.dataTransfer.files);
    if (dropped.length > 1) {
      setFile(null);
      setResult(null);
      setError({ message: 'Перетащите один файл: каждый файл импортируется отдельной операцией', errors: [] });
      return;
    }
    pick(dropped[0]);
  };

  const upload = async () => {
    if (!file) {
      setError({ message: 'Выберите файл для импорта', errors: [] });
      return;
    }
    setBusy(true);
    setError(null);
    setResult(null);
    try {
      const response = await api.importPersons(file);
      setResult(response);
      onImported();
    } catch (e) {
      setError({ message: e.message, errors: e.body?.errors ?? [] });
    } finally {
      setBusy(false);
    }
  };

  const shownErrors = error?.errors.slice(0, MAX_SHOWN_ERRORS) ?? [];

  return (
    <div className="modal-backdrop" onClick={onClose}>
      <div className="modal" onClick={(e) => e.stopPropagation()}>
        <h3>Импорт объектов из файла</h3>

        <p className="hint">
          Файл в формате JSON: массив записей. Поля координат и локации задаются внутри
          той же записи. Обязательны <code>name</code>, <code>coordinates</code> (x ≤ 91,
          y ≤ 599), <code>hairColor</code>, <code>location</code> (y — целое),
          <code> height</code> (&gt; 0); <code>eyeColor</code> и <code>nationality</code> можно
          не указывать. Числа — числом или строкой, разделитель — точка или запятая.
          Если хотя бы одна запись содержит ошибку, не будет создан ни один объект.
        </p>
        <p>
          <button type="button" onClick={downloadSample}>Скачать пример файла</button>
        </p>

        <div
          className={`dropzone${dragging ? ' dragging' : ''}`}
          data-testid="dropzone"
          onDragOver={(e) => { e.preventDefault(); setDragging(true); }}
          onDragLeave={() => setDragging(false)}
          onDrop={onDrop}
        >
          {file
            ? <div>Выбран файл: <strong>{file.name}</strong> ({formatSize(file.size)})</div>
            : <div>Перетащите файл сюда из Finder или выберите на компьютере</div>}
          <label className="file-button">
            {file ? 'Выбрать другой файл…' : 'Выбрать файл…'}
            <input
              type="file"
              hidden
              accept=".json,application/json"
              onChange={(e) => { pick(e.target.files[0]); e.target.value = ''; }}
            />
          </label>
        </div>

        {result !== null && (
          <div className="success">
            Операция #{result.operationId} выполнена: создано объектов — {result.imported}
          </div>
        )}

        {error && (
          <div className="banner">
            {error.message}
            {shownErrors.length > 0 && (
              <ul className="import-errors">
                {shownErrors.map((message, i) => <li key={i}>{message}</li>)}
              </ul>
            )}
            {error.errors.length > shownErrors.length && (
              <div>…и ещё ошибок: {error.errors.length - shownErrors.length}</div>
            )}
          </div>
        )}

        <div className="actions">
          <button onClick={onClose}>Закрыть</button>
          <button className="primary" onClick={upload} disabled={busy || !file}>
            {busy ? 'Загрузка…' : 'Импортировать'}
          </button>
        </div>
      </div>
    </div>
  );
}
