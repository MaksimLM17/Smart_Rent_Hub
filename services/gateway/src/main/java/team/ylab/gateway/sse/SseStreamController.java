package team.ylab.gateway.sse;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Controller providing Server-Sent Events (SSE) streaming endpoints for frontend UI applications
 * behind the Gateway BFF (SDD v1.1 Section 4.1, Appendix B).
 *
 * <p>Authenticated via session cookies, validated by {@link
 * team.ylab.gateway.security.OriginValidationWebFilter}, and supporting {@code Last-Event-ID}
 * replay for automatic client reconnection.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/sse")
public class SseStreamController {

  @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public Flux<ServerSentEvent<Map<String, Object>>> streamEvents(
      Authentication authentication,
      @RequestHeader(value = "Last-Event-ID", required = false) String lastEventId,
      @RequestParam(value = "count", defaultValue = "3") int count,
      @RequestParam(value = "delayMs", defaultValue = "50") long delayMs) {

    String username = authentication != null ? authentication.getName() : "anonymous";
    int startSeq = 1;
    if (lastEventId != null && !lastEventId.isBlank()) {
      try {
        startSeq = Integer.parseInt(lastEventId.trim()) + 1;
      } catch (NumberFormatException ignored) {
        log.warn("Invalid Last-Event-ID header value: {}", lastEventId);
      }
    }

    final int initialSeq = startSeq;
    log.debug(
        "Starting SSE stream for user {} starting from seq {}, count {}",
        username,
        initialSeq,
        count);

    return Flux.range(0, count)
        .delayElements(Duration.ofMillis(Math.max(10, delayMs)))
        .map(
            i -> {
              int seq = initialSeq + i;
              Map<String, Object> data = new LinkedHashMap<>();
              data.put("seq", seq);
              data.put("username", username);
              data.put("timestamp", Instant.now().toString());
              data.put("message", "Live event #" + seq);

              return ServerSentEvent.<Map<String, Object>>builder()
                  .id(String.valueOf(seq))
                  .event("srh.notification")
                  .data(data)
                  .build();
            });
  }

  @GetMapping(value = "/echo", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public Mono<ServerSentEvent<Map<String, Object>>> echoSse(
      Authentication authentication,
      @RequestParam(value = "message", defaultValue = "hello") String message) {

    String username = authentication != null ? authentication.getName() : "anonymous";
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("message", "echo: " + message);
    data.put("username", username);
    data.put("timestamp", Instant.now().toString());

    return Mono.just(
        ServerSentEvent.<Map<String, Object>>builder().id("1").event("echo").data(data).build());
  }
}
