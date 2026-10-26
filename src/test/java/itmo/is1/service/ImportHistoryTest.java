package itmo.is1.service;

import itmo.is1.dto.ImportOperationResponse;
import itmo.is1.dto.PersonImportRecord;
import itmo.is1.dto.PersonRequest.CoordinatesRequest;
import itmo.is1.dto.PersonRequest.LocationRequest;
import itmo.is1.entity.Color;
import itmo.is1.entity.ImportOperation;
import itmo.is1.entity.ImportStatus;
import itmo.is1.entity.Role;
import itmo.is1.entity.User;
import itmo.is1.security.CurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImportHistoryTest {

    private static final ZonedDateTime STARTED = ZonedDateTime.parse("2026-10-08T12:00:00+03:00");

    private InMemoryPersonRepository repository;
    private InMemoryImportOperationRepository operations;
    private CurrentUser currentUser;
    private DirectTransactionRunner transactions;
    private ImportService service;
    private User alice;
    private User bob;
    private User admin;

    @BeforeEach
    void setUp() {
        repository = new InMemoryPersonRepository();
        operations = new InMemoryImportOperationRepository();
        alice = repository.addUser("alice");
        bob = repository.addUser("bob");
        admin = repository.addUser("admin");
        InMemoryPersonRepository.setField(admin, "role", Role.ADMIN);

        currentUser = new CurrentUser();
        transactions = new DirectTransactionRunner();
        PersonServiceUniquenessTest.RecordingEvent events = new PersonServiceUniquenessTest.RecordingEvent();
        UniquenessRules rules = new UniquenessRules();
        rules.repository = repository;

        PersonService personService = new PersonService();
        personService.repository = repository;
        personService.currentUser = currentUser;
        personService.changes = events;
        personService.uniqueness = rules;
        personService.transactions = transactions;
        personService.journal = new TestChangeLog(repository, currentUser).service;

        service = new ImportService();
        service.personService = personService;
        service.personRepository = repository;
        service.operations = operations;
        service.currentUser = currentUser;
        service.transactions = transactions;
        service.changes = events;
    }

    private void as(User user) {
        currentUser.setUsername(user.getUsername());
    }

    private static PersonImportRecord record(String name, String x) {
        return new PersonImportRecord(name, new CoordinatesRequest(x, "1"), null, Color.BLACK,
                new LocationRequest(x, "1", "1"), "170", null);
    }

    private void history(User user, ImportStatus status) {
        operations.save(status == ImportStatus.SUCCESS
                ? ImportOperation.success(user, 1, "f.json", STARTED)
                : ImportOperation.failure(user, "ошибка", "f.json", STARTED));
    }

    @Test
    void successfulImportIsRecordedWithAddedCountInSameTransaction() {
        as(alice);

        ImportOperation operation = service.importRecords(
                List.of(record("A", "1"), record("B", "2")), "persons.json", STARTED);

        assertEquals(ImportStatus.SUCCESS, operation.getStatus());
        assertEquals(2, operation.getAddedCount());
        assertEquals(alice, operation.getUser());
        assertEquals("persons.json", operation.getFileName());
        assertNull(operation.getErrorMessage());
        assertEquals(List.of(operation), operations.saved);
        assertEquals(2, repository.persons.size());
        assertEquals(1, transactions.calls, "объекты и запись истории — в одной SERIALIZABLE-транзакции");
    }

    @Test
    void failedImportCreatesNoObjectsAndIsRecordedWithoutCount() {
        as(alice);
        ImportException error = assertThrows(ImportException.class, () -> service.importRecords(
                List.of(record("A", "1"), record("", "2")), "bad.json", STARTED));
        assertTrue(operations.saved.isEmpty(), "запись SUCCESS не должна появиться");
        assertTrue(repository.persons.isEmpty(), "ни один объект не создан");

        ImportOperation failure = service.recordFailure(error, "bad.json", STARTED);

        assertEquals(ImportStatus.FAILED, failure.getStatus());
        assertNull(failure.getAddedCount(), "число объектов — только у успешных операций");
        assertEquals(alice, failure.getUser());
        assertTrue(failure.getErrorMessage().contains("Запись №2: Имя не может быть пустым"),
                failure.getErrorMessage());
    }

    @Test
    void userSeesOnlyOwnOperations() {
        history(alice, ImportStatus.SUCCESS);
        history(bob, ImportStatus.SUCCESS);
        history(alice, ImportStatus.FAILED);
        history(admin, ImportStatus.SUCCESS);
        as(alice);

        List<ImportOperation> visible = service.history(0, 10);

        assertEquals(List.of(3, 1), visible.stream().map(ImportOperation::getId).toList(), "свои, новые первыми");
        assertEquals(2, service.historyTotal());
        assertFalse(service.isCurrentUserAdmin());
    }

    @Test
    void adminSeesOperationsOfAllUsers() {
        history(alice, ImportStatus.SUCCESS);
        history(bob, ImportStatus.FAILED);
        history(alice, ImportStatus.SUCCESS);
        as(admin);

        List<ImportOperation> visible = service.history(0, 10);

        assertEquals(List.of(3, 2, 1), visible.stream().map(ImportOperation::getId).toList());
        assertEquals(3, service.historyTotal());
        assertTrue(service.isCurrentUserAdmin());
    }

    @Test
    void historyIsPaged() {
        for (int i = 0; i < 5; i++) {
            history(bob, ImportStatus.SUCCESS);
        }
        as(bob);

        assertEquals(List.of(3, 2), service.history(1, 2).stream().map(ImportOperation::getId).toList());
        assertEquals(5, service.historyTotal());
    }

    @Test
    void responseContainsRequiredFields() {
        ImportOperation ok = operations.save(ImportOperation.success(alice, 7, "ok.json", STARTED));
        ImportOperation failed = operations.save(ImportOperation.failure(bob, "ошибка", "bad.json", STARTED));

        ImportOperationResponse okResponse = ImportOperationResponse.of(ok);
        assertEquals(ok.getId(), okResponse.id());
        assertEquals(ImportStatus.SUCCESS, okResponse.status());
        assertEquals("alice", okResponse.username());
        assertEquals(7, okResponse.addedCount());

        ImportOperationResponse failedResponse = ImportOperationResponse.of(failed);
        assertEquals(ImportStatus.FAILED, failedResponse.status());
        assertEquals("bob", failedResponse.username());
        assertNull(failedResponse.addedCount());
        assertEquals("ошибка", failedResponse.errorMessage());
    }
}
