package de.tum.cit.aet.artemis.globalsearch;

import static de.tum.cit.aet.artemis.globalsearch.util.WeaviateTestUtil.assertCourseExistsInWeaviate;
import static de.tum.cit.aet.artemis.globalsearch.util.WeaviateTestUtil.assertCourseNotInWeaviate;
import static de.tum.cit.aet.artemis.globalsearch.util.WeaviateTestUtil.countRowsForEntity;
import static de.tum.cit.aet.artemis.globalsearch.util.WeaviateTestUtil.queryCourseProperties;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.globalsearch.config.schema.entityschemas.SearchableEntitySchema;
import de.tum.cit.aet.artemis.globalsearch.domain.WeaviateOutboxEntry;
import de.tum.cit.aet.artemis.globalsearch.dto.searchableentity.CourseSearchableEntityDTO;
import de.tum.cit.aet.artemis.globalsearch.repository.SearchableEntitySyncStateRepository;
import de.tum.cit.aet.artemis.globalsearch.repository.WeaviateOutboxRepository;
import de.tum.cit.aet.artemis.globalsearch.service.SearchableEntityWeaviateService;
import de.tum.cit.aet.artemis.globalsearch.service.WeaviateService;
import de.tum.cit.aet.artemis.programming.AbstractProgrammingIntegrationLocalCILocalVCTest;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseUtilService;

/**
 * Integration tests for the durable Weaviate outbox: a metadata change is recorded as an outbox row,
 * and the single-writer dispatcher on the scheduling node applies it to Weaviate, refreshes the sync ledger,
 * and removes the row. Also verifies idempotent re-application and that an enqueue joins the caller's
 * transaction (rolling back leaves no outbox row).
 * <p>
 * Tests are skipped when Docker is not available or the Weaviate container failed to start.
 */
@EnabledIf("isWeaviateEnabled")
class WeaviateOutboxIntegrationTest extends AbstractProgrammingIntegrationLocalCILocalVCTest {

    private static final String TEST_PREFIX = "weaviateoutboxint";

    private static final String COURSE_TYPE = SearchableEntitySchema.TypeValues.COURSE;

    @Autowired
    private SearchableEntityWeaviateService searchableEntityWeaviateService;

    @Autowired
    private WeaviateService weaviateService;

    @Autowired
    private WeaviateOutboxRepository outboxRepository;

    @Autowired
    private SearchableEntitySyncStateRepository syncStateRepository;

    @Autowired
    private ProgrammingExerciseUtilService programmingExerciseUtilService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private Course course;

    static boolean isWeaviateEnabled() {
        return weaviateContainer != null && weaviateContainer.isRunning();
    }

    @BeforeEach
    void setUp() {
        userUtilService.addUsers(TEST_PREFIX, 1, 1, 0, 1);
        course = programmingExerciseUtilService.addEnrolledCourseWithOneProgrammingExercise(TEST_PREFIX);
        // Pyris is not running in integration tests — stub the FAQ deletion to prevent PyrisConnectorException.
        doNothing().when(pyrisFaqApi).deleteFaq(any());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void upsert_isDispatchedToWeaviate_writesSyncState_andRemovesOutboxRow() {
        searchableEntityWeaviateService.upsertCourseAsync(CourseSearchableEntityDTO.fromCourse(course));

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            assertThat(queryCourseProperties(weaviateService, course.getId())).as("course indexed in Weaviate").isNotNull();
            assertThat(syncStateRepository.findByEntityTypeAndEntityId(COURSE_TYPE, course.getId())).as("sync ledger row written").isPresent()
                    .hasValueSatisfying(state -> assertThat(state.getContentHash()).hasSize(64));
            assertThat(hasOutboxRowFor(COURSE_TYPE, course.getId())).as("outbox row removed after confirmed write").isFalse();
        });
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void deleteEntity_isDispatchedToWeaviate_andClearsSyncState() throws Exception {
        searchableEntityWeaviateService.upsertCourseAsync(CourseSearchableEntityDTO.fromCourse(course));
        assertCourseExistsInWeaviate(weaviateService, course);

        searchableEntityWeaviateService.deleteEntityAsync(COURSE_TYPE, course.getId());

        assertCourseNotInWeaviate(weaviateService, course.getId());
        await().atMost(Duration.ofSeconds(30))
                .untilAsserted(() -> assertThat(syncStateRepository.findByEntityTypeAndEntityId(COURSE_TYPE, course.getId())).as("sync ledger row cleared on delete").isEmpty());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void duplicateUpserts_areIdempotent_yieldingASingleWeaviateRow() {
        var dto = CourseSearchableEntityDTO.fromCourse(course);
        // Two identical enqueues produce two outbox rows; applying both must converge to one row (deterministic UUID replace).
        searchableEntityWeaviateService.upsertCourseAsync(dto);
        searchableEntityWeaviateService.upsertCourseAsync(dto);

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            assertThat(queryCourseProperties(weaviateService, course.getId())).isNotNull();
            assertThat(countRowsForEntity(weaviateService, COURSE_TYPE, course.getId())).isEqualTo(1);
            assertThat(hasOutboxRowFor(COURSE_TYPE, course.getId())).isFalse();
        });
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void enqueueInRolledBackTransaction_persistsNoOutboxRow() throws Exception {
        var dto = CourseSearchableEntityDTO.fromCourse(course);
        TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);

        // The enqueue joins the ambient transaction, so rolling it back must discard the outbox row.
        assertThatThrownBy(() -> txTemplate.executeWithoutResult(status -> {
            searchableEntityWeaviateService.upsertCourseAsync(dto);
            throw new IllegalStateException("force rollback");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(hasOutboxRowFor(COURSE_TYPE, course.getId())).as("rolled-back enqueue leaves no outbox row").isFalse();
        // The after-commit dispatch never fires on rollback, so nothing reaches Weaviate either.
        assertCourseNotInWeaviate(weaviateService, course.getId());
    }

    private boolean hasOutboxRowFor(String type, long entityId) {
        return outboxRepository.findAll().stream().anyMatch(entry -> type.equals(entry.getEntityType()) && isEntity(entry, entityId));
    }

    private static boolean isEntity(WeaviateOutboxEntry entry, long entityId) {
        return entry.getEntityId() != null && entry.getEntityId() == entityId;
    }
}
