package itmo.is1.rest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import jakarta.enterprise.context.RequestScoped;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import java.util.Map;

@RequestScoped
@Provider
public class JsonProcessingExceptionMapper implements ExceptionMapper<JsonProcessingException> {

    @Override
    public Response toResponse(JsonProcessingException e) {
        if (!(e instanceof JsonMappingException mappingException)) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .type(MediaType.APPLICATION_JSON)
                    .entity(Map.of("field", "", "message", "Тело запроса не является корректным JSON"))
                    .build();
        }
        return toResponse(mappingException);
    }

    private Response toResponse(JsonMappingException e) {
        String field = fieldName(e);
        String message = describe(e, field);

        return Response.status(Response.Status.BAD_REQUEST)
                .type(MediaType.APPLICATION_JSON)
                .entity(Map.of("field", field == null ? "" : field, "message", message))
                .build();
    }

    private String describe(JsonMappingException e, String field) {
        if (e instanceof UnrecognizedPropertyException) {
            return "Неизвестное поле «" + field + "»";
        }
        if (e instanceof InvalidFormatException invalidFormat) {
            return "Поле «" + field + "»: значение «" + invalidFormat.getValue()
                    + "» недопустимо";
        }
        return field == null
                ? "Тело запроса не удалось разобрать"
                : "Некорректное значение поля «" + field + "»";
    }

    private String fieldName(JsonMappingException e) {
        if (e.getPath().isEmpty()) {
            return null;
        }
        return e.getPath().get(e.getPath().size() - 1).getFieldName();
    }
}
