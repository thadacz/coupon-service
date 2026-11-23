/// Fires many concurrent redemptions at a running service and prints how they ended.
/// A Java 25 compact source file (JEP 512): no build, no class declaration, run it directly:
///
///     java scripts/RedeemStorm.java WIOSNA 1000
///     java scripts/RedeemStorm.java WIOSNA 1000 http://localhost:8080 212.77.98.9
///
/// Every attempt uses a different user and the same client IP, so the geolocation provider
/// is asked once and the rest is served from the cache. With a coupon limit of N, the expected
/// output is exactly N x 201 and the rest 409, whatever the concurrency.

import module java.net.http;

void main(String[] args) throws Exception {
    if (args.length < 2) {
        IO.println("Usage: java scripts/RedeemStorm.java <code> <attempts> [baseUrl] [clientIp]");
        return;
    }
    String code = args[0];
    int attempts = Integer.parseInt(args[1]);
    String baseUrl = args.length > 2 ? args[2] : "http://localhost:8080";
    String clientIp = args.length > 3 ? args[3] : "212.77.98.9";

    HttpClient http = HttpClient.newHttpClient();
    URI uri = URI.create(baseUrl + "/api/v1/coupons/" + code + "/redemptions");
    Map<Integer, LongAdder> statuses = new ConcurrentSkipListMap<>();
    CountDownLatch startGate = new CountDownLatch(1);

    long started;
    try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
        for (int i = 0; i < attempts; i++) {
            String userId = "storm-" + i;
            executor.submit(() -> {
                startGate.await();
                HttpRequest request = HttpRequest.newBuilder(uri)
                        .header("Content-Type", "application/json")
                        .header("X-Forwarded-For", clientIp)
                        .POST(HttpRequest.BodyPublishers.ofString("{\"userId\":\"" + userId + "\"}"))
                        .build();
                int status = http.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
                statuses.computeIfAbsent(status, _ -> new LongAdder()).increment();
                return null;
            });
        }
        started = System.nanoTime();
        startGate.countDown();
    }
    Duration took = Duration.ofNanos(System.nanoTime() - started);

    IO.println("%d attempts in %d ms".formatted(attempts, took.toMillis()));
    statuses.forEach((status, count) -> IO.println("  HTTP %d: %d".formatted(status, count.sum())));
}
