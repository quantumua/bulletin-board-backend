package com.example.bulletinboard.perf;

import com.example.bulletinboard.account.UserAccount;
import com.example.bulletinboard.account.UserAccountRepository;
import com.example.bulletinboard.ad.Ad;
import com.example.bulletinboard.ad.AdRepository;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.assertj.core.api.SoftAssertions;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Locally measurable performance and capacity NFRs of u1-backend-auth (Build and Test). The class name
 * deliberately does not end in {@code Tests}, so the default {@code mvnw test} run leaves it out; run
 * it on its own with {@code -Dtest=NfrPerformanceMeasurements}.
 *
 * <p>Every measurement goes over real HTTP to the embedded Tomcat on a random port, after at least 10
 * warm-up requests, and prints one {@code NFR-MEASURE} line with the actual values. Each test asserts
 * the target from {@code construction/u1-backend-auth/nfr-requirements/}; targets are never relaxed.
 * Accounts that are only needed as data are stored directly with one precomputed BCrypt-12 hash, so the
 * seeding does not spend hours hashing; every measured log-in still runs the real BCrypt check.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.jpa.properties.hibernate.generate_statistics=true",
        "logging.level.org.hibernate.engine.internal.StatisticalLoggingSessionEventListener=WARN",
        "logging.level.security=WARN"
})
class NfrPerformanceMeasurements {

    private static final String PASSWORD = "Perf-Passw0rd-" + UUID.randomUUID();
    private static final String HASH = new BCryptPasswordEncoder(12).encode(PASSWORD);
    private static final int WARM_UP = 10;

    @LocalServerPort
    private int port;

    @Autowired
    private UserAccountRepository accountRepository;

    @Autowired
    private AdRepository adRepository;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private final HttpClient client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();

    // ---------------------------------------------------------------- NFR1.6 + NFR1.7

    @Test
    void nfr1_6_logInLatencyAndNfr1_7_tenThousandAccounts() throws Exception {
        String email = seedAccounts("latency", 1).getFirst();
        Session browser = new Session();
        browser.fetchToken();
        for (int i = 0; i < WARM_UP; i++) {
            assertThat(browser.logIn(email, PASSWORD).statusCode()).isEqualTo(200);
        }

        List<Double> before = timeLogIns(browser, email, 50);
        double p50Before = percentile(before, 50);
        double p95Before = percentile(before, 95);
        report("NFR1.6", "log-in latency, 50 sequential log-ins: p50=%.1f ms p95=%.1f ms max=%.1f ms (target p50<=400, p95<=600)",
                p50Before, p95Before, Collections.max(before));

        long start = System.nanoTime();
        seedAccounts("bulk", 10_000);
        long seedMillis = (System.nanoTime() - start) / 1_000_000;
        long total = accountRepository.count();

        for (int i = 0; i < WARM_UP; i++) {
            assertThat(browser.logIn(email, PASSWORD).statusCode()).isEqualTo(200);
        }
        List<Double> after = timeLogIns(browser, email, 50);
        double p50After = percentile(after, 50);
        double p95After = percentile(after, 95);
        double change = (p50After - p50Before) / p50Before * 100;
        report("NFR1.7", "%d accounts in store (10,000 seeded in %d ms): p50=%.1f ms p95=%.1f ms; p50 change vs before=%+.1f%% (target: no measurable change, read as |p50 change|<=10%%)",
                total, seedMillis, p50After, p95After, change);

        // Independent verdicts: a miss on one target must not hide the result of the other
        SoftAssertions targets = new SoftAssertions();
        targets.assertThat(p50Before).as("NFR1.6 p50 ms").isLessThanOrEqualTo(400);
        targets.assertThat(p95Before).as("NFR1.6 p95 ms").isLessThanOrEqualTo(600);
        targets.assertThat(total).as("NFR1.7 account count").isGreaterThanOrEqualTo(10_000);
        targets.assertThat(Math.abs(change)).as("NFR1.7 p50 change in percent").isLessThanOrEqualTo(10);
        targets.assertAll();
    }

    // ---------------------------------------------------------------- NFR1.3

    @Test
    void nfr1_3_unknownEmailAndWrongPasswordTakeTheSameTime() throws Exception {
        // 5 accounts x 4 wrong attempts = 20, so no account reaches the 5-failure lock
        List<String> known = seedAccounts("timing", 5);
        Session browser = new Session();
        browser.fetchToken();
        for (int i = 0; i < WARM_UP; i++) {
            browser.logIn("warmup-" + UUID.randomUUID() + "@example.com", "wrong-password");
        }

        List<Double> unknown = new ArrayList<>();
        List<Double> wrong = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            // Interleaved, so machine drift affects both series equally
            long t0 = System.nanoTime();
            HttpResponse<String> u = browser.logIn("nobody-" + UUID.randomUUID() + "@example.com", "wrong-password");
            unknown.add(millisSince(t0));
            long t1 = System.nanoTime();
            HttpResponse<String> w = browser.logIn(known.get(i % 5), "wrong-password");
            wrong.add(millisSince(t1));
            assertThat(u.statusCode()).isEqualTo(401);
            assertThat(w.statusCode()).isEqualTo(401);
            assertThat(u.body()).isEqualTo(w.body());
        }
        double medianUnknown = percentile(unknown, 50);
        double medianWrong = percentile(wrong, 50);
        double difference = Math.abs(medianUnknown - medianWrong);
        report("NFR1.3", "median log-in time, 20 attempts each: unknown email=%.1f ms, known email + wrong password=%.1f ms, difference=%.1f ms (target <50 ms)",
                medianUnknown, medianWrong, difference);

        assertThat(difference).as("NFR1.3 median difference in ms").isLessThan(50);
    }

    // ---------------------------------------------------------------- NFR3.9

    @Test
    void nfr3_9_tenParallelLogInsFinishWithinThreeSeconds() throws Exception {
        List<String> emails = seedAccounts("parallel", 10);
        List<Session> browsers = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            Session browser = new Session();
            browser.fetchToken();
            browsers.add(browser);
        }
        // Warm the log-in path on this thread first
        Session warm = new Session();
        warm.fetchToken();
        for (int i = 0; i < WARM_UP; i++) {
            warm.logIn(emails.getFirst(), PASSWORD);
        }

        ExecutorService pool = Executors.newFixedThreadPool(10);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            Session browser = browsers.get(i);
            String email = emails.get(i);
            results.add(pool.submit(() -> {
                go.await();
                return browser.logIn(email, PASSWORD).statusCode();
            }));
        }
        long start = System.nanoTime();
        go.countDown();
        int failures = 0;
        for (Future<Integer> result : results) {
            if (result.get() != 200) {
                failures++;
            }
        }
        double elapsed = millisSince(start);
        pool.shutdown();
        report("NFR3.9", "10 parallel log-ins: total=%.0f ms, failures=%d, cores=%d (target <=3000 ms, 0 failures)",
                elapsed, failures, Runtime.getRuntime().availableProcessors());

        assertThat(failures).as("NFR3.9 failures").isZero();
        assertThat(elapsed).as("NFR3.9 total ms").isLessThanOrEqualTo(3000);
    }

    // ---------------------------------------------------------------- NFR3.8

    @Test
    void nfr3_8_fiveHundredSessionsHeldAndEachListsAds() throws Exception {
        List<String> emails = seedAccounts("sessions", 500);
        ExecutorService pool = Executors.newFixedThreadPool(Math.max(4, Runtime.getRuntime().availableProcessors()));
        AtomicInteger loginFailures = new AtomicInteger();
        List<Callable<Session>> logIns = new ArrayList<>();
        for (String email : emails) {
            logIns.add(() -> {
                Session browser = new Session();
                browser.fetchToken();
                if (browser.logIn(email, PASSWORD).statusCode() != 200) {
                    loginFailures.incrementAndGet();
                }
                return browser;
            });
        }
        long start = System.nanoTime();
        List<Session> sessions = new ArrayList<>();
        for (Future<Session> future : pool.invokeAll(logIns)) {
            sessions.add(future.get());
        }
        double loginMillis = millisSince(start);

        // Every session is still held: each one lists ads and is recognised by /me
        AtomicInteger listFailures = new AtomicInteger();
        AtomicInteger anonymous = new AtomicInteger();
        List<Callable<Void>> uses = new ArrayList<>();
        for (Session session : sessions) {
            uses.add(() -> {
                if (session.get("/api/v1/ads").statusCode() != 200) {
                    listFailures.incrementAndGet();
                }
                if (session.get("/api/v1/auth/me").body().contains("\"user\":null")) {
                    anonymous.incrementAndGet();
                }
                return null;
            });
        }
        long useStart = System.nanoTime();
        pool.invokeAll(uses);
        double useMillis = millisSince(useStart);
        pool.shutdown();
        long distinct = sessions.stream().map(s -> s.session).filter(s -> s != null).distinct().count();
        report("NFR3.8", "500 sessions: %d distinct session ids, log-in failures=%d (%.0f ms), list failures=%d, sessions seen as anonymous=%d (%.0f ms for 1,000 requests) (target: 500 sessions without errors)",
                distinct, loginFailures.get(), loginMillis, listFailures.get(), anonymous.get(), useMillis);

        assertThat(loginFailures.get()).as("NFR3.8 log-in failures").isZero();
        assertThat(distinct).as("NFR3.8 distinct sessions").isEqualTo(500);
        assertThat(listFailures.get()).as("NFR3.8 list failures").isZero();
        assertThat(anonymous.get()).as("NFR3.8 lost sessions").isZero();
    }

    // ---------------------------------------------------------------- NFR5.5

    @Test
    void nfr5_5_listOfAThousandAdsByAHundredPosters() throws Exception {
        adRepository.deleteAll();
        List<String> posters = seedAccounts("poster", 100);
        List<Long> posterIds = posters.stream()
                .map(email -> accountRepository.findByEmail(email).orElseThrow().getId()).toList();
        List<Ad> ads = new ArrayList<>();
        for (int i = 0; i < 1_000; i++) {
            ads.add(new Ad("Ad " + i, "Description " + i, posterIds.get(i % 100)));
        }
        adRepository.saveAll(ads);

        Session visitor = new Session();
        for (int i = 0; i < WARM_UP; i++) {
            assertThat(visitor.get("/api/v1/ads").statusCode()).isEqualTo(200);
        }

        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        HttpResponse<String> one = visitor.get("/api/v1/ads");
        long statements = statistics.getPrepareStatementCount();
        assertThat(one.statusCode()).isEqualTo(200);
        int adCount = one.body().split("\"authorName\"", -1).length - 1;
        boolean unknownAuthor = one.body().contains("\"authorName\":\"Unknown\"");
        boolean anyEmail = one.body().contains("@example.com");

        List<Double> timings = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            long t0 = System.nanoTime();
            assertThat(visitor.get("/api/v1/ads").statusCode()).isEqualTo(200);
            timings.add(millisSince(t0));
        }
        double p50 = percentile(timings, 50);
        double p95 = percentile(timings, 95);
        report("NFR5.5", "list of %d ads by 100 posters: SQL statements per request=%d, p50=%.1f ms p95=%.1f ms, 'Unknown' authors=%s, emails in body=%s (target <=2 statements, p95<=150 ms)",
                adCount, statements, p50, p95, unknownAuthor, anyEmail);

        assertThat(adCount).isEqualTo(1_000);
        assertThat(unknownAuthor).as("every ad has its poster's name").isFalse();
        assertThat(anyEmail).as("no email in the list").isFalse();
        assertThat(statements).as("NFR5.5 SQL statements per list request").isLessThanOrEqualTo(2);
        assertThat(p95).as("NFR5.5 p95 ms").isLessThanOrEqualTo(150);
    }

    // ---------------------------------------------------------------- helpers

    private List<String> seedAccounts(String prefix, int count) {
        String run = UUID.randomUUID().toString().substring(0, 8);
        List<UserAccount> accounts = new ArrayList<>(count);
        List<String> emails = new ArrayList<>(count);
        Instant now = Instant.now();
        for (int i = 0; i < count; i++) {
            String email = prefix + "-" + run + "-" + i + "@example.com";
            emails.add(email);
            accounts.add(new UserAccount(email, prefix + " " + i, HASH, now));
        }
        for (int from = 0; from < count; from += 1_000) {
            accountRepository.saveAll(accounts.subList(from, Math.min(count, from + 1_000)));
        }
        return emails;
    }

    private List<Double> timeLogIns(Session browser, String email, int count) throws Exception {
        List<Double> timings = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            long t0 = System.nanoTime();
            HttpResponse<String> response = browser.logIn(email, PASSWORD);
            timings.add(millisSince(t0));
            assertThat(response.statusCode()).isEqualTo(200);
        }
        return timings;
    }

    private static double millisSince(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000.0;
    }

    /**
     * Nearest-rank percentile.
     */
    static double percentile(List<Double> values, int percentile) {
        List<Double> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        int rank = (int) Math.ceil(percentile / 100.0 * sorted.size());
        return sorted.get(Math.max(0, rank - 1));
    }

    private static void report(String id, String format, Object... args) {
        System.out.println("NFR-MEASURE " + id + " " + String.format(format, args));
    }

    /**
     * One browser: keeps the session and anti-forgery cookies like the frontend does.
     */
    private final class Session {

        private volatile String session;
        private volatile String xsrf;

        void fetchToken() throws IOException, InterruptedException {
            get("/api/v1/auth/me");
        }

        HttpResponse<String> logIn(String email, String password) throws IOException, InterruptedException {
            return send(HttpRequest.newBuilder(url("/api/v1/auth/login"))
                    .header("Content-Type", "application/json")
                    .header("X-XSRF-TOKEN", xsrf)
                    .POST(HttpRequest.BodyPublishers.ofString(
                            "{\"email\": \"%s\", \"password\": \"%s\"}".formatted(email, password))));
        }

        HttpResponse<String> get(String path) throws IOException, InterruptedException {
            return send(HttpRequest.newBuilder(url(path)).GET());
        }

        private HttpResponse<String> send(HttpRequest.Builder builder) throws IOException, InterruptedException {
            List<String> cookies = new ArrayList<>();
            if (session != null) {
                cookies.add("JSESSIONID=" + session);
            }
            if (xsrf != null) {
                cookies.add("XSRF-TOKEN=" + xsrf);
            }
            if (!cookies.isEmpty()) {
                builder.header("Cookie", String.join("; ", cookies));
            }
            HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            for (String header : response.headers().allValues("Set-Cookie")) {
                int end = header.indexOf(';');
                String pair = end < 0 ? header : header.substring(0, end);
                if (pair.startsWith("JSESSIONID=")) {
                    session = pair.substring("JSESSIONID=".length());
                } else if (pair.startsWith("XSRF-TOKEN=")) {
                    xsrf = pair.substring("XSRF-TOKEN=".length());
                }
            }
            return response;
        }

        private URI url(String path) {
            return URI.create("http://localhost:" + port + path);
        }
    }
}
