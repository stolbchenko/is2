package itmo.is1.service;

import itmo.is1.dto.PersonImportRecord;
import itmo.is1.entity.ImportOperation;
import itmo.is1.entity.Person;
import itmo.is1.entity.User;
import itmo.is1.repository.ImportOperationRepository;
import itmo.is1.repository.PersonRepository;
import itmo.is1.security.CurrentUser;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.transaction.Transactional.TxType;

import java.time.ZonedDateTime;
import java.util.List;

@ApplicationScoped
public class ImportService {

    private static final int MAX_ERROR_LENGTH = 4000;

    @Inject
    PersonService personService;

    @Inject
    PersonRepository personRepository;

    @Inject
    ImportOperationRepository operations;

    @Inject
    CurrentUser currentUser;

    @Inject
    TransactionRunner transactions;

    @Inject
    Event<PersonChangedEvent> changes;

    /** Объекты и запись SUCCESS создаются в одной транзакции: в истории успех появится только вместе с объектами. */
    public ImportOperation importRecords(List<PersonImportRecord> records, String fileName,
                                         ZonedDateTime startedAt) {
        return transactions.serializable(() -> {
            List<Person> created = personService.importAll(records);
            return operations.save(
                    ImportOperation.success(requireCurrentUser(), created.size(), fileName, startedAt));
        });
    }

    /** Отдельная транзакция: транзакция импорта уже откачена, и запись пропала бы вместе с объектами. */
    @Transactional(TxType.REQUIRES_NEW)
    public ImportOperation recordFailure(ImportException error, String fileName, ZonedDateTime startedAt) {
        ImportOperation operation = operations.save(
                ImportOperation.failure(requireCurrentUser(), describe(error), fileName, startedAt));
        changes.fire(PersonChangedEvent.importFailed());
        return operation;
    }

    /** Обычный пользователь видит только свои операции, администратор — все. */
    public List<ImportOperation> history(int page, int size) {
        return operations.findPage(visibleOwner(), page * size, size);
    }

    public long historyTotal() {
        return operations.count(visibleOwner());
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

    static String describe(ImportException error) {
        StringBuilder text = new StringBuilder(error.getMessage());
        for (String detail : error.getErrors()) {
            text.append('\n').append(detail);
        }
        return text.length() <= MAX_ERROR_LENGTH
                ? text.toString()
                : text.substring(0, MAX_ERROR_LENGTH - 1) + "…";
    }
}
