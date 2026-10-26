package itmo.is1.service;

import itmo.is1.security.CurrentUser;

class TestChangeLog {

    final InMemoryPersonChangeRepository changes = new InMemoryPersonChangeRepository();
    final ChangeLogService service = new ChangeLogService();

    TestChangeLog(InMemoryPersonRepository repository, CurrentUser currentUser) {
        service.changes = changes;
        service.personRepository = repository;
        service.currentUser = currentUser;
    }
}
