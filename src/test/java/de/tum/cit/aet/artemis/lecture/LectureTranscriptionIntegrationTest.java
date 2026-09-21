package de.tum.cit.aet.artemis.lecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.account.util.UserUtilService;
import de.tum.cit.aet.artemis.core.util.CourseUtilService;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.lecture.domain.Lecture;
import de.tum.cit.aet.artemis.lecture.domain.LectureTranscription;
import de.tum.cit.aet.artemis.lecture.domain.LectureTranscriptionSegment;
import de.tum.cit.aet.artemis.lecture.domain.LectureUnit;
import de.tum.cit.aet.artemis.lecture.domain.TranscriptionStatus;
import de.tum.cit.aet.artemis.lecture.dto.LectureTranscriptionDTO;
import de.tum.cit.aet.artemis.lecture.repository.LectureTranscriptionRepository;
import de.tum.cit.aet.artemis.lecture.test_repository.LectureTestRepository;
import de.tum.cit.aet.artemis.lecture.util.LectureUtilService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentBatchTest;

class LectureTranscriptionIntegrationTest extends AbstractSpringIntegrationIndependentBatchTest {

    private static final String TEST_PREFIX = "pyristranscriptioncreationtest";

    @Autowired
    private LectureTranscriptionRepository lectureTranscriptionRepository;

    @Autowired
    private LectureTestRepository lectureRepository;

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private CourseUtilService courseUtilService;

    @Autowired
    private LectureUtilService lectureUtilService;

    private Lecture lecture;

    private LectureUnit lectureUnit;

    @BeforeEach
    void initTestCase() throws Exception {
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
}
