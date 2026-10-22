package team.ylab.spikes.s2.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

public class DebeziumConnectClient {

  private final String baseUrl;
  private final HttpClient httpClient;
  private final ObjectMapper objectMapper;

  public DebeziumConnectClient(String host, int port) {
    this.baseUrl = "http://" + host + ":" + port;
    this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    this.objectMapper = new ObjectMapper();
  }

  public void registerConnector(String name, Map<String, Object> config) throws Exception {
    Map<String, Object> payload = Map.of("name", name, "config", config);
    String body = objectMapper.writeValueAsString(payload);
    HttpRequest request =
        HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + "/connectors"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();

    HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    if (response.statusCode() != 201 && response.statusCode() != 409) {
      throw new IllegalStateException(
          "Failed to register connector: " + response.statusCode() + " - " + response.body());
    }
  }

  public boolean isConnectorRunning(String name) {
    try {
      HttpRequest request =
          HttpRequest.newBuilder()
              .uri(URI.create(baseUrl + "/connectors/" + name + "/status"))
              .GET()
              .build();
      HttpResponse<String> response =
          httpClient.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() != 200) {
        return false;
      }
      JsonNode node = objectMapper.readTree(response.body());
      String connectorState = node.path("connector").path("state").asText();
      JsonNode tasks = node.path("tasks");
      boolean allTasksRunning = tasks.isArray() && !tasks.isEmpty();
      for (JsonNode task : tasks) {
        if (!"RUNNING".equalsIgnoreCase(task.path("state").asText())) {
          allTasksRunning = false;
          break;
        }
      }
      return "RUNNING".equalsIgnoreCase(connectorState) && allTasksRunning;
    } catch (Exception e) {
      return false;
    }
  }

  public void pauseConnector(String name) throws Exception {
    HttpRequest request =
        HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + "/connectors/" + name + "/pause"))
            .PUT(HttpRequest.BodyPublishers.noBody())
            .build();
    HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    if (response.statusCode() != 202) {
      throw new IllegalStateException(
          "Failed to pause connector: " + response.statusCode() + " - " + response.body());
    }
  }

  public void resumeConnector(String name) throws Exception {
    HttpRequest request =
        HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + "/connectors/" + name + "/resume"))
            .PUT(HttpRequest.BodyPublishers.noBody())
            .build();
    HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    if (response.statusCode() != 202) {
      throw new IllegalStateException(
          "Failed to resume connector: " + response.statusCode() + " - " + response.body());
    }
  }

  public void deleteConnector(String name) throws Exception {
    HttpRequest request =
        HttpRequest.newBuilder().uri(URI.create(baseUrl + "/connectors/" + name)).DELETE().build();
    httpClient.send(request, HttpResponse.BodyHandlers.ofString());
  }
}
