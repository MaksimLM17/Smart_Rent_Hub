package team.ylab.gateway.websocket;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Mono;

/**
 * Built-in WebSocket echo handler for verifying full-duplex WebSocket communication through the
 * Gateway BFF with session cookie authentication and Origin validation (SDD v1.1 Section 4.1,
 * Appendix B).
 */
@Slf4j
@Component
public class EchoWebSocketHandler implements WebSocketHandler {

  @Override
  public Mono<Void> handle(WebSocketSession session) {
    log.debug("WebSocket connection opened: id={}", session.getId());
    return session
        .send(
            session
                .receive()
                .filter(msg -> msg.getType() == WebSocketMessage.Type.TEXT)
                .map(
                    msg -> {
                      String payload = msg.getPayloadAsText();
                      log.debug(
                          "WebSocket echo received: id={}, payload={}", session.getId(), payload);
                      return session.textMessage("echo: " + payload);
                    }))
        .doOnError(
            err ->
                log.error("WebSocket error on session {}: {}", session.getId(), err.getMessage()))
        .doFinally(
            signal ->
                log.debug(
                    "WebSocket connection closed: id={}, signal={}", session.getId(), signal));
  }
}
