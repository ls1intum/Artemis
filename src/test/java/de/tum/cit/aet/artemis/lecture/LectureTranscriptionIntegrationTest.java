package de.tum.cit.aet.artemis.lecture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.SQLException;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import de.tum.cit.aet.artemis.account.util.UserUtilService;
import de.tum.cit.aet.artemis.core.util.CourseUtilService;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.lecture.domain.Lecture;
import de.tum.cit.aet.artemis.lecture.domain.LectureTranscription;
import de.tum.cit.aet.artemis.lecture.domain.LectureTranscriptionSegment;
import de.tum.cit.aet.artemis.lecture.domain.LectureTranscriptionSegmentConverter;
import de.tum.cit.aet.artemis.lecture.domain.LectureUnit;
import de.tum.cit.aet.artemis.lecture.domain.LectureUnitProcessingState;
import de.tum.cit.aet.artemis.lecture.domain.TranscriptionStatus;
import de.tum.cit.aet.artemis.lecture.dto.LectureTranscriptionDTO;
import de.tum.cit.aet.artemis.lecture.repository.LectureTranscriptionRepository;
import de.tum.cit.aet.artemis.lecture.repository.LectureUnitProcessingStateRepository;
import de.tum.cit.aet.artemis.lecture.test_repository.LectureTestRepository;
import de.tum.cit.aet.artemis.lecture.util.LectureUtilService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentBatchTest;

class LectureTranscriptionIntegrationTest extends AbstractSpringIntegrationIndependentBatchTest {

    private static final String TEST_PREFIX = "pyristranscriptioncreationtest";

    @Autowired
    private LectureTranscriptionRepository lectureTranscriptionRepository;

    @Autowired
    private LectureUnitProcessingStateRepository processingStateRepository;

    @Autowired
    private LectureTestRepository lectureRepository;

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private CourseUtilService courseUtilService;

    @Autowired
    private LectureUtilService lectureUtilService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private DataSource dataSource;

    private Lecture lecture;

    private LectureUnit lectureUnit;

    @BeforeEach
    void initTestCase() throws Exception {
        raiseLockTimeoutOnH2();
        userUtilService.addUsers(TEST_PREFIX, 2, 2, 0, 2);
        List<Course> courses = courseUtilService.createEnrolledCoursesWithExercisesAndLectures(TEST_PREFIX, true, 1);
        Course course = this.courseRepository.findByIdWithExercisesAndExerciseDetailsAndLecturesElseThrow(courses.getFirst().getId());
        this.lecture = course.getLectures().stream().findFirst().orElseThrow();
        this.lecture.setTitle("Lecture " + lecture.getId());
        this.lecture = lectureRepository.save(this.lecture);
        this.lectureUnit = lectureUtilService.createAttachmentVideoUnit(lecture, false);
        lectureUtilService.addLectureUnitsToLecture(lecture, List.of(this.lectureUnit));
        userUtilService.createAndSaveUser(TEST_PREFIX + "outsider");
    }

    // insertIfTokenMatches's FOR UPDATE blocks on this. H2 gives up after one second by default, so raise its
    // limit rather than loosen the assertion; a no-op on the other engines.
    private void raiseLockTimeoutOnH2() throws SQLException {
        try (var connection = dataSource.getConnection()) {
            if (!connection.getMetaData().getURL().startsWith("jdbc:h2:")) {
                return;
            }
            try (var statement = connection.createStatement()) {
                statement.execute("SET DEFAULT_LOCK_TIMEOUT 10000");
            }
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetLectureTranscription_success() throws Exception {
        var segments = List.of(new LectureTranscriptionSegment(0.0, 10.0, "Welcome to Artemis", 1), new LectureTranscriptionSegment(10.0, 20.0, "Lecture Transcription test", 2));
        lectureTranscriptionRepository.save(new LectureTranscription("en", segments, lectureUnit));

        LectureTranscriptionDTO retrieved = request.get("/api/lecture/lecture-units/" + lectureUnit.getId() + "/transcript", HttpStatus.OK, LectureTranscriptionDTO.class);
        assertThat(retrieved).isNotNull();
        assertThat(retrieved.language()).isEqualTo("en");
        assertThat(retrieved.segments()).hasSize(2);
        assertThat(retrieved.segments().getFirst().text()).isEqualTo("Welcome to Artemis");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetLectureTranscription_notFound() throws Exception {
        request.get("/api/lecture/lecture-units/" + lectureUnit.getId() + "/transcript", HttpStatus.NOT_FOUND, LectureTranscriptionDTO.class);
    }

    /**
     * Students are the audience for a transcript, and the endpoint says so with
     * {@code @EnforceAtLeastStudentInLectureUnit}, so a student enrolled in the course reads it like an instructor does.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetLectureTranscription_allowedForEnrolledStudent() throws Exception {
        var segments = List.of(new LectureTranscriptionSegment(0.0, 10.0, "Welcome to Artemis", 1));
        lectureTranscriptionRepository.save(new LectureTranscription("en", segments, lectureUnit));

        LectureTranscriptionDTO retrieved = request.get("/api/lecture/lecture-units/" + lectureUnit.getId() + "/transcript", HttpStatus.OK, LectureTranscriptionDTO.class);
        assertThat(retrieved).isNotNull();
        assertThat(retrieved.segments()).hasSize(1);
        assertThat(retrieved.segments().getFirst().text()).isEqualTo("Welcome to Artemis");
    }

    /**
     * What the endpoint does refuse: a user who holds no role in the course the lecture unit belongs to.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "outsider", roles = "USER")
    void testGetLectureTranscription_forbiddenForUserWithoutCourseRole() throws Exception {
        request.get("/api/lecture/lecture-units/" + lectureUnit.getId() + "/transcript", HttpStatus.FORBIDDEN, LectureTranscriptionDTO.class);
    }

    /**
     * Verifies updateContentIfExists's bulk UPDATE against a real database: the {@code segments}
     * column is {@code @JdbcTypeCode(SqlTypes.JSON)} with no existing bulk-update precedent
     * elsewhere in this codebase, so this confirms the converter applies correctly in a JPQL SET
     * clause bind, not just in the ordinary entity-save path every other test here exercises.
     */
    @Test
    void testUpdateContentIfExists_appliesWhenRowExists() {
        var originalSegments = List.of(new LectureTranscriptionSegment(0.0, 10.0, "Original text", 1));
        LectureTranscription saved = lectureTranscriptionRepository.save(new LectureTranscription("en", originalSegments, lectureUnit));

        var updatedSegments = List.of(new LectureTranscriptionSegment(0.0, 5.0, "Updated text", 1), new LectureTranscriptionSegment(5.0, 10.0, "More updated text", 2));
        int updated = lectureTranscriptionRepository.updateContentIfExists(saved.getId(), "de", updatedSegments, TranscriptionStatus.COMPLETED);

        assertThat(updated).isEqualTo(1);
        LectureTranscription reloaded = lectureTranscriptionRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getLanguage()).isEqualTo("de");
        assertThat(reloaded.getTranscriptionStatus()).isEqualTo(TranscriptionStatus.COMPLETED);
        assertThat(reloaded.getSegments()).hasSize(2);
        assertThat(reloaded.getSegments().get(0).text()).isEqualTo("Updated text");
        assertThat(reloaded.getSegments().get(1).text()).isEqualTo("More updated text");
    }

    /**
     * The race updateContentIfExists exists to close: a content-triggered requeue deletes the row
     * between a checkpoint's read and its write. The conditional update must no-op, not resurrect it.
     */
    @Test
    void testUpdateContentIfExists_noOpsWhenRowWasDeleted() {
        var segments = List.of(new LectureTranscriptionSegment(0.0, 10.0, "Original text", 1));
        LectureTranscription saved = lectureTranscriptionRepository.save(new LectureTranscription("en", segments, lectureUnit));
        Long deletedId = saved.getId();
        lectureTranscriptionRepository.delete(saved);

        int updated = lectureTranscriptionRepository.updateContentIfExists(deletedId, "de", segments, TranscriptionStatus.COMPLETED);

        assertThat(updated).isZero();
        assertThat(lectureTranscriptionRepository.findById(deletedId)).isEmpty();
    }

    /**
     * Verifies insertIfTokenMatches against a real database: the first native INSERT in this
     * codebase, and the only place a bulk write binds a value into the {@code json}-typed
     * {@code segments} column via an explicit {@code CAST(... AS json)} rather than through
     * Hibernate's own converter/type layer. Confirms the cast round-trips content correctly and
     * that the EXISTS-gated insert actually applies when the token matches.
     */
    @Test
    void testInsertIfTokenMatches_insertsWhenTokenMatches() {
        LectureUnitProcessingState state = new LectureUnitProcessingState(lectureUnit);
        state.setIngestionJobToken("valid-token");
        processingStateRepository.save(state);

        var segments = List.of(new LectureTranscriptionSegment(0.0, 10.0, "Inserted text", 1), new LectureTranscriptionSegment(10.0, 20.0, "More text", 2));
        String segmentsJson = new LectureTranscriptionSegmentConverter().convertToDatabaseColumn(segments);

        int inserted = lectureTranscriptionRepository.insertIfTokenMatches(lectureUnit.getId(), "en", segmentsJson, TranscriptionStatus.COMPLETED.name(), "valid-token");

        assertThat(inserted).isEqualTo(1);
        LectureTranscription created = lectureTranscriptionRepository.findByLectureUnit_Id(lectureUnit.getId()).orElseThrow();
        assertThat(created.getLanguage()).isEqualTo("en");
        assertThat(created.getTranscriptionStatus()).isEqualTo(TranscriptionStatus.COMPLETED);
        assertThat(created.getSegments()).hasSize(2);
        assertThat(created.getSegments().get(0).text()).isEqualTo("Inserted text");
        assertThat(created.getSegments().get(1).text()).isEqualTo("More text");
    }

    /**
     * The exact interleaving this atomic insert exists to close: ownership was proven at some
     * earlier instant, but the token has since changed (a content-triggered requeue invalidated it)
     * by the time this statement actually executes. Folding the check into the insert itself --
     * rather than a separate read before it -- means there is no gap left for that change to land in.
     */
    @Test
    void testInsertIfTokenMatches_noOpsWhenTokenDoesNotMatch() {
        LectureUnitProcessingState state = new LectureUnitProcessingState(lectureUnit);
        state.setIngestionJobToken("current-token");
        processingStateRepository.save(state);

        var segments = List.of(new LectureTranscriptionSegment(0.0, 10.0, "Stale text", 1));
        String segmentsJson = new LectureTranscriptionSegmentConverter().convertToDatabaseColumn(segments);

        int inserted = lectureTranscriptionRepository.insertIfTokenMatches(lectureUnit.getId(), "en", segmentsJson, TranscriptionStatus.COMPLETED.name(), "stale-token");

        assertThat(inserted).isZero();
        assertThat(lectureTranscriptionRepository.findByLectureUnit_Id(lectureUnit.getId())).isEmpty();
    }

    @Test
    void testInsertIfTokenMatches_noOpsWhenNoProcessingStateExists() {
        var segments = List.of(new LectureTranscriptionSegment(0.0, 10.0, "Stale text", 1));
        String segmentsJson = new LectureTranscriptionSegmentConverter().convertToDatabaseColumn(segments);

        int inserted = lectureTranscriptionRepository.insertIfTokenMatches(lectureUnit.getId(), "en", segmentsJson, TranscriptionStatus.COMPLETED.name(), "any-token");

        assertThat(inserted).isZero();
        assertThat(lectureTranscriptionRepository.findByLectureUnit_Id(lectureUnit.getId())).isEmpty();
    }

    /**
     * The window a plain {@code EXISTS} subquery cannot cover: a concurrent invalidation that commits
     * strictly between this statement's snapshot and its own commit, rather than before it starts. The
     * mismatch tests above change the token before {@code insertIfTokenMatches} runs at all, so they
     * cannot exercise this. Here, a transaction that has already taken {@code invalidateTokenIfMatches}'s
     * row lock -- but not yet committed it -- must make a concurrent {@code insertIfTokenMatches} block,
     * not race past it on a stale snapshot; and once that lock is released, the insert must observe the
     * now-invalidated token and skip, not resurrect a row the invalidation was about to orphan.
     */
    @Test
    void testInsertIfTokenMatches_blocksOnAndThenObservesAConcurrentInvalidation() throws Exception {
        LectureUnitProcessingState state = new LectureUnitProcessingState(lectureUnit);
        state.setIngestionJobToken("in-flight-token");
        processingStateRepository.save(state);
        long stateId = state.getId();

        var segments = List.of(new LectureTranscriptionSegment(0.0, 10.0, "Should not persist", 1));
        String segmentsJson = new LectureTranscriptionSegmentConverter().convertToDatabaseColumn(segments);

        var holderHasLock = new CountDownLatch(1);
        var releaseHolder = new CountDownLatch(1);
        var holder = Executors.newSingleThreadExecutor();
        var inserter = Executors.newSingleThreadExecutor();
        try {
            // One transaction invalidates the token and holds the processing-state row's lock open until released.
            var holding = holder.submit(() -> new TransactionTemplate(transactionManager).execute(status -> {
                int invalidated = processingStateRepository.invalidateTokenIfMatches(stateId, "in-flight-token", ZonedDateTime.now());
                holderHasLock.countDown();
                try {
                    releaseHolder.await(30, TimeUnit.SECONDS);
                }
                catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return invalidated;
            }));
            assertThat(holderHasLock.await(10, TimeUnit.SECONDS)).isTrue();

            var insertResult = inserter.submit(
                    () -> lectureTranscriptionRepository.insertIfTokenMatches(lectureUnit.getId(), "en", segmentsJson, TranscriptionStatus.COMPLETED.name(), "in-flight-token"));

            // The assertion that makes this a test about the lock: without FOR UPDATE, the insert completes here,
            // on a snapshot taken before the invalidation committed.
            assertThatThrownBy(() -> insertResult.get(2, TimeUnit.SECONDS)).as("the insert must block while the processing-state row is locked by the invalidation")
                    .isInstanceOf(TimeoutException.class);

            releaseHolder.countDown();
            assertThat(holding.get(30, TimeUnit.SECONDS)).isEqualTo(1);
            // Once unblocked, the insert re-checks the row's now-current (invalidated) token and must skip.
            assertThat(insertResult.get(30, TimeUnit.SECONDS)).isZero();
        }
        finally {
            releaseHolder.countDown();
            holder.shutdownNow();
            inserter.shutdownNow();
        }

        assertThat(lectureTranscriptionRepository.findByLectureUnit_Id(lectureUnit.getId())).isEmpty();
    }
}
