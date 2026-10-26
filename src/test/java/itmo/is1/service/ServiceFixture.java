package itmo.is1.service;

import itmo.is1.dto.PersonRequest;
import itmo.is1.dto.PersonRequest.CoordinatesRequest;
import itmo.is1.dto.PersonRequest.LocationRequest;
import itmo.is1.entity.Color;
import itmo.is1.entity.Person;
import itmo.is1.entity.Role;
import itmo.is1.entity.User;
import itmo.is1.security.CurrentUser;

class ServiceFixture {

    final InMemoryPersonRepository repository = new InMemoryPersonRepository();
    final CurrentUser currentUser = new CurrentUser();
    final PersonServiceUniquenessTest.RecordingEvent events = new PersonServiceUniquenessTest.RecordingEvent();
    final TestChangeLog log = new TestChangeLog(repository, currentUser);
    final PersonService service = new PersonService();
    final User alice = repository.addUser("alice");
    final User bob = repository.addUser("bob");
    final User admin = repository.addUser("admin");
    private int sequence;

    ServiceFixture() {
        InMemoryPersonRepository.setField(admin, "role", Role.ADMIN);
        UniquenessRules rules = new UniquenessRules();
        rules.repository = repository;
        service.repository = repository;
        service.currentUser = currentUser;
        service.changes = events;
        service.uniqueness = rules;
        service.transactions = new DirectTransactionRunner();
        service.journal = log.service;
    }

    ChangeLogService journal() {
        return log.service;
    }

    void as(User user) {
        currentUser.setUsername(user.getUsername());
    }

    /** Запрос на создание с новыми, ни с чем не совпадающими координатами и локацией. */
    PersonRequest newPerson(String name) {
        String n = String.valueOf(++sequence);
        return new PersonRequest(name, null, new CoordinatesRequest(n, "1"), null, Color.BLACK,
                null, new LocationRequest(n, "1", "1"), "170", null, null);
    }

    Person create(User user, String name) {
        as(user);
        return service.create(newPerson(name));
    }

    /** Запрос на изменение текущей версии объекта, оставляющий те же координаты и локацию. */
    static PersonRequest withSame(Person person, String name, String height) {
        return new PersonRequest(name, person.getCoordinates().getId(), null, null, Color.BLACK,
                person.getLocation().getId(), null, height, null, person.getVersion());
    }
}
