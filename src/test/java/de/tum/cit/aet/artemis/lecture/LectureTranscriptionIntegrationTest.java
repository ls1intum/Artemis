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

    // saveCheckpointIfTokenMatches's row lock blocks on this. H2 gives up after one second by default, so raise its
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

    /** A checkpoint of the run that owns the unit updates the stored transcript. */
    @Test
    void testSaveCheckpointIfTokenMatches_updatesWhenTokenMatches() {
        LectureUnitProcessingState state = new LectureUnitProcessingState(lectureUnit);
        state.setIngestionJobToken("valid-token");
        processingStateRepository.save(state);
        LectureTranscription saved = lectureTranscriptionRepository
                .save(new LectureTranscription("en", List.of(new LectureTranscriptionSegment(0.0, 10.0, "Original text", 1)), lectureUnit));

        var updatedSegments = List.of(new LectureTranscriptionSegment(0.0, 5.0, "Updated text", 1), new LectureTranscriptionSegment(5.0, 10.0, "More updated text", 2));
        boolean written = lectureTranscriptionRepository.saveCheckpointIfTokenMatches(lectureUnit, "valid-token", "de", updatedSegments, TranscriptionStatus.COMPLETED);

        assertThat(written).as("the owning run's checkpoint is written").isTrue();
        LectureTranscription reloaded = lectureTranscriptionRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getLanguage()).isEqualTo("de");
        assertThat(reloaded.getTranscriptionStatus()).isEqualTo(TranscriptionStatus.COMPLETED);
        assertThat(reloaded.getSegments()).hasSize(2);
        assertThat(reloaded.getSegments().get(0).text()).isEqualTo("Updated text");
        assertThat(reloaded.getSegments().get(1).text()).isEqualTo("More updated text");
    }

    /**
     * A lease reclaim or retry keeps the transcription row and starts a newer run on it, so the row still exists when a delayed
     * checkpoint of the superseded run arrives. Its stale token must leave the newer run's transcript untouched.
     */
    @Test
    void testSaveCheckpointIfTokenMatches_leavesTheNewerRunsTranscriptAlone() {
        LectureUnitProcessingState state = new LectureUnitProcessingState(lectureUnit);
        state.setIngestionJobToken("newer-run-token");
        processingStateRepository.save(state);
        LectureTranscription kept = lectureTranscriptionRepository
                .save(new LectureTranscription("en", List.of(new LectureTranscriptionSegment(0.0, 10.0, "Newer run text", 1)), lectureUnit));

        boolean written = lectureTranscriptionRepository.saveCheckpointIfTokenMatches(lectureUnit, "superseded-run-token", "de",
                List.of(new LectureTranscriptionSegment(0.0, 10.0, "Stale run text", 1)), TranscriptionStatus.COMPLETED);

        assertThat(written).as("a superseded run's checkpoint is refused").isFalse();
        LectureTranscription reloaded = lectureTranscriptionRepository.findById(kept.getId()).orElseThrow();
        assertThat(reloaded.getLanguage()).isEqualTo("en");
        assertThat(reloaded.getSegments().getFirst().text()).isEqualTo("Newer run text");
    }

    /**
     * Within one run the token cannot tell a raw checkpoint from the enriched one. A raw (PENDING) write delayed past the enriched write
     * must not replace the completed transcript, while an enriched write over a raw transcript still applies.
     */
    @Test
    void testSaveCheckpointIfTokenMatches_neverReplacesACompletedTranscriptWithAPendingOne() {
        LectureUnitProcessingState state = new LectureUnitProcessingState(lectureUnit);
        state.setIngestionJobToken("run-token");
        processingStateRepository.save(state);
        LectureTranscription stored = lectureTranscriptionRepository
                .save(new LectureTranscription("en", List.of(new LectureTranscriptionSegment(0.0, 10.0, "Raw text", 0)), lectureUnit));

        assertThat(lectureTranscriptionRepository.saveCheckpointIfTokenMatches(lectureUnit, "run-token", "en",
                List.of(new LectureTranscriptionSegment(0.0, 10.0, "Enriched text", 1)), TranscriptionStatus.COMPLETED)).as("enriched over raw applies").isTrue();
        assertThat(lectureTranscriptionRepository.saveCheckpointIfTokenMatches(lectureUnit, "run-token", "en",
                List.of(new LectureTranscriptionSegment(0.0, 10.0, "Late raw text", 0)), TranscriptionStatus.PENDING))
                .as("a late raw write over the completed transcript is refused").isFalse();

        LectureTranscription reloaded = lectureTranscriptionRepository.findById(stored.getId()).orElseThrow();
        assertThat(reloaded.getTranscriptionStatus()).isEqualTo(TranscriptionStatus.COMPLETED);
        assertThat(reloaded.getSegments().getFirst().text()).isEqualTo("Enriched text");
        assertThat(processingStateRepository.findById(state.getId())).as("the guarded writes leave the processing state untouched").isPresent();
    }

    /** The first checkpoint of the owning run creates the transcript, with its segments round-tripping through the JSON column. */
    @Test
    void testSaveCheckpointIfTokenMatches_insertsTheFirstTranscriptWhenTokenMatches() {
        LectureUnitProcessingState state = new LectureUnitProcessingState(lectureUnit);
        state.setIngestionJobToken("valid-token");
        processingStateRepository.save(state);

        var segments = List.of(new LectureTranscriptionSegment(0.0, 10.0, "Inserted text", 1), new LectureTranscriptionSegment(10.0, 20.0, "More text", 2));
        boolean written = lectureTranscriptionRepository.saveCheckpointIfTokenMatches(lectureUnit, "valid-token", "en", segments, TranscriptionStatus.COMPLETED);

        assertThat(written).isTrue();
        LectureTranscription created = lectureTranscriptionRepository.findByLectureUnit_Id(lectureUnit.getId()).orElseThrow();
        assertThat(created.getLanguage()).isEqualTo("en");
        assertThat(created.getTranscriptionStatus()).isEqualTo(TranscriptionStatus.COMPLETED);
        assertThat(created.getSegments()).hasSize(2);
        assertThat(created.getSegments().get(0).text()).isEqualTo("Inserted text");
        assertThat(created.getSegments().get(1).text()).isEqualTo("More text");
    }

    /** A content-triggered requeue invalidated the token before the first checkpoint arrived: nothing may be created for the old run. */
    @Test
    void testSaveCheckpointIfTokenMatches_createsNothingWhenTokenDoesNotMatch() {
        LectureUnitProcessingState state = new LectureUnitProcessingState(lectureUnit);
        state.setIngestionJobToken("current-token");
        processingStateRepository.save(state);

        boolean written = lectureTranscriptionRepository.saveCheckpointIfTokenMatches(lectureUnit, "stale-token", "en",
                List.of(new LectureTranscriptionSegment(0.0, 10.0, "Stale text", 1)), TranscriptionStatus.COMPLETED);

        assertThat(written).isFalse();
        assertThat(lectureTranscriptionRepository.findByLectureUnit_Id(lectureUnit.getId())).isEmpty();
    }

    @Test
    void testSaveCheckpointIfTokenMatches_createsNothingWithoutAProcessingState() {
        boolean written = lectureTranscriptionRepository.saveCheckpointIfTokenMatches(lectureUnit, "any-token", "en",
                List.of(new LectureTranscriptionSegment(0.0, 10.0, "Stale text", 1)), TranscriptionStatus.COMPLETED);

        assertThat(written).isFalse();
        assertThat(lectureTranscriptionRepository.findByLectureUnit_Id(lectureUnit.getId())).isEmpty();
    }

    /**
     * The window a check before the write cannot cover: an invalidation that holds the processing-state row lock, not yet committed,
     * when the checkpoint arrives. The checkpoint must block on that lock rather than race past it, and once the invalidation commits it
     * must see the new token and write nothing.
     */
    @Test
    void testSaveCheckpointIfTokenMatches_blocksOnAndThenObservesAConcurrentInvalidation() throws Exception {
        LectureUnitProcessingState state = new LectureUnitProcessingState(lectureUnit);
        state.setIngestionJobToken("in-flight-token");
        processingStateRepository.save(state);
        long stateId = state.getId();

        var holderHasLock = new CountDownLatch(1);
        var releaseHolder = new CountDownLatch(1);
        var holder = Executors.newSingleThreadExecutor();
        var writer = Executors.newSingleThreadExecutor();
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

            var writeResult = writer.submit(() -> lectureTranscriptionRepository.saveCheckpointIfTokenMatches(lectureUnit, "in-flight-token", "en",
                    List.of(new LectureTranscriptionSegment(0.0, 10.0, "Should not persist", 1)), TranscriptionStatus.COMPLETED));

            assertThatThrownBy(() -> writeResult.get(2, TimeUnit.SECONDS)).as("the checkpoint must block while the processing-state row is locked by the invalidation")
                    .isInstanceOf(TimeoutException.class);

            releaseHolder.countDown();
            assertThat(holding.get(30, TimeUnit.SECONDS)).isEqualTo(1);
            assertThat(writeResult.get(30, TimeUnit.SECONDS)).as("once unblocked, the checkpoint sees the invalidated token").isFalse();
        }
        finally {
            releaseHolder.countDown();
            holder.shutdownNow();
            writer.shutdownNow();
        }

        assertThat(lectureTranscriptionRepository.findByLectureUnit_Id(lectureUnit.getId())).isEmpty();
    }

    /** Two first checkpoints of the same run arriving together are serialized by the lock instead of colliding on the unique unit column. */
    @Test
    void testSaveCheckpointIfTokenMatches_serializesConcurrentFirstCheckpoints() throws Exception {
        LectureUnitProcessingState state = new LectureUnitProcessingState(lectureUnit);
        state.setIngestionJobToken("run-token");
        processingStateRepository.save(state);

        var start = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        try {
            var first = pool.submit(() -> {
                start.await(10, TimeUnit.SECONDS);
                return lectureTranscriptionRepository.saveCheckpointIfTokenMatches(lectureUnit, "run-token", "en", List.of(new LectureTranscriptionSegment(0.0, 10.0, "First", 0)),
                        TranscriptionStatus.PENDING);
            });
            var second = pool.submit(() -> {
                start.await(10, TimeUnit.SECONDS);
                return lectureTranscriptionRepository.saveCheckpointIfTokenMatches(lectureUnit, "run-token", "en", List.of(new LectureTranscriptionSegment(0.0, 10.0, "Second", 0)),
                        TranscriptionStatus.PENDING);
            });
            start.countDown();
            assertThat(first.get(30, TimeUnit.SECONDS)).as("the first checkpoint is written").isTrue();
            assertThat(second.get(30, TimeUnit.SECONDS)).as("the second checkpoint is written over it, not rejected by a constraint").isTrue();
        }
        finally {
            pool.shutdownNow();
        }

        assertThat(lectureTranscriptionRepository.findByLectureUnit_Id(lectureUnit.getId())).as("one transcript exists for the unit").isPresent();
    }
}
