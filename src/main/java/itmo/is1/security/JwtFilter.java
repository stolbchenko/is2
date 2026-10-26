package itmo.is1.security;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;

import java.util.Map;

@RequestScoped
@Secured
@Provider
@Priority(Priorities.AUTHENTICATION)
public class JwtFilter implements ContainerRequestFilter {

    @Inject
    JwtService jwtService;

    @Inject
    CurrentUser currentUser;

    @Override
    public void filter(ContainerRequestContext context) {
        String header = context.getHeaderString(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith("Bearer ")) {
            abort(context, "Требуется авторизация");
            return;
        }

        try {
            String username = jwtService.verifyAndGetUsername(header.substring(7));
            context.setProperty("username", username);
            currentUser.setUsername(username);
        } catch (Exception e) {
            abort(context, "Недействительный или истёкший токен");
        }
    }

    private void abort(ContainerRequestContext context, String message) {
        context.abortWith(Response.status(Response.Status.UNAUTHORIZED)
                .entity(Map.of("message", message))
                .build());
    }
}
