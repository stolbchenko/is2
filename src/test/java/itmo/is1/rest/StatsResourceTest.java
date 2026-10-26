package itmo.is1.rest;

import itmo.is1.entity.Color;
import itmo.is1.entity.Person;
import itmo.is1.repository.PersonRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StatsResourceTest {

    private StatsResource resource;
    private StubRepository repository;

    @BeforeEach
    void setUp() {
        repository = new StubRepository();
        resource = new StatsResource();
        resource.repository = repository;
    }

    @Test
    void sumsHeights() {
        repository.people = List.of(person(180f, Color.BLACK, Color.BLUE),
                person(160f, Color.BLUE, Color.BLACK));
        assertEquals(340.0, resource.totalHeight(), 0.001);
    }

    @Test
    void findsMinimumHeight() {
        repository.people = List.of(person(180f, Color.BLACK, Color.BLUE),
                person(155f, Color.BLUE, Color.BLACK));
        assertEquals(155f, resource.minHeight(repository.people).orElseThrow().getHeight());
    }

    @Test
    void minimumOnEmptyPopulationIsEmpty() {
        repository.people = List.of();
        assertTrue(resource.minHeight(repository.people).isEmpty());
    }

    @Test
    void filtersShorterThanThreshold() {
        repository.people = List.of(person(180f, Color.BLACK, Color.BLUE),
                person(155f, Color.BLUE, Color.BLACK),
                person(170f, Color.BLACK, Color.BLACK));
        assertEquals(2, resource.shorterThan(175f).size());
    }

    @Test
    void countsByHairColor() {
        repository.people = List.of(person(180f, Color.BLACK, Color.BLUE),
                person(155f, Color.BLACK, Color.BLACK),
                person(170f, Color.ORANGE, Color.BLACK));
        assertEquals(2, resource.countByHairColor(Color.BLACK));
    }

    @Test
    void computesEyeColorShare() {
        repository.people = List.of(person(180f, Color.BLACK, Color.BLUE),
                person(155f, Color.BLACK, Color.BLUE),
                person(170f, Color.BLACK, Color.YELLOW),
                person(165f, Color.BLACK, Color.YELLOW));
        assertEquals(50.0, resource.eyeColorShare(Color.BLUE), 0.001);
    }

    @Test
    void eyeColorShareOnEmptyPopulationIsZero() {
        repository.people = List.of();
        assertEquals(0.0, resource.eyeColorShare(Color.BLUE), 0.001);
    }

    private Person person(float height, Color hair, Color eye) {
        Person person = new Person();
        person.setHeight(height);
        person.setHairColor(hair);
        person.setEyeColor(eye);
        return person;
    }

    private static class StubRepository extends PersonRepository {
        private List<Person> people = List.of();

        @Override
        public List<Person> findAll() {
            return people;
        }
    }
}
