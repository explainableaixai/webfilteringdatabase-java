package com.alphaquantum.webfilteringdatabase;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

/** Client for the Web Filtering Database API. Instances are immutable and thread-safe. */
public final class WebFilteringDatabaseClient {
  private static final String DEFAULT_BASE_URL = "https://www.webfilteringdatabase.com/api";
  private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {};

  private final String apiKey;
  private final String baseUrl;
  private final HttpClient http;
  private final Duration timeout;
  private final ObjectMapper json = new ObjectMapper();

  /**
   * Creates a client with the default endpoint and a 30 second request timeout.
   *
   * @param apiKey the API key; must not be blank
   */
  public WebFilteringDatabaseClient(String apiKey) {
    this(apiKey, DEFAULT_BASE_URL, defaultHttpClient(), Duration.ofSeconds(30));
  }

  private WebFilteringDatabaseClient(String apiKey, String baseUrl, HttpClient http, Duration timeout) {
    if (apiKey == null || apiKey.isBlank()) {
      throw new IllegalArgumentException("API key is required");
    }
    this.apiKey = apiKey;
    this.baseUrl = baseUrl.replaceAll("/+$", "");
    this.http = http;
    this.timeout = timeout;
  }

  /** @return a builder for custom endpoints, HTTP clients or timeouts */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * Classifies one domain or URL and returns the decoded JSON response.
   *
   * @param value the value to look up; must not be blank
   * @return the JSON response as a map
   * @throws ApiException if the service answers with an HTTP error status
   * @throws IOException on network errors, timeouts or invalid JSON
   * @throws InterruptedException if the calling thread is interrupted
   */
  public Map<String, Object> classify(String value) throws IOException, InterruptedException {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("Input is required");
    }
    String form =
        "query=" + enc(value) + "&data_type=url&api_key=" + enc(apiKey);
    HttpRequest request =
        HttpRequest.newBuilder(URI.create(baseUrl + "/moderate.php"))
            .timeout(timeout)
            .header("Content-Type", "application/x-www-form-urlencoded")
            .header("Accept", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(form))
            .build();
    HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
    if (response.statusCode() >= 400) {
      throw new ApiException(response.statusCode(), response.body());
    }
    return json.readValue(response.body(), MAP);
  }

  private static String enc(String s) {
    return URLEncoder.encode(s, StandardCharsets.UTF_8);
  }

  private static HttpClient defaultHttpClient() {
    return HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
  }

  /** Builder for WebFilteringDatabaseClient. */
  public static final class Builder {
    private String apiKey;
    private String baseUrl = DEFAULT_BASE_URL;
    private HttpClient http;
    private Duration timeout = Duration.ofSeconds(30);

    private Builder() {}

    /** @param value the API key */
    public Builder apiKey(String value) {
      apiKey = value;
      return this;
    }

    /** @param value base URL of the API, for example a test server */
    public Builder baseUrl(String value) {
      baseUrl = value;
      return this;
    }

    /** @param value a shared or custom {@link HttpClient} */
    public Builder httpClient(HttpClient value) {
      http = value;
      return this;
    }

    /** @param value per-request timeout */
    public Builder timeout(Duration value) {
      timeout = value;
      return this;
    }

    /** @return a configured client */
    public WebFilteringDatabaseClient build() {
      return new WebFilteringDatabaseClient(apiKey, baseUrl, http == null ? defaultHttpClient() : http, timeout);
    }
  }
}
