package de.tum.cit.aet.artemis.lecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.user.util.UserUtilService;
import de.tum.cit.aet.artemis.core.util.CourseUtilService;
import de.tum.cit.aet.artemis.lecture.domain.Lecture;
import de.tum.cit.aet.artemis.lecture.domain.LectureTranscriptionSegment;
import de.tum.cit.aet.artemis.lecture.domain.LectureUnit;
import de.tum.cit.aet.artemis.lecture.dto.LectureTranscriptionDTO;
import de.tum.cit.aet.artemis.lecture.repository.LectureRepository;
import de.tum.cit.aet.artemis.lecture.repository.LectureTranscriptionRepository;
import de.tum.cit.aet.artemis.lecture.repository.LectureUnitRepository;
import de.tum.cit.aet.artemis.lecture.util.LectureUtilService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

class LectureTranscriptionIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "pyristranscriptioncreationtest";

    @Autowired
    private LectureTranscriptionRepository lectureTranscriptionRepository;

    @Autowired
    private LectureRepository lectureRepository;

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private CourseUtilService courseUtilService;

    @Autowired
    private LectureUtilService lectureUtilService;

    @Autowired
    private LectureUnitRepository lectureUnitRepository;

    private Lecture lecture;

    private LectureUnit lectureUnit;

    @BeforeEach
    void initTestCase() throws Exception {
        userUtilService.addUsers(TEST_PREFIX, 2, 2, 0, 2);
        List<Course> courses = courseUtilService.createCoursesWithExercisesAndLectures(TEST_PREFIX, true, true, 1);
        Course course = this.courseRepository.findByIdWithExercisesAndExerciseDetailsAndLecturesElseThrow(courses.getFirst().getId());
        this.lecture = course.getLectures().stream().findFirst().orElseThrow();
        this.lecture.setTitle("Lecture " + lecture.getId());
        this.lecture = lectureRepository.save(this.lecture);
        this.lectureUnit = lectureUtilService.createVideoUnit();
        lectureUtilService.addLectureUnitsToLecture(lecture, List.of(this.lectureUnit));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "admin1", roles = "ADMIN")
    void testCreateLectureTranscription_success() throws Exception {
        LectureTranscriptionDTO transcriptionDTO = new LectureTranscriptionDTO(lectureUnit.getId(), "en",
                List.of(new LectureTranscriptionSegment(0.0, 10.0, "Welcome to Artemis", 1), new LectureTranscriptionSegment(10.0, 20.0, "Lecture Transcription test", 2)));

        var response = request.postWithResponseBody("/api/lecture/" + lecture.getId() + "/lecture-unit/" + lectureUnit.getId() + "/transcription", transcriptionDTO,
                LectureTranscriptionDTO.class, HttpStatus.CREATED);

        assertThat(response).isNotNull();
        assertThat(response.language()).isEqualTo("en");
        assertThat(response.segments()).hasSize(2);
        assertThat(response.segments().getFirst().text()).isEqualTo("Welcome to Artemis");

        var savedTranscription = lectureTranscriptionRepository.findByLectureUnit_Id(lectureUnit.getId());
        assertThat(savedTranscription).isPresent();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "admin1", roles = "ADMIN")
    void testCreateLectureTranscription_invalidLectureId() throws Exception {
        LectureTranscriptionDTO transcriptionDTO = new LectureTranscriptionDTO(this.lectureUnit.getId(), "en",
                List.of(new LectureTranscriptionSegment(0.0, 10.0, "Invalid Lecture ID Test", 1)));

        request.post("/api/lecture/" + 9999L + "/lecture-unit/" + lectureUnit.getId() + "/transcription", transcriptionDTO, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "STUDENT")
    void testCreateLectureTranscription_forbiddenForStudent() throws Exception {
        LectureTranscriptionDTO transcriptionDTO = new LectureTranscriptionDTO(this.lectureUnit.getId(), "en",
                List.of(new LectureTranscriptionSegment(0.0, 10.0, "Student Permission Test", 1)));

        request.post("/api/lecture/" + lecture.getId() + "/lecture-unit/" + lectureUnit.getId() + "/transcription", transcriptionDTO, HttpStatus.FORBIDDEN);
    }
}
