package itmo.is1.service;

import itmo.is1.entity.ImportOperation;
import itmo.is1.entity.User;
import itmo.is1.repository.ImportOperationRepository;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** История импорта в памяти: те же правила выборки, что у JPQL-запросов репозитория. */
class InMemoryImportOperationRepository extends ImportOperationRepository {

    final List<ImportOperation> saved = new ArrayList<>();

    @Override
    public ImportOperation save(ImportOperation operation) {
        InMemoryPersonRepository.setId(operation, saved.size() + 1);
        saved.add(operation);
        return operation;
    }

    private List<ImportOperation> visibleTo(User user) {
        return saved.stream()
                .filter(o -> user == null || o.getUser() == user)
                .sorted(Comparator.comparing(ImportOperation::getId).reversed())
                .toList();
    }

    @Override
    public List<ImportOperation> findPage(User user, int offset, int limit) {
        List<ImportOperation> all = visibleTo(user);
        return all.subList(Math.min(offset, all.size()), Math.min(offset + limit, all.size()));
    }

    @Override
    public long count(User user) {
        return visibleTo(user).size();
    }
}
