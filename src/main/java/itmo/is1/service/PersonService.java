package itmo.is1.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

import itmo.is1.dto.DecimalParser;
import itmo.is1.dto.PersonImportRecord;
import itmo.is1.dto.PersonRequest;
import itmo.is1.dto.PersonRequest.CoordinatesRequest;
import itmo.is1.dto.PersonRequest.LocationRequest;
import itmo.is1.dto.PersonState;
import itmo.is1.entity.ChangeAction;
import itmo.is1.entity.Color;
import itmo.is1.entity.Coordinates;
import itmo.is1.entity.Country;
import itmo.is1.entity.Location;
import itmo.is1.entity.Person;
import itmo.is1.entity.PersonChange;
import itmo.is1.entity.User;
import itmo.is1.repository.PersonFilter;
import itmo.is1.repository.PersonRepository;
import itmo.is1.security.CurrentUser;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.transaction.Transactional.TxType;

@ApplicationScoped
public class PersonService {

    @Inject
    PersonRepository repository;

    @Inject
    CurrentUser currentUser;

    @Inject
    Event<PersonChangedEvent> changes;

    @Inject
    UniquenessRules uniqueness;

    @Inject
    TransactionRunner transactions;

    @Inject
    ChangeLogService journal;

    public Person getById(Integer id) {
        Person person = repository.find(id);
        if (person == null) {
            throw ApiException.validation("id", "Объект с ID " + id + " не найден");
        }
        return person;
    }

    public List<Person> page(PersonFilter filter, String sortField, boolean ascending,
                             int page, int size) {
        return repository.findPage(filter, sortField, ascending, page * size, size);
    }

    public long total(PersonFilter filter) {
        return repository.count(filter);
    }


    public Person create(PersonRequest request) {
        return transactions.serializable(() -> {
            Person person = new Person();
            person.setOwner(requireCurrentUser());
            uniqueness.requireUniqueName(request.name(), null);
            apply(person, request);
            repository.save(person);
            journal.recordCreated(person, null);
            changes.fire(PersonChangedEvent.created(person.getId()));
            return person;
        });
    }

    /** Изменение по версии, которую видел клиент: если объект уже изменили, 409 вместо затирания чужой правки. */
    public Person update(Integer id, PersonRequest request) {
        Integer expectedVersion = requireVersion(request.version());
        return transactions.serializable(() -> modify(id, request, ChangeAction.UPDATED, null, expectedVersion));
    }

    /** {@code expectedVersion == null} — без проверки версии: откат применяет состояние к текущему объекту. */
    private Person modify(Integer id, PersonRequest request, ChangeAction action, String note,
                          Integer expectedVersion) {
        Person person = getById(id);
        requireCanModify(person, "изменять");
        requireSameVersion(person, expectedVersion);
        uniqueness.requireUniqueName(request.name(), id);
        PersonState before = PersonState.of(person);
        apply(person, request);
        Person merged = repository.update(person);
        journal.recordChanged(action, merged, before, PersonState.of(merged), note);
        changes.fire(PersonChangedEvent.updated(id));
        return merged;
    }

    /**
     * Откат к состоянию из журнала (только администратор) по тем же правилам, что и изменение.
     * Координаты и локация ищутся по значению и создаются заново, только если их нет.
     */
    public Person rollback(Integer id, Integer changeId, RollbackTarget target) {
        return transactions.serializable(() -> {
            if (!requireCurrentUser().isAdmin()) {
                throw ApiException.accessDenied("Откатывать объекты может только администратор");
            }
            Person person = getById(id);
            PersonChange change = journal.requireChange(changeId, id);
            String json = target == RollbackTarget.BEFORE ? change.getBeforeState() : change.getAfterState();
            if (json == null) {
                throw ApiException.validation("changeId", "В записи журнала #" + changeId + " нет состояния "
                        + (target == RollbackTarget.BEFORE ? "«до изменения»" : "«после изменения»"));
            }
            PersonState state = PersonState.fromJson(json);
            if (state.equals(PersonState.of(person))) {
                throw ApiException.validation("changeId", "Объект уже находится в этом состоянии");
            }
            String note = "Откат к состоянию " + (target == RollbackTarget.BEFORE ? "до" : "после")
                    + " изменения #" + changeId;
            return modify(id, requestFor(state), ChangeAction.ROLLED_BACK, note, null);
        });
    }

    /** Запрос на изменение, восстанавливающий снимок; существующие координаты и локация переиспользуются. */
    private PersonRequest requestFor(PersonState state) {
        Integer coordinatesId = repository.findCoordinatesByValue(
                        DecimalParser.parseFloat(state.coordX(), "coordX", "Координата X"),
                        DecimalParser.parseDouble(state.coordY(), "coordY", "Координата Y"))
                .stream().findFirst().map(Coordinates::getId).orElse(null);
        Integer locationId = repository.findLocationsByValue(
                        DecimalParser.parseDouble(state.locX(), "locX", "Координата X локации"),
                        DecimalParser.parseLong(state.locY(), "locY", "Координата Y локации"),
                        DecimalParser.parseDouble(state.locZ(), "locZ", "Координата Z локации"))
                .stream().findFirst().map(Location::getId).orElse(null);
        return new PersonRequest(
                state.name(),
                coordinatesId,
                coordinatesId == null ? new CoordinatesRequest(state.coordX(), state.coordY()) : null,
                state.eyeColor() == null ? null : Color.valueOf(state.eyeColor()),
                Color.valueOf(state.hairColor()),
                locationId,
                locationId == null ? new LocationRequest(state.locX(), state.locY(), state.locZ()) : null,
                state.height(),
                state.nationality() == null ? null : Country.valueOf(state.nationality()),
                null);
    }

    public void delete(Integer id, Integer reassignCoordinatesTo, Integer reassignLocationTo, Integer version) {
        Integer expectedVersion = requireVersion(version);
        transactions.serializable(() -> {
            Person person = getById(id);
            requireCanModify(person, "удалять");
            requireSameVersion(person, expectedVersion);
            Coordinates coordinates = person.getCoordinates();
            Location location = person.getLocation();
            PersonState before = PersonState.of(person);
            User owner = person.getOwner();
            repository.delete(person);
            handleOrphanedCoordinates(coordinates, reassignCoordinatesTo, id);
            handleOrphanedLocation(location, reassignLocationTo, id);
            journal.recordDeleted(id, owner, before);
            changes.fire(PersonChangedEvent.deleted(id));
        });
    }

    /** Все объекты файла создаются в одной транзакции (её открывает ImportService): при любой ошибке — ни одного. */
    @Transactional(TxType.MANDATORY)
    public List<Person> importAll(List<PersonImportRecord> records) {
        User owner = requireCurrentUser();
        Map<Integer, Person> persons = new LinkedHashMap<>();
        // номер записи → её ошибки, чтобы в ответе ошибки шли по порядку записей
        Map<Integer, List<String>> errorsByRecord = new TreeMap<>();
        BiConsumer<Integer, String> addError = (recordNo, message) ->
                errorsByRecord.computeIfAbsent(recordNo, k -> new ArrayList<>()).add(message);

        for (int i = 0; i < records.size(); i++) {
            int recordNo = i + 1;
            List<String> recordErrors = new ArrayList<>();
            Person person = buildImported(records.get(i), recordErrors);
            if (person != null) {
                person.setOwner(owner);
                persons.put(recordNo, person);
            }
            recordErrors.forEach(message -> addError.accept(recordNo, message));
        }
        uniqueness.checkImport(persons, addError);

        List<String> errors = new ArrayList<>();
        errorsByRecord.forEach((recordNo, messages) ->
                messages.forEach(message -> errors.add("Запись №" + recordNo + ": " + message)));
        if (!errors.isEmpty()) {
            throw new ImportException(
                    "Импорт отменён: записи не прошли проверку, ни один объект не создан", errors);
        }

        List<Person> created = List.copyOf(persons.values());
        repository.saveAll(created);
        created.forEach(person -> journal.recordCreated(person, owner, "Импорт из файла"));
        changes.fire(PersonChangedEvent.imported());
        return created;
    }

    /**
     * Проверяет запись импорта по тем же правилам, что и форму, без обращения к БД.
     * Все найденные ошибки добавляет в {@code errors}; при наличии ошибок возвращает null.
     */
    static Person buildImported(PersonImportRecord record, List<String> errors) {
        int errorsBefore = errors.size();
        String name = UniquenessRules.cleanName(record.name());
        if (name == null || name.isEmpty()) {
            errors.add("Имя не может быть пустым");
        }
        if (record.hairColor() == null) {
            errors.add("Цвет волос не может быть null");
        }
        Float height = collect(errors, () -> parseHeight(record.height()));

        Float coordX = null;
        Double coordY = null;
        CoordinatesRequest coordinates = record.coordinates();
        if (coordinates == null) {
            errors.add("Не заданы координаты (coordinates)");
        } else {
            coordX = collect(errors, () -> parseCoordX(coordinates.x()));
            coordY = collect(errors, () -> parseCoordY(coordinates.y()));
        }

        Double locX = null;
        Long locY = null;
        Double locZ = null;
        LocationRequest location = record.location();
        if (location == null) {
            errors.add("Не задана локация (location)");
        } else {
            locX = collect(errors, () -> parseLocX(location.x()));
            locY = collect(errors, () -> parseLocY(location.y()));
            locZ = collect(errors, () -> parseLocZ(location.z()));
        }

        if (errors.size() > errorsBefore) {
            return null;
        }
        Person person = new Person();
        person.setName(name);
        person.setEyeColor(record.eyeColor());
        person.setHairColor(record.hairColor());
        person.setHeight(height);
        person.setNationality(record.nationality());
        person.setCoordinates(new Coordinates(coordX, coordY));
        person.setLocation(new Location(locX, locY, locZ));
        return person;
    }

    private static <T> T collect(List<String> errors, Supplier<T> parser) {
        try {
            return parser.get();
        } catch (ApiException e) {
            errors.add(e.getMessage());
            return null;
        }
    }

    /** Изменять и удалять может владелец и любой администратор; роль читается из БД, поэтому её смена действует сразу. */
    private void requireCanModify(Person person, String action) {
        String owner = person.getOwner().getUsername();
        if (owner.equals(currentUser.getUsername()) || requireCurrentUser().isAdmin()) {
            return;
        }
        throw ApiException.accessDenied(
                "Объект принадлежит пользователю «" + owner + "», "
                        + "вы можете его просматривать, но не " + action);
    }

    private static Integer requireVersion(Integer version) {
        if (version == null) {
            throw ApiException.validation("version",
                    "Не указана версия объекта (version): откройте объект заново и повторите операцию");
        }
        return version;
    }

    private static void requireSameVersion(Person person, Integer expectedVersion) {
        if (expectedVersion != null && !expectedVersion.equals(person.getVersion())) {
            throw ApiException.conflict("version", "Объект #" + person.getId() + " изменён"
                    + " другим пользователем после того, как вы его открыли (ваша версия " + expectedVersion
                    + ", текущая " + person.getVersion() + "). Обновите данные и повторите операцию");
        }
    }

    public boolean isCurrentUserAdmin() {
        return requireCurrentUser().isAdmin();
    }

    private User requireCurrentUser() {
        User user = repository.findUserByUsername(currentUser.getUsername());
        if (user == null) {
            throw ApiException.validation("owner", "Текущий пользователь не найден");
        }
        return user;
    }

    private void handleOrphanedCoordinates(Coordinates coordinates, Integer reassignTo, Integer deletedId) {
        List<Person> dependents = repository.findByCoordinates(coordinates.getId()).stream()
                .filter(p -> !p.getId().equals(deletedId))
                .toList();

        if (dependents.isEmpty()) {
            repository.deleteCoordinates(coordinates);
            return;
        }

        if (reassignTo == null) {
            return;
        }

        Coordinates replacement = repository.findCoordinates(reassignTo);
        if (replacement == null) {
            throw ApiException.validation("reassignCoordinatesTo",
                    "Координаты для перепривязки с ID " + reassignTo + " не найдены");
        }
        dependents.forEach(p -> {
            p.setCoordinates(replacement);
            repository.update(p);
        });
        repository.deleteCoordinates(coordinates);
    }

    private void handleOrphanedLocation(Location location, Integer reassignTo, Integer deletedId) {
        List<Person> dependents = repository.findByLocation(location.getId()).stream()
                .filter(p -> !p.getId().equals(deletedId))
                .toList();

        if (dependents.isEmpty()) {
            repository.deleteLocation(location);
            return;
        }

        if (reassignTo == null) {
            return;
        }

        Location replacement = repository.findLocation(reassignTo);
        if (replacement == null) {
            throw ApiException.validation("reassignLocationTo",
                    "Локация для перепривязки с ID " + reassignTo + " не найдена");
        }
        dependents.forEach(p -> {
            p.setLocation(replacement);
            repository.update(p);
        });
        repository.deleteLocation(location);
    }

    private void apply(Person person, PersonRequest request) {
        applyScalars(person, request);
        person.setCoordinates(resolveCoordinates(request));
        person.setLocation(resolveLocation(request));
    }

    private static void applyScalars(Person person, PersonRequest request) {
        // Имя сохраняется в том виде, в каком его сравнивает проверка уникальности
        String name = UniquenessRules.cleanName(request.name());
        if (name == null || name.isEmpty()) {
            throw ApiException.validation("name", "Имя не может быть пустым");
        }
        float height = parseHeight(request.height());
        person.setName(name);
        person.setEyeColor(request.eyeColor());
        person.setHairColor(request.hairColor());
        person.setHeight(height);
        person.setNationality(request.nationality());
    }

    private Coordinates resolveCoordinates(PersonRequest request) {
        if (request.coordinatesId() != null) {
            Coordinates existing = repository.findCoordinates(request.coordinatesId());
            if (existing == null) {
                throw ApiException.validation("coordinatesId",
                        "Координаты с ID " + request.coordinatesId() + " не найдены");
            }
            return existing;
        }
        if (request.coordinates() == null) {
            throw ApiException.validation("coordinates",
                    "Нужно выбрать существующие координаты или задать новые");
        }
        Coordinates coordinates = newCoordinates(request.coordinates());
        uniqueness.requireUniqueCoordinates(coordinates);
        return repository.saveCoordinates(coordinates);
    }

    private Location resolveLocation(PersonRequest request) {
        if (request.locationId() != null) {
            Location existing = repository.findLocation(request.locationId());
            if (existing == null) {
                throw ApiException.validation("locationId",
                        "Локация с ID " + request.locationId() + " не найдена");
            }
            return existing;
        }
        if (request.location() == null) {
            throw ApiException.validation("location",
                    "Нужно выбрать существующую локацию или задать новую");
        }
        Location location = newLocation(request.location());
        uniqueness.requireUniqueLocation(location);
        return repository.saveLocation(location);
    }

    private static Coordinates newCoordinates(CoordinatesRequest request) {
        return new Coordinates(parseCoordX(request.x()), parseCoordY(request.y()));
    }

    private static Location newLocation(LocationRequest request) {
        return new Location(parseLocX(request.x()), parseLocY(request.y()), parseLocZ(request.z()));
    }

    private static float parseHeight(String raw) {
        float height = DecimalParser.parseFloat(raw, "height", "Рост");
        if (height <= 0) {
            throw ApiException.validation("height", "Рост должен быть больше 0");
        }
        return height;
    }

    private static float parseCoordX(String raw) {
        float x = DecimalParser.parseFloat(raw, "coordX", "Координата X");
        if (x > 91) {
            throw ApiException.validation("coordX", "Координата X не может превышать 91");
        }
        return x;
    }

    private static double parseCoordY(String raw) {
        double y = DecimalParser.parseDouble(raw, "coordY", "Координата Y");
        if (y > 599) {
            throw ApiException.validation("coordY", "Координата Y не может превышать 599");
        }
        return y;
    }

    private static double parseLocX(String raw) {
        return DecimalParser.parseDouble(raw, "locX", "Координата X локации");
    }

    private static long parseLocY(String raw) {
        return DecimalParser.parseLong(raw, "locY", "Координата Y локации");
    }

    private static double parseLocZ(String raw) {
        return DecimalParser.parseDouble(raw, "locZ", "Координата Z локации");
    }
}
