package itmo.is1.service;

import itmo.is1.dto.PersonRequest;
import itmo.is1.entity.Person;
import itmo.is1.entity.Role;
import itmo.is1.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OptimisticLockingTest {

    private ServiceFixture f;
    private User admin2;
    private Person anna;

    @BeforeEach
    void setUp() {
        f = new ServiceFixture();
        admin2 = f.repository.addUser("admin2");
        InMemoryPersonRepository.setField(admin2, "role", Role.ADMIN);
        anna = f.create(f.alice, "Анна");
    }

    @Test
    void newObjectHasVersionOneAndEachUpdateIncrementsIt() {
        assertEquals(1, anna.getVersion());
        f.as(f.alice);
        f.service.update(anna.getId(), ServiceFixture.withSame(anna, "Анна 2", "171"));
        assertEquals(2, anna.getVersion());
        f.service.update(anna.getId(), ServiceFixture.withSame(anna, "Анна 3", "172"));
        assertEquals(3, anna.getVersion());
    }

    @Test
    void twoAdminsEditTheSameObject_secondEditBasedOnStaleVersionIsRejected() {
        // оба администратора открыли форму, когда версия была 1
        PersonRequest fromAdmin1 = ServiceFixture.withSame(anna, "Правка первого админа", "150");
        PersonRequest fromAdmin2 = ServiceFixture.withSame(anna, "Правка второго админа", "190");

        f.as(f.admin);
        f.service.update(anna.getId(), fromAdmin1);
        int journalSize = f.log.changes.inOrder().size();

        f.as(admin2);
        ApiException e = assertThrows(ApiException.class, () -> f.service.update(anna.getId(), fromAdmin2));

        assertEquals(409, e.getStatus());
        assertEquals("version", e.getField());
        assertTrue(e.getMessage().contains("ваша версия 1, текущая 2"), e.getMessage());
        assertEquals("Правка первого админа", anna.getName(), "правка первого администратора не потеряна");
        assertEquals(150f, anna.getHeight());
        assertEquals(2, anna.getVersion());
        assertEquals(journalSize, f.log.changes.inOrder().size(), "отклонённое изменение не попадает в журнал");
    }

    @Test
    void afterReloadingTheSecondAdminCanApplyTheirEdit() {
        f.as(f.admin);
        f.service.update(anna.getId(), ServiceFixture.withSame(anna, "Правка первого админа", "150"));

        f.as(admin2);
        f.service.update(anna.getId(), ServiceFixture.withSame(anna, "Правка второго админа", "190"));

        assertEquals("Правка второго админа", anna.getName());
        assertEquals(3, anna.getVersion());
    }

    @Test
    void updateWithoutVersionIsRejected() {
        f.as(f.alice);
        PersonRequest s = ServiceFixture.withSame(anna, "Без версии", "170");
        PersonRequest noVersion = new PersonRequest(s.name(), s.coordinatesId(), null, null, s.hairColor(),
                s.locationId(), null, s.height(), null, null);

        ApiException e = assertThrows(ApiException.class, () -> f.service.update(anna.getId(), noVersion));

        assertEquals(400, e.getStatus());
        assertEquals("version", e.getField());
        assertEquals("Анна", anna.getName());
    }

    @Test
    void deleteBasedOnStaleVersionIsRejectedAndObjectSurvives() {
        int seenByAdmin2 = anna.getVersion();
        f.as(f.admin);
        f.service.update(anna.getId(), ServiceFixture.withSame(anna, "Исправлено первым админом", "170"));

        f.as(admin2);
        ApiException e = assertThrows(ApiException.class,
                () -> f.service.delete(anna.getId(), null, null, seenByAdmin2));

        assertEquals(409, e.getStatus());
        assertEquals("version", e.getField());
        assertTrue(f.repository.persons.contains(anna));
    }

    @Test
    void deleteWithCurrentVersionSucceeds() {
        f.as(admin2);
        f.service.delete(anna.getId(), null, null, anna.getVersion());

        assertTrue(f.repository.persons.isEmpty());
    }

    @Test
    void deleteWithoutVersionIsRejected() {
        f.as(f.alice);
        ApiException e = assertThrows(ApiException.class, () -> f.service.delete(anna.getId(), null, null, null));

        assertEquals(400, e.getStatus());
        assertTrue(f.repository.persons.contains(anna));
    }

    @Test
    void editOfAlreadyDeletedObjectGives404() {
        PersonRequest stale = ServiceFixture.withSame(anna, "Поздно", "170");
        f.as(f.admin);
        f.service.delete(anna.getId(), null, null, anna.getVersion());

        f.as(admin2);
        assertEquals(404, assertThrows(ApiException.class, () -> f.service.update(anna.getId(), stale)).getStatus());
    }

    @Test
    void permissionIsCheckedBeforeVersion() {
        f.as(f.bob);
        PersonRequest stale = new PersonRequest("Взлом", anna.getCoordinates().getId(), null, null,
                anna.getHairColor(), anna.getLocation().getId(), null, "170", null, 999);

        assertEquals(403, assertThrows(ApiException.class, () -> f.service.update(anna.getId(), stale)).getStatus());
    }

    @Test
    void rollbackDoesNotRequireVersionButIncrementsIt() {
        f.as(f.alice);
        f.service.update(anna.getId(), ServiceFixture.withSame(anna, "Анна 2", "171"));
        var change = f.log.changes.inOrder().get(1);

        f.as(f.admin);
        f.service.rollback(anna.getId(), change.getId(), RollbackTarget.BEFORE);

        assertEquals("Анна", anna.getName());
        assertEquals(3, anna.getVersion());
    }
}
