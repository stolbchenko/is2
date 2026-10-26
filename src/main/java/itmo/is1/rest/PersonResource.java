package itmo.is1.rest;

import itmo.is1.dto.PageResponse;
import itmo.is1.dto.PersonImportRecord;
import itmo.is1.dto.PersonRequest;
import itmo.is1.dto.PersonResponse;
import itmo.is1.dto.PersonResponse.CoordinatesResponse;
import itmo.is1.dto.PersonResponse.LocationResponse;
import itmo.is1.entity.Color;
import itmo.is1.entity.Country;
import itmo.is1.entity.ImportOperation;
import itmo.is1.repository.PersonFilter;
import itmo.is1.repository.PersonRepository;
import itmo.is1.security.CurrentUser;
import itmo.is1.security.Secured;
import itmo.is1.service.ApiException;
import itmo.is1.service.ImportException;
import itmo.is1.service.ImportService;
import itmo.is1.service.PersonImportParser;
import itmo.is1.service.PersonService;
import itmo.is1.service.RollbackTarget;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.EntityPart;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

@RequestScoped
@Secured
@Path("/persons")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class PersonResource {

    private static final int MAX_IMPORT_BYTES = 5 * 1024 * 1024;
    private static final Logger LOG = Logger.getLogger(PersonResource.class.getName());

    @Inject
    PersonService service;

    @Inject
    ImportService importService;

    @Inject
    PersonRepository repository;

    @Inject
    CurrentUser currentUser;

    @GET
    public PageResponse<PersonResponse> list(
            @QueryParam("page") @jakarta.ws.rs.DefaultValue("0") int page,
            @QueryParam("size") @jakarta.ws.rs.DefaultValue("10") int size,
            @QueryParam("sort") @jakarta.ws.rs.DefaultValue("id") String sort,
            @QueryParam("order") @jakarta.ws.rs.DefaultValue("asc") String order,
            @QueryParam("name") String nameFilter,
            @QueryParam("eyeColor") Color eyeColor,
            @QueryParam("hairColor") Color hairColor,
            @QueryParam("nationality") Country nationality) {

        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        boolean ascending = !"desc".equalsIgnoreCase(order);
        PersonFilter filter = new PersonFilter(nameFilter, eyeColor, hairColor, nationality);

        boolean admin = service.isCurrentUserAdmin();
        List<PersonResponse> items = service.page(filter, sort, ascending, safePage, safeSize)
                .stream()
                .map(p -> PersonResponse.of(p, currentUser.getUsername(), admin))
                .toList();

        return new PageResponse<>(items, service.total(filter), safePage, safeSize);
    }

    @GET
    @Path("/{id}")
    public PersonResponse byId(@PathParam("id") Integer id) {
        return PersonResponse.of(service.getById(id), currentUser.getUsername(), service.isCurrentUserAdmin());
    }

    @POST
    public Response create(@Valid PersonRequest request) {
        PersonResponse created =
                PersonResponse.of(service.create(request), currentUser.getUsername(), service.isCurrentUserAdmin());
        return Response.status(Response.Status.CREATED).entity(created).build();
    }

    @PUT
    @Path("/{id}")
    public PersonResponse update(@PathParam("id") Integer id, @Valid PersonRequest request) {
        return PersonResponse.of(service.update(id, request), currentUser.getUsername(),
                service.isCurrentUserAdmin());
    }

    public record RollbackRequest(Integer changeId, RollbackTarget to) {
    }

    @POST
    @Path("/{id}/rollback")
    public PersonResponse rollback(@PathParam("id") Integer id, RollbackRequest request) {
        if (request == null || request.changeId() == null || request.to() == null) {
            throw ApiException.validation("changeId",
                    "Укажите запись журнала (changeId) и состояние (to: BEFORE или AFTER)");
        }
        // откат разрешён только администратору, поэтому флаг editable всегда true
        return PersonResponse.of(service.rollback(id, request.changeId(), request.to()),
                currentUser.getUsername(), true);
    }

    @DELETE
    @Path("/{id}")
    public Response delete(@PathParam("id") Integer id,
                           @QueryParam("reassignCoordinatesTo") Integer reassignCoordinatesTo,
                           @QueryParam("reassignLocationTo") Integer reassignLocationTo,
                           @QueryParam("version") Integer version) {
        service.delete(id, reassignCoordinatesTo, reassignLocationTo, version);
        return Response.noContent().build();
    }

    @POST
    @Path("/import")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public Map<String, Object> importFile(List<EntityPart> parts) {
        ZonedDateTime startedAt = ZonedDateTime.now();
        String fileName = null;
        try {
            EntityPart file = parts.stream()
                    .filter(part -> "file".equals(part.getName()))
                    .findFirst()
                    .orElseThrow(() -> new ImportException("Файл для импорта не передан"));
            fileName = file.getFileName().orElse(null);
            ImportOperation operation =
                    importService.importRecords(readRecords(file), fileName, startedAt);
            return Map.of("operationId", operation.getId(), "imported", operation.getAddedCount());
        } catch (ImportException e) {
            recordFailure(e, fileName, startedAt);
            throw e;
        } catch (ApiException e) {
            recordFailure(new ImportException(e.getMessage()), fileName, startedAt);
            throw e;
        } catch (RuntimeException e) {
            ImportException failure = new ImportException(
                    "Импорт отменён из-за ошибки при сохранении в базу данных, ни один объект не создан",
                    List.of(rootMessage(e)));
            recordFailure(failure, fileName, startedAt);
            throw failure;
        }
    }

    private List<PersonImportRecord> readRecords(EntityPart file) {
        try (InputStream content = file.getContent()) {
            byte[] bytes = content.readNBytes(MAX_IMPORT_BYTES + 1);
            if (bytes.length > MAX_IMPORT_BYTES) {
                throw new ImportException("Файл слишком большой: допускается не более "
                        + MAX_IMPORT_BYTES / (1024 * 1024) + " МБ");
            }
            return PersonImportParser.parse(new ByteArrayInputStream(bytes));
        } catch (IOException e) {
            throw new ImportException("Не удалось прочитать загруженный файл");
        }
    }

    /** Сбой записи в историю не должен подменять клиенту настоящую ошибку импорта. */
    private void recordFailure(ImportException error, String fileName, ZonedDateTime startedAt) {
        try {
            importService.recordFailure(error, fileName, startedAt);
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "Не удалось записать неудачную операцию импорта в историю", e);
        }
    }

    private static String rootMessage(Throwable e) {
        Throwable root = e;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return root.getMessage() == null ? root.getClass().getSimpleName() : root.getMessage();
    }

    @GET
    @Path("/coordinates")
    public List<CoordinatesResponse> coordinates() {
        return repository.allCoordinates().stream().map(CoordinatesResponse::of).toList();
    }

    @GET
    @Path("/locations")
    public List<LocationResponse> locations() {
        return repository.allLocations().stream().map(LocationResponse::of).toList();
    }
}
