package com.alphaquantum.webfilteringdatabase;

import static org.junit.jupiter.api.Assertions.*;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class WebFilteringDatabaseClientTest {
  private HttpServer server;

  private WebFilteringDatabaseClient stub(int status, String body) throws Exception {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/",
        ex -> {
          try {
            if (status < 400) {
                String form = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                assertTrue(form.contains("api_key=test-key"));
                assertTrue(form.contains("query=example.com"));
            }
            byte[] out = body.getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(status, out.length);
            ex.getResponseBody().write(out);
          } catch (AssertionError e) {
            ex.sendResponseHeaders(500, -1);
          } finally {
            ex.close();
          }
        });
    server.start();
    return WebFilteringDatabaseClient.builder()
        .apiKey("test-key")
        .baseUrl("http://127.0.0.1:" + server.getAddress().getPort())
        .timeout(Duration.ofSeconds(5))
        .build();
  }

  @AfterEach
  void stop() {
    if (server != null) {
      server.stop(0);
    }
  }

  @Test
  void rejectsEmptyKey() {
    assertThrows(IllegalArgumentException.class, () -> new WebFilteringDatabaseClient(""));
  }

  @Test
  void sendsKeyAndParsesJson() throws Exception {
    Map<String, Object> r = stub(200, "{\"ok\":true}").classify("example.com");
    assertEquals(Boolean.TRUE, r.get("ok"));
  }

  @Test
  void httpErrorBecomesApiException() throws Exception {
    ApiException e =
        assertThrows(ApiException.class, () -> stub(429, "slow down").classify("example.com"));
    assertEquals(429, e.getStatusCode());
    assertEquals("slow down", e.getBody());
  }
}
