package itmo.is1.rest;

import itmo.is1.service.AuthService;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.util.Map;

@RequestScoped
@Path("/auth")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AuthResource {

    @Inject
    AuthService authService;

    public record Credentials(String username, String password) {
    }

    @POST
    @Path("/register")
    public Map<String, String> register(Credentials credentials) {
        String token = authService.register(credentials.username(), credentials.password());
        return Map.of("token", token, "username", credentials.username());
    }

    @POST
    @Path("/login")
    public Map<String, String> login(Credentials credentials) {
        String token = authService.login(credentials.username(), credentials.password());
        return Map.of("token", token, "username", credentials.username());
    }
}
