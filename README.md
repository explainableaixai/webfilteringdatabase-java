# webfilteringdatabase (Java and Kotlin)

JVM client for assigning web filtering categories to domains. Proxies, DNS services, parental-control backends and MSP platforms running on the JVM call `classify` for hosts their local list does not know, and get a category from the [web filter lookup API](https://www.webfilteringdatabase.com/api-docs.php). Works from Java 11+ and from Kotlin.

## Dependency

```kotlin
// build.gradle.kts
implementation("io.github.explainableaixai:webfilteringdatabase:1.0.0")
```

```xml
<dependency>
  <groupId>io.github.explainableaixai</groupId>
  <artifactId>webfilteringdatabase</artifactId>
  <version>1.0.0</version>
</dependency>
```

## From Java

```java
var wf = new WebFilteringDatabaseClient(System.getenv("AQ_API_KEY"));
Map<String, Object> r = wf.classify("recently-registered.example");
```

## From Kotlin

The client blocks while it waits for the reply, so call it on the IO dispatcher:

```kotlin
val wf = WebFilteringDatabaseClient.builder()
    .apiKey(System.getenv("AQ_API_KEY"))
    .timeout(Duration.ofSeconds(10))
    .build()

suspend fun categoryOf(host: String): String? = withContext(Dispatchers.IO) {
    val r = wf.classify(host)
    if (r.containsKey("detail") || r.containsKey("error")) null
    else r["web_filtering_category"] as? String
}
```

What comes back is a plain `Map` built from the JSON: a filtering category, a confidence score and whatever else the API reference documents. One quirk to guard against: an error can arrive with status 200, so if the map has a `detail` or `error` key, do not store it as a category.

## Never classify on the hot path

A filtering decision has to be fast. A live classification takes as long as the service needs to examine the site. Keep them apart:

1. **Lookup.** Check an in-memory map loaded from the licensed file, then a cache of categories learned earlier.
2. **Miss.** Answer with the `unclassified` policy and put the host on a queue.
3. **Learn.** A background worker drains the queue a few hosts at a time, calls `classify`, and fills the cache.

With Kotlin coroutines, the worker is a `Channel<String>` read by a small, fixed number of consumers. In plain Java, a bounded `ExecutorService` fed by the request threads does the same. Track in-flight hosts in a concurrent set, so a popular new domain is classified once rather than a hundred times.

## Deciding what unclassified means

| Setting | Typical rule |
|---|---|
| Schools | Block until categorised |
| Company offices | Allow, log, review |
| Hotels and guest Wi-Fi | Allow, but block known high-risk categories |
| Kiosks and shared terminals | Block |

Very new domains show up often in phishing and fraud, so holding them briefly costs strict networks almost nothing.

## Multi-tenant platforms

Keep facts and choices apart. A domain's category is a fact, so store it once for all tenants. What each tenant does with a category is a choice, so store that per tenant. New customers start with everything the platform already knows, and one customer's rules never affect another's.

## Exceptions

- `ApiException` (unchecked) for HTTP errors. Branch on `getStatusCode()`. A 401 points at the key, a 403 at the subscription or its monthly allowance, and a 429 tells the worker to pause.
- `HttpTimeoutException` if no reply arrives within the configured time (half a minute unless you change it).
- `IOException` when the connection drops or the body cannot be parsed.
- `IllegalArgumentException` when the key or host is empty.

In the background-worker design, an error only means the host stays unclassified until someone requests it again. No separate retry logic is needed.

## Privacy

The client sends the value you pass plus your key. It sends nothing about the user or device behind the request. If you classify full URLs, strip query strings first, since they may contain tokens or personal data and the category depends on the host.

## Education and CIPA

US schools and libraries that receive E-rate funding must filter under the Children's Internet Protection Act. The adult, gambling, weapons and proxy categories cover those duties directly. Generative AI usually needs its own switch in schools, open for some lessons and closed during exams. A [district AI rollout plan](https://www.aitoolsblocklist.com) usually includes that switch, fed from the AI register.

## Visibility first

Before tightening rules, look at current behaviour. A [white-label shadow AI assessment](https://www.shadowaitools.com/white-label-shadow-ai-assessment.php) is built from logs your platform already writes. For analytics rather than enforcement, [URL database files](https://www.urlcategorizationdatabase.com/pricing.php) give you topic labels in bulk.

## Testing

Point `baseUrl` at an in-process stub. The project's tests use `com.sun.net.httpserver.HttpServer` on a random port to check that the key and query are sent in the form body, and that an HTTP 429 surfaces as `ApiException`.

Resolver authors working outside the JVM can pick up [the Rust crate](https://crates.io/crates/webfilteringdatabase) or [the Go module for DNS services](https://pkg.go.dev/github.com/explainableaixai/webfilteringdatabase-go). Parental-control apps built in Flutter have [their own client](https://pub.dev/packages/webfilteringdatabase).

## License

MIT
