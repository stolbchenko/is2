# Развёртывание ИС

## 1. WildFly 31

```bash
curl -LO https://github.com/wildfly/wildfly/releases/download/31.0.1.Final/wildfly-31.0.1.Final.zip
unzip wildfly-31.0.1.Final.zip -d ~/
export WILDFLY_HOME=~/wildfly-31.0.1.Final
```

## 2. Модуль EclipseLink

WildFly по умолчанию использует Hibernate, поэтому EclipseLink подключается как модуль сервера.

```bash
MODULE_DIR=$WILDFLY_HOME/modules/system/layers/base/org/eclipse/persistence/main
mkdir -p "$MODULE_DIR"

curl -L -o "$MODULE_DIR/eclipselink.jar" \
  https://repo1.maven.org/maven2/org/eclipse/persistence/eclipselink/4.0.2/eclipselink-4.0.2.jar

cp wildfly/eclipselink-module.xml "$MODULE_DIR/module.xml"
```

Затем в `$WILDFLY_HOME/modules/system/layers/base/org/jboss/as/jpa/spi/main/module.xml`
убедиться, что модуль экспортируется (в WildFly 31 это уже так по умолчанию).

## 3. Драйвер PostgreSQL

```bash
DRIVER_DIR=$WILDFLY_HOME/modules/system/layers/base/org/postgresql/main
mkdir -p "$DRIVER_DIR"
curl -L -o "$DRIVER_DIR/postgresql.jar" \
  https://repo1.maven.org/maven2/org/postgresql/postgresql/42.7.4/postgresql-42.7.4.jar
cp wildfly/postgresql-module.xml "$DRIVER_DIR/module.xml"
```

## 4. Локальная БД

```bash
docker compose up -d
```

Поднимает PostgreSQL на 5432 с базой `studs` и применяет `src/main/resources/schema.sql`.

## 5. Datasource и секреты

Пароль БД и секрет JWT **не хранятся в репозитории** — они передаются переменными окружения.

```bash
export IS1_DB_HOST=localhost
export IS1_DB_USER=is1
export IS1_DB_PASSWORD=is1pass
export IS1_JWT_SECRET=$(openssl rand -hex 32)

$WILDFLY_HOME/bin/standalone.sh
```

В отдельном терминале зарегистрировать драйвер и datasource:

```bash
$WILDFLY_HOME/bin/jboss-cli.sh --connect --file=wildfly/datasource.cli
```

## 6. Сборка и деплой

```bash
gradle war
cp build/libs/is1.war $WILDFLY_HOME/standalone/deployments/
```

Приложение доступно на <http://localhost:8080/is1/>.

## 7. Роли и история импорта

Все зарегистрированные пользователи получают роль `USER` и в истории импорта видят
только свои операции и могут изменять и удалять только свои объекты. Администратор (`ADMIN`) видит
операции всех пользователей и может изменять и удалять любые объекты.
Через API роль не выдаётся — только в БД. Администраторов может быть **сколько угодно**: роль хранится
у каждого пользователя отдельно, никаких ограничений на число администраторов нет. Пользователь должен
сначала зарегистрироваться на сайте. Сервер читает роль из БД при каждом запросе, поэтому изменение действует сразу, без перезапуска
сервера и повторного входа; в уже открытой вкладке кнопки обновятся после обновления страницы.

Подключение к БД: локально `docker exec -it is1-postgres psql -U is1 -d studs`, на кафедральном сервере
`psql -h pg -U s467604 -d studs`.

```sql
-- назначить администратора (можно повторить для любого числа пользователей)
UPDATE app_user SET role = 'ADMIN' WHERE username = 'имя_пользователя';

-- назначить сразу нескольких
UPDATE app_user SET role = 'ADMIN' WHERE username IN ('admin1', 'admin2', 'admin3');

-- снять роль
UPDATE app_user SET role = 'USER' WHERE username = 'имя_пользователя';

-- посмотреть всех пользователей и их роли
SELECT id, username, role FROM app_user ORDER BY id;

-- только администраторы
SELECT id, username FROM app_user WHERE role = 'ADMIN' ORDER BY id;
```

Если команда вернула `UPDATE 0`, такого пользователя нет (проверь имя, оно чувствительно к регистру).
Приложение не защищает «последнего администратора»: если снять роль у всех, вернуть её можно только
такой же командой в БД.

Администратор также может изменять и удалять любые объекты и откатывать объект к версии из журнала
изменений (вкладка «История изменений» в окне объекта).

`schema.sql` можно применять повторно: если БД создана до появления ролей, истории и оптимистической
блокировки, он добавит колонки `app_user.role`, `person.version` (существующим объектам — версия 1) и таблицу
`import_operation`, не трогая данные. **После обновления приложения схему нужно применить повторно** —
без колонки `person.version` сервер не сможет читать объекты.

```bash
docker exec -i is1-postgres psql -U is1 -d studs < src/main/resources/schema.sql
```

## 8. Нагрузочный сценарий (JMeter)

Сценарий одновременной работы нескольких пользователей и обоснование уровней изоляции
транзакций — в [`jmeter/README.md`](jmeter/README.md).

```bash
jmeter -n -t jmeter/is1-concurrency.jmx -Jthreads=10 \
    -Juser.classpath=$HOME/wildfly-31.0.1.Final/modules/system/layers/base/org/postgresql/main/postgresql.jar \
    -l results.jtl
python3 jmeter/summary.py results.jtl
```

Сценарий сам назначает роль ADMIN двум пользователям через JDBC, поэтому нужен драйвер PostgreSQL
(`-Juser.classpath`, копировать его в JMeter не нужно) и доступ к БД: по умолчанию
`jdbc:postgresql://localhost:55432/studs`, пользователь `is1`; другие — `-JdbUrl=… -JdbUser=… -JdbPassword=…`.

## 9. Разработка фронтенда

```bash
cd src/main/frontend
npm run dev
```

Vite поднимает дев-сервер и проксирует `/is1/api` и `/is1/ws` на WildFly.

## 10. Сдача на кафедральный сервер

По ТЗ подключение идёт напрямую через хост `pg` (без переопределения `IS1_DB_HOST` —
это значение по умолчанию в `wildfly/datasource.cli`). Если WildFly развёрнут прямо
на кафедральном сервере (например, на helios рядом с `pg`), туннель не нужен —
`pg` резолвится напрямую внутри сети кафедры.

Если разворачиваете и проверяете локально, `pg` извне сети кафедры недоступен —
тогда пробрасываем порт туннелем и явно подменяем хост на `localhost`:

```bash
ssh -L 5432:pg:5432 s467604@helios.cs.ifmo.ru
```

```bash
export IS1_DB_HOST=localhost
export IS1_DB_USER=s467604
export IS1_DB_PASSWORD='<пароль от helios>'
```

Схему применить вручную:

```bash
psql -h pg -U s467604 -d studs -f src/main/resources/schema.sql
```
