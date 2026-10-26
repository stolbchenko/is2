package itmo.is1.service;

import itmo.is1.dto.PersonChangeResponse;
import itmo.is1.dto.PersonRequest;
import itmo.is1.dto.PersonRequest.CoordinatesRequest;
import itmo.is1.dto.PersonState;
import itmo.is1.dto.PersonState.FieldChange;
import itmo.is1.entity.ChangeAction;
import itmo.is1.entity.Color;
import itmo.is1.entity.Coordinates;
import itmo.is1.entity.Person;
import itmo.is1.entity.PersonChange;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RollbackTest {

    private ServiceFixture f;
    private Person anna;

    @BeforeEach
    void setUp() {
        f = new ServiceFixture();
        anna = f.create(f.alice, "Анна");
    }

    private List<PersonChange> entries() {
        return f.log.changes.inOrder();
    }

    private PersonChange lastEntry() {
        return entries().get(entries().size() - 1);
    }

    /** Владелец вносит «плохое» изменение: имя и рост. */
    private PersonChange badEdit() {
        f.as(f.alice);
        f.service.update(anna.getId(), ServiceFixture.withSame(anna, "Нецензурное имя", "190"));
        return lastEntry();
    }

    private ApiException rollbackFails(Integer changeId, RollbackTarget target) {
        return assertThrows(ApiException.class, () -> f.service.rollback(anna.getId(), changeId, target));
    }

    @Test
    void adminRestoresStateBeforeTheBadChange() {
        PersonChange bad = badEdit();
        f.as(f.admin);

        Person restored = f.service.rollback(anna.getId(), bad.getId(), RollbackTarget.BEFORE);

        assertEquals("Анна", restored.getName());
        assertEquals(170f, restored.getHeight());
        assertSame(f.alice, restored.getOwner(), "владелец объекта не меняется");
        assertEquals("UPDATED", f.events.fired.get(f.events.fired.size() - 1).action());
    }

    @Test
    void rollbackIsLoggedAsItsOwnEntry() {
        PersonChange bad = badEdit();
        f.as(f.admin);

        f.service.rollback(anna.getId(), bad.getId(), RollbackTarget.BEFORE);

        PersonChange entry = lastEntry();
        assertEquals(ChangeAction.ROLLED_BACK, entry.getAction());
        assertEquals(f.admin, entry.getActor());
        assertEquals(f.alice, entry.getOwner());
        assertEquals("Откат к состоянию до изменения #" + bad.getId(), entry.getNote());
        assertEquals(List.of(
                        new FieldChange("name", "Имя", "Нецензурное имя", "Анна"),
                        new FieldChange("height", "Рост", "190", "170")),
                PersonChangeResponse.of(entry).changes());
    }

    @Test
    void adminCanRestoreAnyEarlierVersion() {
        f.as(f.alice);
        f.service.update(anna.getId(), ServiceFixture.withSame(anna, "Версия 2", "171"));
        PersonChange toVersion2 = lastEntry();
        f.service.update(anna.getId(), ServiceFixture.withSame(anna, "Версия 3", "172"));
        f.service.update(anna.getId(), ServiceFixture.withSame(anna, "Версия 4", "173"));
        f.as(f.admin);

        Person restored = f.service.rollback(anna.getId(), toVersion2.getId(), RollbackTarget.AFTER);

        assertEquals("Версия 2", restored.getName());
        assertEquals(171f, restored.getHeight());
        assertEquals("Откат к состоянию после изменения #" + toVersion2.getId(), lastEntry().getNote());
    }

    @Test
    void rollbackItselfCanBeUndone() {
        PersonChange bad = badEdit();
        f.as(f.admin);
        f.service.rollback(anna.getId(), bad.getId(), RollbackTarget.BEFORE);
        PersonChange rollback = lastEntry();

        Person undone = f.service.rollback(anna.getId(), rollback.getId(), RollbackTarget.BEFORE);

        assertEquals("Нецензурное имя", undone.getName());
        assertEquals(2, entries().stream().filter(e -> e.getAction() == ChangeAction.ROLLED_BACK).count());
    }

    @Test
    void existingCoordinatesWithTheSameValuesAreReused() {
        Coordinates original = anna.getCoordinates();
        f.as(f.alice);
        f.service.update(anna.getId(), new PersonRequest("Анна", null, new CoordinatesRequest("50", "60"), null,
                Color.BLACK, anna.getLocation().getId(), null, "170", null, anna.getVersion()));
        assertNotEquals(original, anna.getCoordinates());
        PersonChange moved = lastEntry();
        int coordinatesBefore = f.repository.coordinates.size();
        f.as(f.admin);

        f.service.rollback(anna.getId(), moved.getId(), RollbackTarget.BEFORE);

        assertSame(original, anna.getCoordinates(), "возвращены те же координаты, а не копия");
        assertEquals(coordinatesBefore, f.repository.coordinates.size(), "новых координат не создано");
    }

    @Test
    void coordinatesAreRecreatedWhenNoLongerExist() {
        Coordinates original = anna.getCoordinates();
        f.as(f.alice);
        f.service.update(anna.getId(), new PersonRequest("Анна", null, new CoordinatesRequest("50", "60"), null,
                Color.BLACK, anna.getLocation().getId(), null, "170", null, anna.getVersion()));
        PersonChange moved = lastEntry();
        f.repository.coordinates.remove(original);
        f.as(f.admin);

        f.service.rollback(anna.getId(), moved.getId(), RollbackTarget.BEFORE);

        assertEquals(1f, anna.getCoordinates().getX());
        assertEquals(1.0, anna.getCoordinates().getY());
        assertNotEquals(original.getId(), anna.getCoordinates().getId());
        assertEquals(1, f.repository.findCoordinatesByValue(1f, 1.0).size(), "дубля по значению нет");
    }

    @Test
    void rollbackRespectsNameUniqueness() {
        f.as(f.alice);
        f.service.update(anna.getId(), ServiceFixture.withSame(anna, "Анна Новая", "170"));
        PersonChange rename = lastEntry();
        f.create(f.bob, "Анна"); // прежнее имя освободилось, и им воспользовались
        int entriesBefore = entries().size();
        f.as(f.admin);

        ApiException e = rollbackFails(rename.getId(), RollbackTarget.BEFORE);

        assertEquals(409, e.getStatus());
        assertEquals("name", e.getField());
        assertEquals("Анна Новая", anna.getName(), "объект не изменился");
        assertEquals(entriesBefore, entries().size(), "неудачный откат не попадает в журнал");
    }

    @Test
    void stateIsValidatedLikeAnyOtherChange() {
        PersonState broken = PersonState.of(anna);
        PersonState zeroHeight = new PersonState(broken.name(), broken.eyeColor(), broken.hairColor(), "0",
                broken.nationality(), broken.coordX(), broken.coordY(), broken.locX(), broken.locY(), broken.locZ());
        PersonChange tampered = f.log.changes.save(new PersonChange(anna.getId(), ChangeAction.UPDATED,
                f.alice, f.alice, broken.toJson(), zeroHeight.toJson(), null));
        f.as(f.admin);

        ApiException e = rollbackFails(tampered.getId(), RollbackTarget.AFTER);

        assertEquals(400, e.getStatus());
        assertEquals("height", e.getField());
        assertEquals(170f, anna.getHeight());
    }

    @Test
    void onlyAdminMayRollback() {
        PersonChange bad = badEdit();

        f.as(f.alice); // владелец
        assertEquals(403, rollbackFails(bad.getId(), RollbackTarget.BEFORE).getStatus());
        f.as(f.bob); // посторонний
        assertEquals(403, rollbackFails(bad.getId(), RollbackTarget.BEFORE).getStatus());

        assertEquals("Нецензурное имя", anna.getName());
    }

    @Test
    void unknownOrForeignJournalEntryIs404() {
        Person boris = f.create(f.bob, "Борис");
        PersonChange borisEntry = lastEntry();
        f.as(f.admin);

        assertEquals(404, rollbackFails(999, RollbackTarget.BEFORE).getStatus());
        ApiException foreign = rollbackFails(borisEntry.getId(), RollbackTarget.AFTER);
        assertEquals(404, foreign.getStatus(), "запись относится к другому объекту: " + boris.getId());
    }

    @Test
    void creationEntryHasNoStateBefore() {
        f.as(f.admin);
        PersonChange creation = entries().get(0);

        ApiException e = rollbackFails(creation.getId(), RollbackTarget.BEFORE);

        assertEquals(400, e.getStatus());
        assertTrue(e.getMessage().contains("нет состояния «до изменения»"), e.getMessage());
    }

    @Test
    void rollbackToTheCurrentStateIsRejected() {
        PersonChange bad = badEdit();
        f.as(f.admin);

        ApiException e = rollbackFails(bad.getId(), RollbackTarget.AFTER); // текущее состояние и есть «после»

        assertEquals(400, e.getStatus());
        assertEquals("Объект уже находится в этом состоянии", e.getMessage());
    }

    @Test
    void deletedObjectCannotBeRolledBack() {
        PersonChange creation = entries().get(0);
        f.as(f.alice);
        f.service.delete(anna.getId(), null, null, anna.getVersion());
        f.as(f.admin);

        assertEquals(404, rollbackFails(creation.getId(), RollbackTarget.AFTER).getStatus());
    }
}
