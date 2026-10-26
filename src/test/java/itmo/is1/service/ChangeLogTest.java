package itmo.is1.service;

import itmo.is1.dto.PersonChangeResponse;
import itmo.is1.dto.PersonImportRecord;
import itmo.is1.dto.PersonRequest.CoordinatesRequest;
import itmo.is1.dto.PersonRequest.LocationRequest;
import itmo.is1.dto.PersonState;
import itmo.is1.dto.PersonState.FieldChange;
import itmo.is1.entity.ChangeAction;
import itmo.is1.entity.Color;
import itmo.is1.entity.Person;
import itmo.is1.entity.PersonChange;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChangeLogTest {

    private ServiceFixture f;

    @BeforeEach
    void setUp() {
        f = new ServiceFixture();
    }

    private List<PersonChange> entries() {
        return f.log.changes.inOrder();
    }

    private List<Integer> ids(List<PersonChange> changes) {
        return changes.stream().map(PersonChange::getId).toList();
    }


    @Test
    void creationIsLoggedWithWhoWhenAndFullState() {
        Person anna = f.create(f.alice, "Анна");

        assertEquals(1, entries().size());
        PersonChange entry = entries().get(0);
        assertEquals(ChangeAction.CREATED, entry.getAction());
        assertEquals(anna.getId(), entry.getPersonId());
        assertEquals(f.alice, entry.getActor());
        assertEquals(f.alice, entry.getOwner());
        assertNotNull(entry.getCreatedAt());
        assertNull(entry.getBeforeState(), "у создания нет состояния «до»");
        PersonState after = PersonState.fromJson(entry.getAfterState());
        assertEquals("Анна", after.name());
        assertEquals("170", after.height());
        assertEquals("BLACK", after.hairColor());
        assertEquals("1", after.coordX());
        assertEquals("1", after.locY());
    }

    @Test
    void updateIsLoggedWithBeforeAfterAndChangedFieldsOnly() {
        Person anna = f.create(f.alice, "Анна");

        f.service.update(anna.getId(), ServiceFixture.withSame(anna, "Анна Новая", "180,5"));

        assertEquals(2, entries().size());
        PersonChange entry = entries().get(1);
        assertEquals(ChangeAction.UPDATED, entry.getAction());
        assertEquals("Анна", PersonState.fromJson(entry.getBeforeState()).name());
        assertEquals("Анна Новая", PersonState.fromJson(entry.getAfterState()).name());
        assertEquals(List.of(
                        new FieldChange("name", "Имя", "Анна", "Анна Новая"),
                        new FieldChange("height", "Рост", "170", "180.5")),
                PersonChangeResponse.of(entry).changes());
    }

    @Test
    void updateWithoutRealChangesIsNotLogged() {
        Person anna = f.create(f.alice, "Анна");

        f.service.update(anna.getId(), ServiceFixture.withSame(anna, "Анна", "170"));

        assertEquals(1, entries().size(), "в журнале только создание");
    }

    @Test
    void changeMadeByAdminIsLoggedUnderAdminButOwnerStaysTheObjectsOwner() {
        Person anna = f.create(f.alice, "Анна");
        f.as(f.admin);

        f.service.update(anna.getId(), ServiceFixture.withSame(anna, "Цензура", "170"));

        PersonChange entry = entries().get(1);
        assertEquals(f.admin, entry.getActor());
        assertEquals(f.alice, entry.getOwner(), "запись принадлежит владельцу объекта: он увидит правку админа");
    }

    @Test
    void deletionIsLoggedWithTheLastStateAndOutlivesTheObject() {
        Person anna = f.create(f.alice, "Анна");
        Integer id = anna.getId();

        f.service.delete(id, null, null, anna.getVersion());

        assertTrue(f.repository.persons.isEmpty());
        PersonChange entry = entries().get(1);
        assertEquals(ChangeAction.DELETED, entry.getAction());
        assertEquals(id, entry.getPersonId());
        assertNull(entry.getAfterState(), "у удаления нет состояния «после»");
        assertEquals("Анна", PersonState.fromJson(entry.getBeforeState()).name());
        assertEquals(f.alice, entry.getOwner());
        // и после удаления объекта журнал по нему доступен
        f.as(f.alice);
        assertEquals(2, f.journal().historyTotal(id));
    }

    @Test
    void importLogsEveryCreatedObjectWithNote() {
        f.as(f.alice);
        PersonImportRecord a = new PersonImportRecord("A", new CoordinatesRequest("1", "1"), null, Color.BLACK,
                new LocationRequest("1", "1", "1"), "170", null);
        PersonImportRecord b = new PersonImportRecord("B", new CoordinatesRequest("2", "2"), null, Color.BLUE,
                new LocationRequest("2", "2", "2"), "160", null);

        List<Person> created = f.service.importAll(List.of(a, b));

        assertEquals(2, entries().size());
        for (int i = 0; i < 2; i++) {
            PersonChange entry = entries().get(i);
            assertEquals(ChangeAction.CREATED, entry.getAction());
            assertEquals(created.get(i).getId(), entry.getPersonId());
            assertEquals(f.alice, entry.getActor());
            assertEquals("Импорт из файла", entry.getNote());
        }
    }

    @Test
    void rejectedOperationsLeaveNoTraceInJournal() {
        Person anna = f.create(f.alice, "Анна");
        int before = entries().size();

        f.as(f.alice);
        assertEquals(409, assertThrows(ApiException.class, () -> f.service.create(f.newPerson(" анна "))).getStatus());
        assertEquals(400, assertThrows(ApiException.class, () -> f.service.update(anna.getId(),
                ServiceFixture.withSame(anna, "Анна", "0"))).getStatus());
        f.as(f.bob);
        assertEquals(403, assertThrows(ApiException.class, () -> f.service.update(anna.getId(),
                ServiceFixture.withSame(anna, "Взлом", "170"))).getStatus());
        assertEquals(403, assertThrows(ApiException.class,
                () -> f.service.delete(anna.getId(), null, null, anna.getVersion())).getStatus());

        assertEquals(before, entries().size());
    }


    @Test
    void userSeesOnlyEntriesOfOwnObjectsIncludingAdminsEdits() {
        Person annaObject = f.create(f.alice, "Анна");
        f.create(f.bob, "Борис");
        f.as(f.admin);
        f.service.update(annaObject.getId(), ServiceFixture.withSame(annaObject, "Анна (правка админа)", "170"));

        f.as(f.alice);
        List<PersonChange> aliceSees = f.journal().history(null, 0, 10);
        assertEquals(2, aliceSees.size(), "создание и правка администратора");
        assertTrue(aliceSees.stream().allMatch(c -> c.getOwner() == f.alice));
        assertEquals(2, f.journal().historyTotal(null));

        f.as(f.bob);
        assertEquals(1, f.journal().history(null, 0, 10).size());
        assertEquals(1, f.journal().historyTotal(null));
    }

    @Test
    void adminSeesEntriesOfAllObjects() {
        f.create(f.alice, "Анна");
        f.create(f.bob, "Борис");
        f.as(f.admin);

        assertEquals(2, f.journal().history(null, 0, 10).size());
        assertEquals(2, f.journal().historyTotal(null));
        assertTrue(f.journal().isCurrentUserAdmin());
    }

    @Test
    void historyOfForeignObjectIsEmptyForRegularUser() {
        Person anna = f.create(f.alice, "Анна");
        f.as(f.bob);

        assertEquals(List.of(), f.journal().history(anna.getId(), 0, 10));
        assertEquals(0, f.journal().historyTotal(anna.getId()));
    }

    @Test
    void historyCanBeLimitedToOneObjectAndIsPagedNewestFirst() {
        Person anna = f.create(f.alice, "Анна");
        f.create(f.alice, "Борис");
        f.service.update(anna.getId(), ServiceFixture.withSame(anna, "Анна 1", "170"));
        f.service.update(anna.getId(), ServiceFixture.withSame(anna, "Анна 2", "170"));
        f.service.update(anna.getId(), ServiceFixture.withSame(anna, "Анна 3", "170"));

        // записи Анны: id 1 (создание), 3, 4, 5; запись 2 — создание Бориса
        assertEquals(List.of(5, 4), ids(f.journal().history(anna.getId(), 0, 2)));
        assertEquals(List.of(3, 1), ids(f.journal().history(anna.getId(), 1, 2)));
        assertEquals(4, f.journal().historyTotal(anna.getId()));
        assertEquals(5, f.journal().historyTotal(null));
    }

    @Test
    void responseDescribesEntryForTheUi() {
        Person anna = f.create(f.alice, "Анна");
        f.service.delete(anna.getId(), null, null, anna.getVersion());

        PersonChangeResponse created = PersonChangeResponse.of(entries().get(0));
        assertEquals(ChangeAction.CREATED, created.action());
        assertEquals("alice", created.actor());
        assertEquals("alice", created.owner());
        assertTrue(created.hasAfter());
        assertTrue(!created.hasBefore());
        assertTrue(created.changes().stream().anyMatch(c -> c.field().equals("name")
                && c.before() == null && c.after().equals("Анна")));
        assertTrue(created.createdAt().matches("\\d{4}-\\d{2}-\\d{2}T.*"), created.createdAt());

        PersonChangeResponse deleted = PersonChangeResponse.of(entries().get(1));
        assertTrue(deleted.hasBefore());
        assertTrue(!deleted.hasAfter());
        assertTrue(deleted.changes().stream().anyMatch(c -> c.field().equals("name")
                && c.before().equals("Анна") && c.after() == null));
    }
}
