package itmo.is1.rest;

import itmo.is1.dto.DecimalParser;
import itmo.is1.dto.PersonResponse;
import itmo.is1.entity.Color;
import itmo.is1.entity.Person;
import itmo.is1.repository.PersonRepository;
import itmo.is1.security.CurrentUser;
import itmo.is1.security.Secured;
import itmo.is1.service.PersonService;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RequestScoped
@Secured
@Path("/stats")
@Produces(MediaType.APPLICATION_JSON)
public class StatsResource {

    @Inject
    PersonRepository repository;

    @Inject
    CurrentUser currentUser;

    @Inject
    PersonService service;

    @GET
    @Path("/height-sum")
    public Map<String, String> heightSum() {
        return Map.of("sum", DecimalParser.format(totalHeight()));
    }

    @GET
    @Path("/min-height")
    public Response minHeight() {
        return minHeight(repository.findAll())
                .map(p -> Response.ok(PersonResponse.of(p, currentUser.getUsername(), service.isCurrentUserAdmin()))
                        .build())
                .orElseGet(() -> Response.status(Response.Status.NOT_FOUND)
                        .entity(Map.of("message", "В системе нет ни одного объекта"))
                        .build());
    }

    @GET
    @Path("/shorter-than")
    public List<PersonResponse> shorterThan(@QueryParam("height") String height) {
        float threshold =
                DecimalParser.parseFloat(height, "height", "Пороговое значение роста");
        boolean admin = service.isCurrentUserAdmin();
        return shorterThan(threshold).stream()
                .map(p -> PersonResponse.of(p, currentUser.getUsername(), admin))
                .toList();
    }

    @GET
    @Path("/hair-count/{color}")
    public Map<String, Object> hairCount(@PathParam("color") Color color) {
        return Map.of("color", color, "count", countByHairColor(color));
    }

    @GET
    @Path("/eye-share/{color}")
    public Map<String, Object> eyeShare(@PathParam("color") Color color) {
        return Map.of("color", color, "percentage",
                DecimalParser.format(eyeColorShare(color)));
    }

    double totalHeight() {
        return repository.findAll().stream()
                .mapToDouble(Person::getHeight)
                .sum();
    }

    Optional<Person> minHeight(List<Person> people) {
        return people.stream()
                .min(Comparator.comparing(Person::getHeight));
    }

    List<Person> shorterThan(float threshold) {
        return repository.findAll().stream()
                .filter(p -> p.getHeight() < threshold)
                .toList();
    }

    long countByHairColor(Color hairColor) {
        return repository.findAll().stream()
                .filter(p -> p.getHairColor() == hairColor)
                .count();
    }

    double eyeColorShare(Color eyeColor) {
        List<Person> all = repository.findAll();
        if (all.isEmpty()) {
            return 0.0;
        }
        long matching = all.stream()
                .filter(p -> p.getEyeColor() == eyeColor)
                .count();
        return matching * 100.0 / all.size();
    }
}
