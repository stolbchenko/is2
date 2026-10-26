package itmo.is1.service;

import itmo.is1.entity.PersonChange;
import itmo.is1.entity.User;
import itmo.is1.repository.PersonChangeRepository;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Журнал изменений в памяти: те же правила выборки, что у JPQL-запросов репозитория. */
class InMemoryPersonChangeRepository extends PersonChangeRepository {

    final List<PersonChange> saved = new ArrayList<>();

    @Override
    public PersonChange save(PersonChange change) {
        InMemoryPersonRepository.setField(change, "id", saved.size() + 1);
        saved.add(change);
        return change;
    }

    @Override
    public PersonChange find(Integer id) {
        return saved.stream().filter(c -> c.getId().equals(id)).findFirst().orElse(null);
    }

    private List<PersonChange> matching(User owner, Integer personId) {
        return saved.stream()
                .filter(c -> owner == null || c.getOwner() == owner)
                .filter(c -> personId == null || c.getPersonId().equals(personId))
                .sorted(Comparator.comparing(PersonChange::getId).reversed())
                .toList();
    }

    @Override
    public List<PersonChange> findPage(User owner, Integer personId, int offset, int limit) {
        List<PersonChange> all = matching(owner, personId);
        return all.subList(Math.min(offset, all.size()), Math.min(offset + limit, all.size()));
    }

    @Override
    public long count(User owner, Integer personId) {
        return matching(owner, personId).size();
    }

    /** Записи журнала по порядку создания (в {@link #findPage} новые идут первыми). */
    List<PersonChange> inOrder() {
        return List.copyOf(saved);
    }
}
