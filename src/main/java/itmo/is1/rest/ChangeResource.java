package itmo.is1.rest;

import itmo.is1.dto.ChangeHistoryResponse;
import itmo.is1.dto.PersonChangeResponse;
import itmo.is1.security.Secured;
import itmo.is1.service.ChangeLogService;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

import java.util.List;

@RequestScoped
@Secured
@Path("/changes")
@Produces(MediaType.APPLICATION_JSON)
public class ChangeResource {

    @Inject
    ChangeLogService service;

    @GET
    public ChangeHistoryResponse history(@QueryParam("personId") Integer personId,
                                         @QueryParam("page") @DefaultValue("0") int page,
                                         @QueryParam("size") @DefaultValue("10") int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        List<PersonChangeResponse> items = service.history(personId, safePage, safeSize).stream()
                .map(PersonChangeResponse::of)
                .toList();
        return new ChangeHistoryResponse(items, service.historyTotal(personId), safePage, safeSize,
                service.isCurrentUserAdmin());
    }
}
