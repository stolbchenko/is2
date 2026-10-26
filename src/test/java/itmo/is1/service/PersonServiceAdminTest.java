package itmo.is1.service;

import itmo.is1.dto.PersonRequest;
import itmo.is1.dto.PersonResponse;
import itmo.is1.entity.Color;
import itmo.is1.entity.Person;
import itmo.is1.entity.Role;
import itmo.is1.entity.User;
import itmo.is1.security.CurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PersonServiceAdminTest {

    private InMemoryPersonRepository repository;
    private CurrentUser currentUser;
    private PersonService service;
    private PersonServiceUniquenessTest.RecordingEvent events;
    private User alice;
    private User bob;
    private User admin;
    private Person alicePerson;

    @BeforeEach
    void setUp() {
        repository = new InMemoryPersonRepository();
        alice = repository.addUser("alice");
        bob = repository.addUser("bob");
        admin = repository.addUser("admin");
        InMemoryPersonRepository.setField(admin, "role", Role.ADMIN);
        alicePerson = repository.addPerson("Анна", alice);

        currentUser = new CurrentUser();
        events = new PersonServiceUniquenessTest.RecordingEvent();
        UniquenessRules rules = new UniquenessRules();
        rules.repository = repository;

        service = new PersonService();
        service.repository = repository;
        service.currentUser = currentUser;
        service.changes = events;
        service.uniqueness = rules;
        service.transactions = new DirectTransactionRunner();
        service.journal = new TestChangeLog(repository, currentUser).service;
    }

    private void as(User user) {
        currentUser.setUsername(user.getUsername());
    }

    private PersonRequest rename(String name) {
        return new PersonRequest(name, alicePerson.getCoordinates().getId(), null, null, Color.ORANGE,
                alicePerson.getLocation().getId(), null, "190", null, alicePerson.getVersion());
    }

    @Test
    void ownerCanUpdateAndDeleteOwnObject() {
        as(alice);

        assertEquals("Анна Новая", service.update(alicePerson.getId(), rename("Анна Новая")).getName());
        service.delete(alicePerson.getId(), null, null, alicePerson.getVersion());

        assertEquals(List.of(), repository.persons);
    }

    @Test
    void regularUserCannotUpdateOrDeleteSomeoneElsesObject() {
        as(bob);

        ApiException update = assertThrows(ApiException.class,
                () -> service.update(alicePerson.getId(), rename("Взлом")));
        ApiException delete = assertThrows(ApiException.class,
                () -> service.delete(alicePerson.getId(), null, null, alicePerson.getVersion()));

        assertEquals(403, update.getStatus());
        assertEquals(403, delete.getStatus());
        assertTrue(update.getMessage().contains("Объект принадлежит пользователю «alice»"), update.getMessage());
        assertEquals("Анна", alicePerson.getName());
        assertEquals(List.of(alicePerson), repository.persons);
        assertEquals(List.of(), events.fired, "отклонённая операция не должна рассылать событий");
    }

    @Test
    void adminCanUpdateSomeoneElsesObjectAndOwnerStaysTheSame() {
        as(admin);

        Person updated = service.update(alicePerson.getId(), rename("Анна (отредактировано)"));

        assertEquals("Анна (отредактировано)", updated.getName());
        assertEquals(Color.ORANGE, updated.getHairColor());
        assertSame(alice, updated.getOwner(), "владелец не должен переходить к администратору");
        assertEquals(1, events.fired.size());
    }

    @Test
    void adminCanDeleteSomeoneElsesObjectAndItsUnusedCoordinatesAreRemoved() {
        as(admin);

        service.delete(alicePerson.getId(), null, null, alicePerson.getVersion());

        assertEquals(List.of(), repository.persons);
        assertEquals(List.of(), repository.coordinates);
        assertEquals(List.of(), repository.locations);
        assertEquals("DELETED", events.fired.get(0).action());
    }

    @Test
    void adminStillSubjectToValidationAndUniqueness() {
        repository.addPerson("Борис", bob);
        as(admin);

        ApiException duplicate = assertThrows(ApiException.class,
                () -> service.update(alicePerson.getId(), rename(" борис ")));
        assertEquals(409, duplicate.getStatus());

        PersonRequest badHeight = new PersonRequest("Анна", alicePerson.getCoordinates().getId(), null, null,
                Color.BLACK, alicePerson.getLocation().getId(), null, "0", null, alicePerson.getVersion());
        assertEquals("height", assertThrows(ApiException.class,
                () -> service.update(alicePerson.getId(), badHeight)).getField());
        assertEquals("Анна", alicePerson.getName());
    }

    @Test
    void missingObjectIsStill404ForAdmin() {
        as(admin);
        assertEquals(404, assertThrows(ApiException.class,
                () -> service.delete(999, null, null, 1)).getStatus());
    }

    @Test
    void roleChangeTakesEffectImmediately() {
        as(bob);
        assertThrows(ApiException.class, () -> service.update(alicePerson.getId(), rename("До повышения")));

        InMemoryPersonRepository.setField(bob, "role", Role.ADMIN);
        assertTrue(service.isCurrentUserAdmin());
        assertEquals("После повышения", service.update(alicePerson.getId(), rename("После повышения")).getName());

        InMemoryPersonRepository.setField(bob, "role", Role.USER);
        assertFalse(service.isCurrentUserAdmin());
        assertEquals(403, assertThrows(ApiException.class,
                () -> service.update(alicePerson.getId(), rename("После понижения"))).getStatus());
    }

    @Test
    void responseTellsWhoMayEdit() {
        // creationDate в БД ставит @PrePersist, в репозитории на памяти его нет
        InMemoryPersonRepository.setField(alicePerson, "creationDate", java.time.ZonedDateTime.now());
        assertTrue(PersonResponse.of(alicePerson, "alice", false).editable(), "владелец");
        assertFalse(PersonResponse.of(alicePerson, "bob", false).editable(), "чужой обычный пользователь");
        assertTrue(PersonResponse.of(alicePerson, "admin", true).editable(), "администратор");
        assertEquals("alice", PersonResponse.of(alicePerson, "admin", true).owner());
    }
}
