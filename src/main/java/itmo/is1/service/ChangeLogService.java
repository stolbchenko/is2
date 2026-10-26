package itmo.is1.service;

import itmo.is1.dto.PersonState;
import itmo.is1.entity.ChangeAction;
import itmo.is1.entity.Person;
import itmo.is1.entity.PersonChange;
import itmo.is1.entity.User;
import itmo.is1.repository.PersonChangeRepository;
import itmo.is1.repository.PersonRepository;
import itmo.is1.security.CurrentUser;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;

/** Записи журнала пишутся в транзакции самого изменения: отменённое изменение не оставляет следа. */
@ApplicationScoped
public class ChangeLogService {

    @Inject
    PersonChangeRepository changes;

    @Inject
    PersonRepository personRepository;

    @Inject
    CurrentUser currentUser;

    public void recordCreated(Person person, String note) {
        recordCreated(person, requireCurrentUser(), note);
    }

    public void recordCreated(Person person, User actor, String note) {
        changes.save(new PersonChange(person.getId(), ChangeAction.CREATED, actor, person.getOwner(),
                null, PersonState.of(person).toJson(), note));
    }

    /** Запись изменения; если значения не изменились, журнал не пополняется. */
    public void recordChanged(ChangeAction action, Person person, PersonState before, PersonState after,
                              String note) {
        if (before.equals(after)) {
            return;
        }
        changes.save(new PersonChange(person.getId(), action, requireCurrentUser(), person.getOwner(),
                before.toJson(), after.toJson(), note));
    }

    public void recordDeleted(Integer personId, User owner, PersonState before) {
        changes.save(new PersonChange(personId, ChangeAction.DELETED, requireCurrentUser(), owner,
                before.toJson(), null, null));
    }

    public PersonChange requireChange(Integer changeId, Integer personId) {
        PersonChange change = changes.find(changeId);
        if (change == null || !change.getPersonId().equals(personId)) {
            throw ApiException.validation("changeId",
                    "Запись журнала #" + changeId + " не найдена у объекта #" + personId);
        }
        return change;
    }

    /** Пользователь видит записи по своим объектам (в том числе правки администратора), администратор — все. */
    public List<PersonChange> history(Integer personId, int page, int size) {
        return changes.findPage(visibleOwner(), personId, page * size, size);
    }

    public long historyTotal(Integer personId) {
        return changes.count(visibleOwner(), personId);
    }

    public boolean isCurrentUserAdmin() {
        return requireCurrentUser().isAdmin();
    }

    private User visibleOwner() {
        User user = requireCurrentUser();
        return user.isAdmin() ? null : user;
    }

    private User requireCurrentUser() {
        User user = personRepository.findUserByUsername(currentUser.getUsername());
        if (user == null) {
            throw ApiException.validation("owner", "Текущий пользователь не найден");
        }
        return user;
    }
}
