package itmo.is1.rest;

import itmo.is1.dto.ImportHistoryResponse;
import itmo.is1.dto.ImportOperationResponse;
import itmo.is1.security.Secured;
import itmo.is1.service.ImportService;
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
@Path("/imports")
@Produces(MediaType.APPLICATION_JSON)
public class ImportResource {

    @Inject
    ImportService service;

    @GET
    public ImportHistoryResponse history(@QueryParam("page") @DefaultValue("0") int page,
                                         @QueryParam("size") @DefaultValue("10") int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        List<ImportOperationResponse> items = service.history(safePage, safeSize).stream()
                .map(ImportOperationResponse::of)
                .toList();
        return new ImportHistoryResponse(items, service.historyTotal(), safePage, safeSize,
                service.isCurrentUserAdmin());
    }
}
