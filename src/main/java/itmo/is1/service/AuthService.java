package itmo.is1.service;

import itmo.is1.entity.User;
import itmo.is1.repository.PersonRepository;
import itmo.is1.security.JwtService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.mindrot.jbcrypt.BCrypt;

@ApplicationScoped
public class AuthService {

    @Inject
    PersonRepository repository;

    @Inject
    JwtService jwtService;

    @Inject
    TransactionRunner transactions;

    public String register(String username, String password) {
        if (username == null || username.isBlank()) {
            throw ApiException.validation("username", "Имя пользователя не может быть пустым");
        }
        if (password == null || password.length() < 6) {
            throw ApiException.validation("password", "Пароль должен быть не короче 6 символов");
        }
        String passwordHash = BCrypt.hashpw(password, BCrypt.gensalt());

        // SERIALIZABLE: при одновременной регистрации одного имени второй получит «уже существует», а не 500 от UNIQUE
        transactions.serializable(() -> {
            if (repository.findUserByUsername(username) != null) {
                throw ApiException.validation("username", "Пользователь с таким именем уже существует");
            }
            User user = new User();
            user.setUsername(username);
            user.setPasswordHash(passwordHash);
            repository.saveUser(user);
        });

        return jwtService.issue(username);
    }

    public String login(String username, String password) {
        User user = repository.findUserByUsername(username);
        if (user == null || !BCrypt.checkpw(password, user.getPasswordHash())) {
            throw ApiException.validation("credentials", "Неверное имя пользователя или пароль");
        }
        return jwtService.issue(username);
    }
}
