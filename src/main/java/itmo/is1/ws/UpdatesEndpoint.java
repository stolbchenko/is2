package itmo.is1.ws;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

import itmo.is1.service.PersonChangedEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.TransactionPhase;
import jakarta.websocket.OnClose;
import jakarta.websocket.OnError;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import jakarta.websocket.server.ServerEndpoint;

@ApplicationScoped
@ServerEndpoint("/ws/updates")
public class UpdatesEndpoint {

    private static final Set<Session> SESSIONS = new CopyOnWriteArraySet<>();

    @OnOpen
    public void onOpen(Session session) {
        SESSIONS.add(session);
    }

    @OnClose
    public void onClose(Session session) {
        SESSIONS.remove(session);
    }

    @OnError
    public void onError(Session session, Throwable error) {
        SESSIONS.remove(session);
    }

    // Рассылаем только после коммита: при откате (например, неудачном импорте) клиенты не дёргаются
    public void onPersonChanged(@Observes(during = TransactionPhase.AFTER_SUCCESS) PersonChangedEvent event) {
        String payload = "{\"action\":\"%s\",\"id\":%d}".formatted(event.action(), event.personId());
        SESSIONS.stream()
                .filter(Session::isOpen)
                .forEach(session -> session.getAsyncRemote().sendText(payload));
    }
}
