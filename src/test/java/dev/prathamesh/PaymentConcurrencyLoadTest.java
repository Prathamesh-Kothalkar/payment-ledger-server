package dev.prathamesh;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.IntStream;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import dev.prathamesh.load.LoadRunner;
import dev.prathamesh.load.Report;
import dev.prathamesh.model.UserModel;
import dev.prathamesh.repository.AccountRepository;
import dev.prathamesh.service.LedgerService;
import dev.prathamesh.service.PaymentService;
import dev.prathamesh.service.UserService;
import dev.prathamesh.types.LedgerVerifyResponse;
import dev.prathamesh.types.PaymentRequest;

/**
 * Heavy concurrency tests. Skipped unless run with -Dload=true:
 *   ./mvnw test -Dtest=PaymentConcurrencyLoadTest -Dload=true
 * Options: -Dload.threads=20000  -Dload.inflight=200  -Dload.timeout=600
 */
@SpringBootTest
@EnabledIfSystemProperty(named = "load", matches = "true")
@TestMethodOrder(MethodOrderer.MethodName.class)
class PaymentConcurrencyLoadTest extends AbstractPostgresTest {

    static final int THREADS = Integer.getInteger("load.threads", 10_000);
    static final int IN_FLIGHT = Integer.getInteger("load.inflight", THREADS);
    static final int TIMEOUT_SEC = Integer.getInteger("load.timeout", 300);
    static final int ACCOUNTS = Math.max(200, THREADS / 50);
    static final BigDecimal SIGNUP_BALANCE = new BigDecimal("2000"); // signup bonus in UserService

    static final List<Report> REPORTS = new ArrayList<>();

    @Autowired PaymentService paymentService;
    @Autowired UserService userService;
    @Autowired LedgerService ledgerService;
    @Autowired AccountRepository accountRepository;
    @Autowired JdbcTemplate jdbc;

    // ------------------------------------------------------------------ scenarios

    @Test
    void s1_sameKeyFromManyThreads() throws Exception {
        List<Long> acc = newAccounts(2);
        String key = "same-key-" + UUID.randomUUID();

        Report r = LoadRunner.run("1. same idempotency key x N", THREADS, IN_FLIGHT, TIMEOUT_SEC,
                i -> transfer(acc.get(0), acc.get(1), "10", key));

        r.check("exactly one payment row for the key",
                count("select count(*) from payments where idempotency_key = ?", key) == 1);
        r.check("exactly 2 ledger entries (one debit, one credit)",
                count("select count(*) from ledger_entries le join payments p on p.payment_id = le.payment_id "
                        + "where p.idempotency_key = ?", key) == 2);
        r.check("sender debited exactly once", balance(acc.get(0)).compareTo(new BigDecimal("1990")) == 0);
        r.check("receiver credited exactly once", balance(acc.get(1)).compareTo(new BigDecimal("2010")) == 0);
        r.check("payment ended SUCCESS", "SUCCESS".equals(
                jdbc.queryForObject("select status from payments where idempotency_key = ?", String.class, key)));
        finish(r);
    }

    @Test
    void s2_randomTransfersSpreadAcrossAccounts() throws Exception {
        List<Long> acc = newAccounts(ACCOUNTS);
        BigDecimal before = sumBalances(acc);

        Report r = LoadRunner.run("2. N random transfers, many accounts", THREADS, IN_FLIGHT, TIMEOUT_SEC, i -> {
            ThreadLocalRandom rnd = ThreadLocalRandom.current();
            int a = rnd.nextInt(acc.size());
            int b = (a + 1 + rnd.nextInt(acc.size() - 1)) % acc.size();
            return transfer(acc.get(a), acc.get(b), String.valueOf(1 + rnd.nextInt(5)),
                    "spread-" + UUID.randomUUID());
        });

        r.check("every payment SUCCESS", r.count("SUCCESS") == THREADS);
        r.check("money conserved across the accounts", sumBalances(acc).compareTo(before) == 0);
        finish(r);
    }

    @Test
    void s3_hotReceiverAccount() throws Exception {
        List<Long> acc = newAccounts(ACCOUNTS + 1);
        long hot = acc.get(0);
        BigDecimal before = sumBalances(acc);

        Report r = LoadRunner.run("3. N payments into ONE hot account", THREADS, IN_FLIGHT, TIMEOUT_SEC, i -> {
            long sender = acc.get(1 + ThreadLocalRandom.current().nextInt(acc.size() - 1));
            return transfer(sender, hot, "1", "hot-" + UUID.randomUUID());
        });

        r.check("every payment SUCCESS", r.count("SUCCESS") == THREADS);
        r.check("hot account got exactly the sum of successes (no lost updates)",
                balance(hot).compareTo(SIGNUP_BALANCE.add(BigDecimal.valueOf(r.count("SUCCESS")))) == 0);
        r.check("money conserved", sumBalances(acc).compareTo(before) == 0);
        finish(r);
    }

    @Test
    void s4_overdraftIsImpossible() throws Exception {
        List<Long> acc = newAccounts(2);
        long sender = acc.get(0), receiver = acc.get(1);
        long expectedOk = Math.min(THREADS, SIGNUP_BALANCE.longValue());
        long expectedRejected = THREADS - expectedOk;

        Report r = LoadRunner.run("4. overdraft: N x 1 from a 2000 balance", THREADS, IN_FLIGHT, TIMEOUT_SEC,
                i -> transfer(sender, receiver, "1", "overdraft-" + UUID.randomUUID()));

        r.check("exactly 2000 payments succeed", r.count("SUCCESS") == expectedOk);
        r.check("all the rest rejected for insufficient balance",
                r.count("REJECTED:Insufficient balance") == expectedRejected);
        r.check("sender balance is exactly 0",
                balance(sender).compareTo(SIGNUP_BALANCE.subtract(BigDecimal.valueOf(expectedOk))) == 0);
        r.check("receiver got exactly what was spent",
                balance(receiver).compareTo(SIGNUP_BALANCE.add(BigDecimal.valueOf(expectedOk))) == 0);
        r.check("rejected payments are recorded as FAILED",
                count("select count(*) from payments where source_account_id = ? and status = 'FAILED'", sender)
                        == expectedRejected);
        finish(r);
    }

    @Test
    void s5_oppositeTransfersDoNotDeadlock() throws Exception {
        List<Long> acc = newAccounts(2);
        long a = acc.get(0), b = acc.get(1);

        Report r = LoadRunner.run("5. A->B and B->A simultaneously", THREADS, IN_FLIGHT, TIMEOUT_SEC,
                i -> i % 2 == 0
                        ? transfer(a, b, "1", "opp-" + UUID.randomUUID())
                        : transfer(b, a, "1", "opp-" + UUID.randomUUID()));

        long okAtoB = IntStream.range(0, THREADS).filter(i -> i % 2 == 0 && "SUCCESS".equals(r.outcomeAt(i))).count();
        long okBtoA = IntStream.range(0, THREADS).filter(i -> i % 2 == 1 && "SUCCESS".equals(r.outcomeAt(i))).count();

        r.check("finished without deadlock (runner would have timed out)", true);
        r.check("every task either succeeded or was rejected for balance",
                r.count("SUCCESS") + r.count("REJECTED:Insufficient balance") == THREADS);
        r.check("A balance matches successful outcomes exactly",
                balance(a).compareTo(SIGNUP_BALANCE.subtract(BigDecimal.valueOf(okAtoB)).add(BigDecimal.valueOf(okBtoA))) == 0);
        r.check("B balance matches successful outcomes exactly",
                balance(b).compareTo(SIGNUP_BALANCE.add(BigDecimal.valueOf(okAtoB)).subtract(BigDecimal.valueOf(okBtoA))) == 0);
        finish(r);
    }

    @Test
    void s6_retryStorm() throws Exception {
        List<Long> acc = newAccounts(ACCOUNTS);
        BigDecimal before = sumBalances(acc);
        int keys = Math.max(1, THREADS / 10);               // each key is sent ~10 times
        String prefix = "storm-" + UUID.randomUUID() + "-";

        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        int[] from = new int[keys];
        int[] to = new int[keys];
        for (int k = 0; k < keys; k++) {                     // same key must always carry the same request
            from[k] = rnd.nextInt(acc.size());
            to[k] = (from[k] + 1 + rnd.nextInt(acc.size() - 1)) % acc.size();
        }

        Report r = LoadRunner.run("6. retry storm (each key ~10x)", THREADS, IN_FLIGHT, TIMEOUT_SEC, i -> {
            int k = i % keys;
            return transfer(acc.get(from[k]), acc.get(to[k]), "3", prefix + k);
        });

        r.check("exactly one payment per key",
                count("select count(*) from payments where idempotency_key like ?", prefix + "%") == keys);
        r.check("every payment executed exactly once (2 entries each)",
                count("select count(*) from ledger_entries le join payments p on p.payment_id = le.payment_id "
                        + "where p.idempotency_key like ?", prefix + "%") == 2L * keys);
        r.check("all payments ended SUCCESS",
                count("select count(*) from payments where idempotency_key like ? and status = 'SUCCESS'",
                        prefix + "%") == keys);
        r.check("callers only ever saw SUCCESS / PROCESSING / PENDING",
                r.count("SUCCESS") + r.count("PROCESSING") + r.count("PENDING") == THREADS);
        r.check("money conserved", sumBalances(acc).compareTo(before) == 0);
        finish(r);
    }

    // ------------------------------------------------------------------ report

    @AfterAll
    static void writeReport() throws IOException {
        if (REPORTS.isEmpty()) return;
        String env = String.format("%d cores, Java %s, threads=%d, in-flight=%d, hikari pool=50, postgres:17-alpine (Docker)",
                Runtime.getRuntime().availableProcessors(), System.getProperty("java.version"), THREADS, IN_FLIGHT);
        String md = Report.matrix(REPORTS, env);
        System.out.println("\n" + md);

        Path dir = Path.of("target");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("load-report.md"), md);
        Files.writeString(dir.resolve("load-report.csv"), Report.csv(REPORTS));
    }

    // ------------------------------------------------------------------ helpers

    /** Common checks that must hold after ANY scenario; then print, record and assert. */
    private void finish(Report r) {
        LedgerVerifyResponse v = ledgerService.verify();
        r.check("ledger sums to zero", v.zeroSum());
        r.check("cached balances match ledger on every account", v.mismatchedAccountIds().isEmpty());
        r.check("no USER account is negative",
                count("select count(*) from accounts where account_type = 'USER' and balance < 0") == 0);
        r.check("no payment stuck in PENDING/PROCESSING",
                count("select count(*) from payments where status in ('PENDING','PROCESSING')") == 0);
        r.check("no unexpected exceptions", r.count("EXCEPTION") == 0);

        REPORTS.add(r);
        System.out.println(r.summary());
        r.assertPassed();    // metrics are printed first, so you still see numbers on failure
    }

    private String transfer(long from, long to, String amount, String key) {
        try {
            return paymentService.transfer(new PaymentRequest(from, to, new BigDecimal(amount), key))
                    .getStatus().name();
        } catch (IllegalArgumentException e) {
            return "REJECTED:" + e.getMessage();
        }
    }

    /** Creates n users (each gets an account and the signup bonus) and returns their account ids. */
    private List<Long> newAccounts(int n) {
        List<Long> userIds = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            UserModel u = userService.createUser(
                    new UserModel("load-" + i, "load-" + UUID.randomUUID() + "@test.io"));
            userIds.add(u.getId());
        }
        Map<Long, Long> accountByUser = new HashMap<>();
        accountRepository.findAll().forEach(a -> accountByUser.put(a.getUserId(), a.getId()));
        return userIds.stream().map(accountByUser::get).toList();
    }

    private BigDecimal balance(long accountId) {
        return jdbc.queryForObject("select balance from accounts where account_id = ?", BigDecimal.class, accountId);
    }

    private BigDecimal sumBalances(List<Long> ids) {
        BigDecimal sum = BigDecimal.ZERO;
        for (Long id : ids) sum = sum.add(balance(id));
        return sum;
    }

    private long count(String sql, Object... args) {
        Long n = jdbc.queryForObject(sql, Long.class, args);
        return n == null ? 0 : n;
    }
}