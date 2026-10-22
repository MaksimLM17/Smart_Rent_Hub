package team.ylab.gateway.websocket;

import java.util.Collections;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.HandlerMapping;
import org.springframework.web.reactive.handler.SimpleUrlHandlerMapping;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.server.support.WebSocketHandlerAdapter;

/**
 * Configures WebSocket handling and URL mapping for Gateway BFF endpoints (SDD v1.1 Section 4.1,
 * Appendix B).
 *
 * <p>Maps {@code /ws/echo} to {@link EchoWebSocketHandler} with order {@code -2} (preceding Gateway
 * route matching), adapted via {@link WebSocketHandlerAdapter}.
 */
@Configuration
public class GatewayWebSocketConfig {

  @Bean
  public HandlerMapping webSocketHandlerMapping(EchoWebSocketHandler echoWebSocketHandler) {
    SimpleUrlHandlerMapping mapping = new SimpleUrlHandlerMapping();
    mapping.setUrlMap(
        Collections.singletonMap("/ws/echo", (WebSocketHandler) echoWebSocketHandler));
    // Order before Gateway RoutePredicateHandlerMapping (default order 1)
    mapping.setOrder(-2);
    return mapping;
  }

  @Bean
  public WebSocketHandlerAdapter webSocketHandlerAdapter() {
    return new WebSocketHandlerAdapter();
  }
}
