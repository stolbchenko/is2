package itmo.is1.service;

import itmo.is1.dto.PersonImportRecord;
import itmo.is1.dto.PersonRequest;
import itmo.is1.dto.PersonRequest.CoordinatesRequest;
import itmo.is1.dto.PersonRequest.LocationRequest;
import itmo.is1.entity.Color;
import itmo.is1.entity.Person;
import itmo.is1.entity.User;
import itmo.is1.security.CurrentUser;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.event.NotificationOptions;
import jakarta.enterprise.util.TypeLiteral;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletionStage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PersonServiceUniquenessTest {

    private InMemoryPersonRepository repository;
    private PersonService service;
    private RecordingEvent events;
    private User alice;
    private final DirectTransactionRunner transactions = new DirectTransactionRunner();

    static class RecordingEvent implements Event<PersonChangedEvent> {
        final List<PersonChangedEvent> fired = new ArrayList<>();

        @Override
        public void fire(PersonChangedEvent event) {
            fired.add(event);
        }

        @Override
        public <U extends PersonChangedEvent> CompletionStage<U> fireAsync(U event) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <U extends PersonChangedEvent> CompletionStage<U> fireAsync(U event, NotificationOptions options) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Event<PersonChangedEvent> select(Annotation... qualifiers) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <U extends PersonChangedEvent> Event<U> select(Class<U> subtype, Annotation... qualifiers) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <U extends PersonChangedEvent> Event<U> select(TypeLiteral<U> subtype, Annotation... qualifiers) {
            throw new UnsupportedOperationException();
        }
    }

    @BeforeEach
    void setUp() {
        repository = new InMemoryPersonRepository();
        alice = repository.addUser("alice");

        CurrentUser currentUser = new CurrentUser();
        currentUser.setUsername("alice");
        UniquenessRules rules = new UniquenessRules();
        rules.repository = repository;
        events = new RecordingEvent();

        service = new PersonService();
        service.repository = repository;
        service.currentUser = currentUser;
        service.changes = events;
        service.uniqueness = rules;
        service.transactions = transactions;
        service.journal = new TestChangeLog(repository, currentUser).service;
    }

    private static PersonRequest newPerson(String name, String cx, String cy, String lx, String ly, String lz) {
        return new PersonRequest(name, null, new CoordinatesRequest(cx, cy), null, Color.BLACK,
                null, new LocationRequest(lx, ly, lz), "170", null, null);
    }

    private static PersonRequest withExisting(Person person, String name, Integer coordinatesId, Integer locationId) {
        return new PersonRequest(name, coordinatesId, null, null, Color.BLUE, locationId, null, "180", null,
                person.getVersion());
    }

    @Test
    void createsPersonWithUniqueValues() {
        Person created = service.create(newPerson("Анна", "1", "2", "3", "4", "5"));

        assertEquals(1, repository.persons.size());
        assertEquals("Анна", created.getName());
        assertEquals(alice, created.getOwner());
        assertEquals(1, events.fired.size());
        assertEquals(1, transactions.calls, "создание должно идти через SERIALIZABLE-транзакцию");
    }

    @Test
    void createRejectsDuplicateNameBeforeCreatingAnything() {
        repository.addPerson("Анна", alice);
        int coordinatesBefore = repository.coordinates.size();

        ApiException e = assertThrows(ApiException.class,
                () -> service.create(newPerson(" анна ", "1", "2", "3", "4", "5")));
        assertEquals(409, e.getStatus());
        assertEquals(1, repository.persons.size());
        assertEquals(coordinatesBefore, repository.coordinates.size());
        assertEquals(List.of(), events.fired);
    }

    @Test
    void createRejectsDuplicateNewCoordinatesAndLocation() {
        repository.addCoordinates(1, 2);
        repository.addLocation(3, 4, 5);

        assertEquals("coordX", assertThrows(ApiException.class,
                () -> service.create(newPerson("Анна", "1,0", "2", "9", "9", "9"))).getField());
        assertEquals("locX", assertThrows(ApiException.class,
                () -> service.create(newPerson("Борис", "8", "8", "3", "4", "5"))).getField());
        assertEquals(List.of(), repository.persons);
    }

    @Test
    void existingCoordinatesAndLocationCanBeShared() {
        Person anna = repository.addPerson("Анна", alice);

        Person boris = service.create(withExisting(anna, "Борис",
                anna.getCoordinates().getId(), anna.getLocation().getId()));

        assertEquals(anna.getCoordinates(), boris.getCoordinates());
        assertEquals(anna.getLocation(), boris.getLocation());
    }

    @Test
    void updateMayKeepOwnNameButNotTakeAnother() {
        Person anna = repository.addPerson("Анна", alice);
        repository.addPerson("Борис", alice);
        Integer coordinatesId = anna.getCoordinates().getId();
        Integer locationId = anna.getLocation().getId();

        Person updated = service.update(anna.getId(), withExisting(anna, "АННА", coordinatesId, locationId));
        assertEquals("АННА", updated.getName());

        ApiException e = assertThrows(ApiException.class,
                () -> service.update(anna.getId(), withExisting(anna, "борис", coordinatesId, locationId)));
        assertEquals("name", e.getField());
        assertEquals("АННА", anna.getName());
    }

    @Test
    void createUpdateAndDeleteEachRunInOwnSerializableTransaction() {
        Person anna = service.create(newPerson("Анна", "1", "2", "3", "4", "5"));
        service.update(anna.getId(), withExisting(anna, "Анна Петрова",
                anna.getCoordinates().getId(), anna.getLocation().getId()));
        service.delete(anna.getId(), null, null, anna.getVersion());

        assertEquals(3, transactions.calls);
        assertEquals(List.of(), repository.persons);
        assertEquals(List.of(), repository.coordinates, "неиспользуемые координаты удалены вместе с объектом");
    }

    private static PersonImportRecord record(String name, String cx, String cy, String lx, String ly, String lz) {
        return new PersonImportRecord(name, new CoordinatesRequest(cx, cy), null, Color.YELLOW,
                new LocationRequest(lx, ly, lz), "160", null);
    }

    @Test
    void importCreatesAllUniqueRecords() {
        List<Person> created = service.importAll(List.of(
                record("A", "1", "1", "1", "1", "1"),
                record("B", "2", "2", "2", "2", "2")));

        assertEquals(2, created.size());
        assertEquals(2, repository.persons.size());
        assertEquals(1, repository.saveAllCalls);
    }

    @Test
    void importWithDuplicatesCreatesNothingAndReportsErrorsInRecordOrder() {
        repository.addPerson("Существующий", alice);
        int personsBefore = repository.persons.size();

        ImportException e = assertThrows(ImportException.class, () -> service.importAll(List.of(
                record("A", "1", "1", "1", "1", "1"),
                record("существующий", "2", "2", "2", "2", "2"),
                record("a", "3", "3", "3", "3", "3"),
                record("", "1", "1", "4", "4", "4"))));

        assertEquals(List.of(
                "Запись №2: Человек с именем «существующий» уже существует (ID 4), имена должны быть уникальны",
                "Запись №3: имя «a» уже задано в записи №1, имена должны быть уникальны",
                "Запись №4: Имя не может быть пустым"), e.getErrors());
        assertEquals(0, repository.saveAllCalls);
        assertEquals(personsBefore, repository.persons.size());
        assertEquals(List.of(), events.fired);
    }

    @Test
    void importChecksNestedValuesAgainstEachOther() {
        ImportException e = assertThrows(ImportException.class, () -> service.importAll(List.of(
                record("A", "1", "1", "1", "1", "1"),
                record("B", "1.0", "1", "2", "2", "2"),
                record("C", "3", "3", "1", "1", "1,0"))));

        assertEquals(List.of(
                "Запись №2: координаты (1; 1) уже заданы в записи №1, координаты должны быть уникальны",
                "Запись №3: локация (1; 1; 1) уже задана в записи №1, локации должны быть уникальны"),
                e.getErrors());
    }
}
