package itmo.is1.rest;

import itmo.is1.service.ImportException;
import jakarta.enterprise.context.RequestScoped;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import java.util.Map;

@RequestScoped
@Provider
public class ImportExceptionMapper implements ExceptionMapper<ImportException> {

    @Override
    public Response toResponse(ImportException e) {
        return Response.status(Response.Status.BAD_REQUEST)
                .type(MediaType.APPLICATION_JSON)
                .entity(Map.of(
                        "message", e.getMessage(),
                        "errors", e.getErrors()))
                .build();
    }
}
