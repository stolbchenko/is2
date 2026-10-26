CREATE TABLE IF NOT EXISTS app_user (
    id SERIAL PRIMARY KEY,
    username TEXT NOT NULL UNIQUE,
    password_hash TEXT NOT NULL,
    role TEXT NOT NULL DEFAULT 'USER' CHECK (role IN ('USER', 'ADMIN'))
);

-- Для БД, созданных до появления ролей
ALTER TABLE app_user ADD COLUMN IF NOT EXISTS role TEXT NOT NULL DEFAULT 'USER'
    CHECK (role IN ('USER', 'ADMIN'));

-- Уникальность имени человека, значений координат и локации намеренно НЕ задана здесь:
-- по требованию ЛР2 она проверяется только в бизнес-логике (itmo.is1.service.UniquenessRules).

CREATE TABLE IF NOT EXISTS coordinates (
    id SERIAL PRIMARY KEY,
    x REAL NOT NULL CHECK (x <= 91),
    y DOUBLE PRECISION NOT NULL CHECK (y <= 599)
);

CREATE TABLE IF NOT EXISTS location (
    id SERIAL PRIMARY KEY,
    x DOUBLE PRECISION NOT NULL,
    y BIGINT NOT NULL,
    z DOUBLE PRECISION NOT NULL
);

CREATE TABLE IF NOT EXISTS person (
    id SERIAL PRIMARY KEY CHECK (id > 0),
    name TEXT NOT NULL CHECK (length(trim(name)) > 0),
    coordinates_id INTEGER NOT NULL REFERENCES coordinates (id),
    creation_date TIMESTAMP WITH TIME ZONE NOT NULL,
    eye_color TEXT CHECK (eye_color IN ('BLACK', 'BLUE', 'YELLOW', 'ORANGE')),
    hair_color TEXT NOT NULL CHECK (hair_color IN ('BLACK', 'BLUE', 'YELLOW', 'ORANGE')),
    location_id INTEGER NOT NULL REFERENCES location (id),
    height REAL NOT NULL CHECK (height > 0),
    nationality TEXT CHECK (nationality IN ('RUSSIA', 'FRANCE', 'VATICAN', 'SOUTH_KOREA', 'NORTH_KOREA')),
    owner_id INTEGER NOT NULL REFERENCES app_user (id),
    -- версия для оптимистической блокировки (@Version): изменение и удаление по устаревшей версии отклоняются
    version INTEGER NOT NULL DEFAULT 1
);

-- Для БД, созданных до появления оптимистической блокировки
ALTER TABLE person ADD COLUMN IF NOT EXISTS version INTEGER NOT NULL DEFAULT 1;

CREATE INDEX IF NOT EXISTS idx_person_name ON person (lower(name));
CREATE INDEX IF NOT EXISTS idx_person_owner ON person (owner_id);

CREATE TABLE IF NOT EXISTS import_operation (
    id SERIAL PRIMARY KEY,
    user_id INTEGER NOT NULL REFERENCES app_user (id),
    status TEXT NOT NULL CHECK (status IN ('SUCCESS', 'FAILED')),
    added_count INTEGER CHECK (added_count >= 0),
    file_name TEXT,
    started_at TIMESTAMP WITH TIME ZONE NOT NULL,
    error_message TEXT,
    -- число добавленных объектов есть только у успешных операций
    CHECK ((status = 'SUCCESS') = (added_count IS NOT NULL))
);

CREATE INDEX IF NOT EXISTS idx_import_operation_user ON import_operation (user_id);

-- Журнал изменений объектов. person_id намеренно без внешнего ключа: записи должны пережить
-- удаление объекта (в них сохраняется, что и кем было удалено).
CREATE TABLE IF NOT EXISTS person_change (
    id SERIAL PRIMARY KEY,
    person_id INTEGER NOT NULL,
    action TEXT NOT NULL CHECK (action IN ('CREATED', 'UPDATED', 'DELETED', 'ROLLED_BACK')),
    actor_id INTEGER NOT NULL REFERENCES app_user (id),
    owner_id INTEGER NOT NULL REFERENCES app_user (id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    before_state TEXT,
    after_state TEXT,
    note TEXT,
    -- у создания нет состояния «до», у удаления — «после», у остальных есть оба
    CHECK ((action = 'CREATED') = (before_state IS NULL)),
    CHECK ((action = 'DELETED') = (after_state IS NULL))
);

CREATE INDEX IF NOT EXISTS idx_person_change_person ON person_change (person_id);
CREATE INDEX IF NOT EXISTS idx_person_change_owner ON person_change (owner_id);
