package de.tum.cit.aet.artemis.globalsearch;

import static de.tum.cit.aet.artemis.globalsearch.util.WeaviateTestUtil.assertAnswerPostExistsInWeaviate;
import static de.tum.cit.aet.artemis.globalsearch.util.WeaviateTestUtil.assertAnswerPostNotInWeaviate;
import static de.tum.cit.aet.artemis.globalsearch.util.WeaviateTestUtil.assertCourseExistsInWeaviate;
import static de.tum.cit.aet.artemis.globalsearch.util.WeaviateTestUtil.assertCourseNotInWeaviate;
import static de.tum.cit.aet.artemis.globalsearch.util.WeaviateTestUtil.assertLectureUnitExistsInWeaviate;
import static de.tum.cit.aet.artemis.globalsearch.util.WeaviateTestUtil.assertLectureUnitNotInWeaviate;
import static de.tum.cit.aet.artemis.globalsearch.util.WeaviateTestUtil.assertPostExistsInWeaviate;
import static de.tum.cit.aet.artemis.globalsearch.util.WeaviateTestUtil.assertPostNotInWeaviate;
import static de.tum.cit.aet.artemis.globalsearch.util.WeaviateTestUtil.countRowsForEntity;
import static de.tum.cit.aet.artemis.globalsearch.util.WeaviateTestUtil.queryCourseProperties;
import static de.tum.cit.aet.artemis.globalsearch.util.WeaviateTestUtil.seedRow;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

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
import de.tum.cit.aet.artemis.globalsearch.dto.searchableentity.AnswerPostSearchableEntityDTO;
import de.tum.cit.aet.artemis.globalsearch.dto.searchableentity.CourseSearchableEntityDTO;
import de.tum.cit.aet.artemis.globalsearch.dto.searchableentity.LectureUnitSearchableEntityDTO;
import de.tum.cit.aet.artemis.globalsearch.dto.searchableentity.PostSearchableEntityDTO;
import de.tum.cit.aet.artemis.globalsearch.repository.SearchableEntitySyncStateRepository;
import de.tum.cit.aet.artemis.globalsearch.repository.WeaviateOutboxRepository;
import de.tum.cit.aet.artemis.globalsearch.service.SearchableEntityWeaviateService;
import de.tum.cit.aet.artemis.globalsearch.service.WeaviateService;
import de.tum.cit.aet.artemis.programming.AbstractProgrammingIntegrationLocalCILocalVCTest;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseUtilService;

/**
 * Integration tests for the durable Weaviate outbox: a metadata change is recorded as an outbox row,
 * and the single-writer dispatcher on the scheduling node applies it to Weaviate, refreshes the sync ledger,
 * and removes the row. Also verifies idempotent re-application, that an enqueue joins the caller's transaction
 * (rolling back leaves no outbox row), and that each bulk-delete operation maps to the correct Weaviate deletion.
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
    void testUpsert_isDispatchedWritesSyncStateAndRemovesOutboxRow() {
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
    void testDeleteEntity_isDispatchedAndClearsSyncState() throws Exception {
        searchableEntityWeaviateService.upsertCourseAsync(CourseSearchableEntityDTO.fromCourse(course));
        assertCourseExistsInWeaviate(weaviateService, course);

        searchableEntityWeaviateService.deleteEntityAsync(COURSE_TYPE, course.getId());

        assertCourseNotInWeaviate(weaviateService, course.getId());
        await().atMost(Duration.ofSeconds(30))
                .untilAsserted(() -> assertThat(syncStateRepository.findByEntityTypeAndEntityId(COURSE_TYPE, course.getId())).as("sync ledger row cleared on delete").isEmpty());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testDuplicateUpserts_areIdempotentYieldingASingleRow() {
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
    void testEnqueueInRolledBackTransaction_persistsNoOutboxRow() throws Exception {
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

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testDeleteAllPostsForChannel_removesChannelPostsAndLeavesOthers() throws Exception {
        long courseId = 990100, channelId = 990101, otherChannelId = 990109, postId = 990102, answerPostId = 990103, otherPostId = 990108;
        seedRow(weaviateService, SearchableEntitySchema.TypeValues.POST, postId, new PostSearchableEntityDTO(postId, courseId, channelId, "title", "content").toPropertyMap());
        seedRow(weaviateService, SearchableEntitySchema.TypeValues.ANSWER_POST, answerPostId,
                new AnswerPostSearchableEntityDTO(answerPostId, postId, courseId, channelId, "answer").toPropertyMap());
        seedRow(weaviateService, SearchableEntitySchema.TypeValues.POST, otherPostId,
                new PostSearchableEntityDTO(otherPostId, courseId, otherChannelId, "title", "content").toPropertyMap());
        assertPostExistsInWeaviate(weaviateService, postId);
        assertAnswerPostExistsInWeaviate(weaviateService, answerPostId);
        assertPostExistsInWeaviate(weaviateService, otherPostId);

        searchableEntityWeaviateService.deleteAllPostsForChannelAsync(channelId);

        assertPostNotInWeaviate(weaviateService, postId);
        assertAnswerPostNotInWeaviate(weaviateService, answerPostId);
        assertPostExistsInWeaviate(weaviateService, otherPostId);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testDeleteAllPostsForCourse_removesCoursePostsAndAnswerPosts() throws Exception {
        long courseId = 990200, channelId = 990201, postId = 990202, answerPostId = 990203;
        seedRow(weaviateService, SearchableEntitySchema.TypeValues.POST, postId, new PostSearchableEntityDTO(postId, courseId, channelId, "title", "content").toPropertyMap());
        seedRow(weaviateService, SearchableEntitySchema.TypeValues.ANSWER_POST, answerPostId,
                new AnswerPostSearchableEntityDTO(answerPostId, postId, courseId, channelId, "answer").toPropertyMap());
        assertPostExistsInWeaviate(weaviateService, postId);
        assertAnswerPostExistsInWeaviate(weaviateService, answerPostId);

        searchableEntityWeaviateService.deleteAllPostsForCourseAsync(courseId);

        assertPostNotInWeaviate(weaviateService, postId);
        assertAnswerPostNotInWeaviate(weaviateService, answerPostId);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testDeleteAllAnswerPostsForPost_removesAnswerPostsLeavesThePost() throws Exception {
        long courseId = 990300, channelId = 990301, postId = 990302, answerPostId = 990303;
        seedRow(weaviateService, SearchableEntitySchema.TypeValues.POST, postId, new PostSearchableEntityDTO(postId, courseId, channelId, "title", "content").toPropertyMap());
        seedRow(weaviateService, SearchableEntitySchema.TypeValues.ANSWER_POST, answerPostId,
                new AnswerPostSearchableEntityDTO(answerPostId, postId, courseId, channelId, "answer").toPropertyMap());
        assertPostExistsInWeaviate(weaviateService, postId);
        assertAnswerPostExistsInWeaviate(weaviateService, answerPostId);

        searchableEntityWeaviateService.deleteAllAnswerPostsForPostAsync(postId);

        assertAnswerPostNotInWeaviate(weaviateService, answerPostId);
        assertPostExistsInWeaviate(weaviateService, postId);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testDeleteAllForCourse_removesEveryRowAndLeavesOtherCourses() throws Exception {
        long courseId = 990400, otherCourseId = 990409, channelId = 990401, lectureId = 990404, postId = 990402, unitId = 990403, otherPostId = 990408;
        seedRow(weaviateService, SearchableEntitySchema.TypeValues.POST, postId, new PostSearchableEntityDTO(postId, courseId, channelId, "title", "content").toPropertyMap());
        seedRow(weaviateService, SearchableEntitySchema.TypeValues.LECTURE_UNIT, unitId,
                new LectureUnitSearchableEntityDTO(unitId, courseId, lectureId, "unit", "desc", "text", null).toPropertyMap());
        seedRow(weaviateService, SearchableEntitySchema.TypeValues.POST, otherPostId,
                new PostSearchableEntityDTO(otherPostId, otherCourseId, channelId, "title", "content").toPropertyMap());
        assertPostExistsInWeaviate(weaviateService, postId);
        assertLectureUnitExistsInWeaviate(weaviateService, unitId);
        assertPostExistsInWeaviate(weaviateService, otherPostId);

        searchableEntityWeaviateService.deleteAllForCourseAsync(courseId);

        assertPostNotInWeaviate(weaviateService, postId);
        assertLectureUnitNotInWeaviate(weaviateService, unitId);
        assertPostExistsInWeaviate(weaviateService, otherPostId);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testDeleteAllLectureUnitsForLecture_removesUnitsAndLeavesOthers() throws Exception {
        long courseId = 990500, lectureId = 990501, otherLectureId = 990509, unitId = 990502, otherUnitId = 990508;
        seedRow(weaviateService, SearchableEntitySchema.TypeValues.LECTURE_UNIT, unitId,
                new LectureUnitSearchableEntityDTO(unitId, courseId, lectureId, "unit", "desc", "text", null).toPropertyMap());
        seedRow(weaviateService, SearchableEntitySchema.TypeValues.LECTURE_UNIT, otherUnitId,
                new LectureUnitSearchableEntityDTO(otherUnitId, courseId, otherLectureId, "unit", "desc", "text", null).toPropertyMap());
        assertLectureUnitExistsInWeaviate(weaviateService, unitId);
        assertLectureUnitExistsInWeaviate(weaviateService, otherUnitId);

        searchableEntityWeaviateService.deleteAllLectureUnitsForLectureAsync(lectureId);

        assertLectureUnitNotInWeaviate(weaviateService, unitId);
        assertLectureUnitExistsInWeaviate(weaviateService, otherUnitId);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testBulkDelete_removesOlderRowsButSparesRowsWrittenAfterTheDelete() throws Exception {
        long courseId = 990600, channelId = 990601, stalePostId = 990602, newerPostId = 990603;
        // A stale row (no source_seq) stands in for a write that predates the delete; the fence must remove it.
        seedRow(weaviateService, SearchableEntitySchema.TypeValues.POST, stalePostId,
                new PostSearchableEntityDTO(stalePostId, courseId, channelId, "stale", "content").toPropertyMap());
        // A row written by an upsert enqueued after the delete carries a source_seq larger than the delete's own
        // outbox id; the fence must spare it. 9_000_000_000 is far larger than any outbox id assigned in this test.
        Map<String, Object> newerProperties = new HashMap<>(new PostSearchableEntityDTO(newerPostId, courseId, channelId, "newer", "content").toPropertyMap());
        newerProperties.put(SearchableEntitySchema.Properties.SOURCE_SEQ, 9_000_000_000L);
        seedRow(weaviateService, SearchableEntitySchema.TypeValues.POST, newerPostId, newerProperties);
        assertPostExistsInWeaviate(weaviateService, stalePostId);
        assertPostExistsInWeaviate(weaviateService, newerPostId);

        searchableEntityWeaviateService.deleteAllPostsForCourseAsync(courseId);

        // The delete drains the stale row; asserting it is gone first guarantees the delete has run.
        assertPostNotInWeaviate(weaviateService, stalePostId);
        // The newer row was written after the delete was enqueued, so the fence must have left it untouched.
        assertPostExistsInWeaviate(weaviateService, newerPostId);
    }

    private boolean hasOutboxRowFor(String type, long entityId) {
        return outboxRepository.findAll().stream().anyMatch(entry -> type.equals(entry.getEntityType()) && isEntity(entry, entityId));
    }

    private static boolean isEntity(WeaviateOutboxEntry entry, long entityId) {
        return entry.getEntityId() != null && entry.getEntityId() == entityId;
    }
}
