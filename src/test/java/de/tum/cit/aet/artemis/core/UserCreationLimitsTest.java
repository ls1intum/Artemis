package de.tum.cit.aet.artemis.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.KeyPairGenerator;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.apache.sshd.common.config.keys.AuthorizedKeyEntry;
import org.apache.sshd.common.config.keys.PublicKeyEntry;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.test_repository.UserTestRepository;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.security.SecurityUtils;
import de.tum.cit.aet.artemis.course.domain.CourseRequest;
import de.tum.cit.aet.artemis.course.domain.CourseRequestStatus;
import de.tum.cit.aet.artemis.course.dto.CourseRequestCreateDTO;
import de.tum.cit.aet.artemis.course.repository.CourseRequestRepository;
import de.tum.cit.aet.artemis.course.service.CourseRequestService;
import de.tum.cit.aet.artemis.localvc.service.sshuserkeys.UserSshPublicKeyService;
import de.tum.cit.aet.artemis.programming.domain.UserSshPublicKey;
import de.tum.cit.aet.artemis.programming.dto.UserSshPublicKeyDTO;
import de.tum.cit.aet.artemis.programming.repository.UserSshPublicKeyRepository;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

class UserCreationLimitsTest extends AbstractSpringIntegrationIndependentTest {

    @Autowired
    private CourseRequestRepository courseRequestRepository;

    @Autowired
    private CourseRequestService courseRequestService;

    @Autowired
    private UserSshPublicKeyRepository sshKeyRepository;

    @Autowired
    private UserSshPublicKeyService sshKeyService;

    @Autowired
    private UserTestRepository userRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private User user;

    @BeforeEach
    void setUp() {
        var saved = userUtilService.createAndSaveUser("creationlimit" + UUID.randomUUID().toString().substring(0, 8));
        user = userRepository.findByIdWithAuthoritiesElseThrow(saved.getId());
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM course_request WHERE requester_id = ?", user.getId());
        sshKeyRepository.deleteAllByUserId(user.getId());
    }

    @Test
    void concurrentCourseRequestsCannotExceedPendingLimit() throws Exception {
        for (int i = 0; i < 4; i++) {
            CourseRequest pending = new CourseRequest();
            pending.setTitle("Pending " + i);
            pending.setShortName("LIMIT" + user.getId() + "N" + i);
            pending.setSemester("WS2026");
            pending.setStartDate(ZonedDateTime.now());
            pending.setEndDate(ZonedDateTime.now().plusMonths(3));
            pending.setReason("Waiting for a decision");
            pending.setRequester(user);
            pending.setStatus(CourseRequestStatus.PENDING);
            pending.setCreatedDate(ZonedDateTime.now());
            courseRequestRepository.save(pending);
        }

        List<String> results = createConcurrently(() -> createCourseRequest("A"), () -> createCourseRequest("B"));

        assertThat(courseRequestRepository.countByRequesterIdAndStatus(user.getId(), CourseRequestStatus.PENDING)).isEqualTo(5);
        assertThat(results).containsExactlyInAnyOrder("created", "courseRequestTooManyPending");
    }

    private void createCourseRequest(String suffix) {
        var dto = new CourseRequestCreateDTO("Concurrent Request", "LIMIT" + user.getId() + suffix, "WS2026", ZonedDateTime.now(), ZonedDateTime.now().plusMonths(3), false, null,
                "Request at the pending limit");
        SecurityUtils.runAs(user, () -> courseRequestService.createCourseRequest(dto));
    }

    @Test
    void concurrentSshKeysCannotExceedUserLimit() throws Exception {
        for (int i = 0; i < 49; i++) {
            var key = new UserSshPublicKey();
            key.setUserId(user.getId());
            key.setPublicKey("fixture " + i);
            key.setKeyHash("limit" + user.getId() + "fixture" + i);
            key.setLabel("Fixture " + i);
            key.setCreationDate(ZonedDateTime.now());
            sshKeyRepository.save(key);
        }
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        String firstKey = PublicKeyEntry.toString(generator.generateKeyPair().getPublic());
        String secondKey = PublicKeyEntry.toString(generator.generateKeyPair().getPublic());

        List<String> results = createConcurrently(() -> createSshKey(firstKey), () -> createSshKey(secondKey));

        assertThat(sshKeyRepository.countByUserId(user.getId())).isEqualTo(50);
        assertThat(results).containsExactlyInAnyOrder("created", "tooManyKeys");
    }

    private void createSshKey(String publicKey) throws Exception {
        var dto = new UserSshPublicKeyDTO(null, "Concurrent key", publicKey, null, null, null, null);
        sshKeyService.createSshKeyForUser(user, AuthorizedKeyEntry.parseAuthorizedKeyEntry(publicKey), dto);
    }

    @FunctionalInterface
    private interface Creation {

        void run() throws Exception;
    }

    /**
     * Hold the parent row while both independent database transactions begin. Without serialization before counting,
     * both inserts reach their foreign-key checks with the old count and wait here; after release both would succeed.
     * With serialization, both wait before counting and only one can consume the remaining slot.
     */
    private List<String> createConcurrently(Creation first, Creation second) throws Exception {
        // Reserve a worker for the observer while both creation transactions are blocked on the user row.
        try (var executor = Executors.newFixedThreadPool(3)) {
            var transaction = new TransactionTemplate(transactionManager);
            List<CompletableFuture<String>> futures = transaction.execute(status -> {
                jdbcTemplate.queryForList("SELECT id FROM jhi_user WHERE id = ? FOR UPDATE", user.getId());
                int holder = jdbcTemplate.queryForObject("SELECT pg_backend_pid()", Integer.class);
                var a = createAsync(first, executor);
                var b = createAsync(second, executor);
                // Query from another thread/connection so PostgreSQL's per-transaction statistics snapshot stays fresh.
                CompletableFuture.runAsync(() -> Awaitility.await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(blockedBy(holder)).isEqualTo(2)), executor)
                        .orTimeout(15, TimeUnit.SECONDS).join();
                return List.of(a, b);
            });
            return List.of(futures.getFirst().get(10, TimeUnit.SECONDS), futures.getLast().get(10, TimeUnit.SECONDS));
        }
    }

    private long blockedBy(int holder) {
        return jdbcTemplate.queryForObject("""
                WITH RECURSIVE blocked(pid) AS (
                    SELECT pid FROM pg_stat_activity WHERE ? = ANY(pg_blocking_pids(pid))
                    UNION
                    SELECT activity.pid FROM pg_stat_activity activity
                    JOIN blocked ON blocked.pid = ANY(pg_blocking_pids(activity.pid))
                )
                SELECT COUNT(*) FROM blocked
                """, Long.class, holder);
    }

    private CompletableFuture<String> createAsync(Creation creation, Executor executor) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                creation.run();
                return "created";
            }
            catch (BadRequestAlertException exception) {
                return exception.getErrorKey();
            }
            catch (Exception exception) {
                throw new IllegalStateException(exception);
            }
        }, executor);
    }
}
