package itmo.is1.service;

import itmo.is1.entity.ChangeAction;
import itmo.is1.entity.ImportOperation;
import itmo.is1.entity.PersonChange;
import itmo.is1.entity.Person;
import itmo.is1.entity.Role;
import itmo.is1.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Администраторов может быть сколько угодно: роль хранится у каждого пользователя отдельно и
 * назначается вручную в БД ({@code UPDATE app_user SET role = 'ADMIN' ...}).
 */
class MultipleAdminsTest {

    private ServiceFixture f;
    private User admin2;
    private Person annaObject;

    @BeforeEach
    void setUp() {
        f = new ServiceFixture();                 // в фикстуре уже есть администратор «admin»
        admin2 = f.repository.addUser("admin2");
        promote(admin2);
        annaObject = f.create(f.alice, "Анна");
    }

    private static void promote(User user) {
        InMemoryPersonRepository.setField(user, "role", Role.ADMIN);
    }

    private static void demote(User user) {
        InMemoryPersonRepository.setField(user, "role", Role.USER);
    }

    @Test
    void bothAdminsAreRecognisedAsAdmins() {
        f.as(f.admin);
        assertTrue(f.service.isCurrentUserAdmin());
        f.as(admin2);
        assertTrue(f.service.isCurrentUserAdmin());
        f.as(f.alice);
        assertFalse(f.service.isCurrentUserAdmin());
    }

    @Test
    void eachAdminCanChangeSomeoneElsesObjectAndEachChangeIsLoggedUnderItsAuthor() {
        f.as(f.admin);
        f.service.update(annaObject.getId(), ServiceFixture.withSame(annaObject, "Правка первого админа", "170"));
        f.as(admin2);
        f.service.update(annaObject.getId(), ServiceFixture.withSame(annaObject, "Правка второго админа", "170"));

        List<PersonChange> entries = f.log.changes.inOrder();
        assertEquals(List.of(f.alice, f.admin, admin2),
                entries.stream().map(PersonChange::getActor).toList());
        assertSame(f.alice, annaObject.getOwner(), "владелец объекта не меняется");
        assertEquals("Правка второго админа", annaObject.getName());
    }

    @Test
    void adminCanModifyObjectOwnedByAnotherAdmin() {
        Person adminsObject = f.create(f.admin, "Объект админа");
        f.as(admin2);

        f.service.delete(adminsObject.getId(), null, null, adminsObject.getVersion());

        assertFalse(f.repository.persons.contains(adminsObject));
    }

    @Test
    void secondAdminDeletingAnAlreadyDeletedObjectGetsNotFound() {
        f.as(f.admin);
        f.service.delete(annaObject.getId(), null, null, annaObject.getVersion());

        f.as(admin2);
        ApiException e = assertThrows(ApiException.class,
                () -> f.service.delete(annaObject.getId(), null, null, annaObject.getVersion()));

        assertEquals(404, e.getStatus());
        long deletions = f.log.changes.inOrder().stream()
                .filter(c -> c.getAction() == ChangeAction.DELETED).count();
        assertEquals(1, deletions, "удаление записано один раз");
    }

    @Test
    void adminsCanRollBackAndUndoEachOthersRollbacks() {
        f.as(f.alice);
        f.service.update(annaObject.getId(), ServiceFixture.withSame(annaObject, "Нецензурное имя", "170"));
        PersonChange bad = f.log.changes.inOrder().get(1);

        f.as(f.admin);
        f.service.rollback(annaObject.getId(), bad.getId(), RollbackTarget.BEFORE);
        assertEquals("Анна", annaObject.getName());
        PersonChange rollback = f.log.changes.inOrder().get(2);

        f.as(admin2);
        f.service.rollback(annaObject.getId(), rollback.getId(), RollbackTarget.BEFORE);
        assertEquals("Нецензурное имя", annaObject.getName());
        assertEquals(List.of(f.admin, admin2), f.log.changes.inOrder().subList(2, 4).stream()
                .map(PersonChange::getActor).toList());
    }

    @Test
    void bothAdminsSeeTheWholeJournalAndRegularUsersOnlyTheirOwn() {
        f.create(f.bob, "Борис");

        for (User admin : List.of(f.admin, admin2)) {
            f.as(admin);
            assertEquals(2, f.journal().historyTotal(null), admin.getUsername());
        }
        f.as(f.alice);
        assertEquals(1, f.journal().historyTotal(null));
    }

    @Test
    void bothAdminsSeeAllImportOperations() {
        ImportService imports = new ImportService();
        imports.personRepository = f.repository;
        imports.currentUser = f.currentUser;
        imports.operations = new InMemoryImportOperationRepository();
        ZonedDateTime now = ZonedDateTime.now();
        imports.operations.save(ImportOperation.success(f.alice, 3, "a.json", now));
        imports.operations.save(ImportOperation.failure(f.bob, "ошибка", "b.json", now));

        for (User admin : List.of(f.admin, admin2)) {
            f.as(admin);
            assertEquals(2, imports.historyTotal(), admin.getUsername());
            assertTrue(imports.isCurrentUserAdmin());
        }
        f.as(f.alice);
        assertEquals(1, imports.historyTotal());
    }

    @Test
    void demotingOneAdminDoesNotAffectTheOthers() {
        demote(admin2);

        f.as(admin2);
        assertEquals(403, assertThrows(ApiException.class, () -> f.service.update(annaObject.getId(),
                ServiceFixture.withSame(annaObject, "Бывший админ", "170"))).getStatus());
        f.as(f.admin);
        f.service.update(annaObject.getId(), ServiceFixture.withSame(annaObject, "Админ всё ещё может", "170"));
        assertEquals("Админ всё ещё может", annaObject.getName());
    }

    @Test
    void anyNumberOfAdminsCanBeAppointed() {
        promote(f.bob);          // третий администратор
        promote(f.alice);        // четвёртый — в том числе владелец объекта

        for (User user : List.of(f.admin, admin2, f.bob, f.alice)) {
            f.as(user);
            assertTrue(f.service.isCurrentUserAdmin(), user.getUsername());
        }
        f.as(f.bob);
        f.service.update(annaObject.getId(), ServiceFixture.withSame(annaObject, "Правка третьего админа", "170"));
        assertEquals("Правка третьего админа", annaObject.getName());
    }
}
