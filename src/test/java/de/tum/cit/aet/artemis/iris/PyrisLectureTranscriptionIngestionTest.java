package de.tum.cit.aet.artemis.iris;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.user.util.UserUtilService;
import de.tum.cit.aet.artemis.core.util.CourseUtilService;
import de.tum.cit.aet.artemis.lecture.domain.Lecture;
import de.tum.cit.aet.artemis.lecture.domain.LectureTranscription;
import de.tum.cit.aet.artemis.lecture.domain.LectureTranscriptionSegment;
import de.tum.cit.aet.artemis.lecture.domain.LectureUnit;
import de.tum.cit.aet.artemis.lecture.repository.LectureRepository;
import de.tum.cit.aet.artemis.lecture.repository.LectureTranscriptionRepository;
import de.tum.cit.aet.artemis.lecture.repository.LectureUnitRepository;
import de.tum.cit.aet.artemis.lecture.util.LectureUtilService;

class PyrisLectureTranscriptionIngestionTest extends AbstractIrisIntegrationTest {

    private static final String TEST_PREFIX = "pyristranscriptioningestiontest";

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

    private Lecture lecture1;

    private LectureUnit lectureUnit;

    @BeforeEach
    void initTestCase() throws Exception {
        userUtilService.addUsers(TEST_PREFIX, 1, 1, 0, 1);
        List<Course> courses = courseUtilService.createCoursesWithExercisesAndLectures(TEST_PREFIX, true, true, 1);
        Course course1 = this.courseRepository.findByIdWithExercisesAndExerciseDetailsAndLecturesElseThrow(courses.getFirst().getId());
        this.lecture1 = course1.getLectures().stream().findFirst().orElseThrow();
        this.lecture1.setTitle("Lecture " + lecture1.getId()); // needed for search by title
        this.lecture1 = lectureRepository.save(this.lecture1);
        this.lectureUnit = lectureUtilService.createTextUnit();
        lectureUtilService.addLectureUnitsToLecture(lecture1, List.of(this.lectureUnit));

        // Add users that are not in the course
        userUtilService.createAndSaveUser(TEST_PREFIX + "student42");
        userUtilService.createAndSaveUser(TEST_PREFIX + "tutor42");
        userUtilService.createAndSaveUser(TEST_PREFIX + "instructor42");

        LectureTranscriptionSegment segment1 = new LectureTranscriptionSegment(0.0, 12.0, "Welcome to today's lecture", 1);
        LectureTranscriptionSegment segment2 = new LectureTranscriptionSegment(0.0, 12.0, "Today we will talk about Artemis", 1);
        LectureTranscription transcription = new LectureTranscription("en", List.of(new LectureTranscriptionSegment[] { segment1, segment2 }), this.lectureUnit);

        lectureTranscriptionRepository.save(transcription);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testIngestTranscriptionInPyrisWithLectureId() throws Exception {
        activateIrisFor(lecture1.getCourse());
        irisRequestMockProvider.mockTranscriptionIngestionWebhookRunResponse(dto -> {
            assertThat(dto.settings().authenticationToken()).isNotNull();
        });
        request.put("/api/courses/" + lecture1.getCourse().getId() + "/lecture/" + lecture1.getId() + "/lecture-unit/" + lectureUnit.getId() + "/ingest-transcription",
                Optional.empty(), HttpStatus.OK);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testIngestTranscriptionWithInvalidLectureId() throws Exception {
        activateIrisFor(lecture1.getCourse());
        request.put("/api/courses/" + lecture1.getCourse().getId() + "/lecture/" + 9999L + "/lecture-unit/" + lectureUnit.getId() + "/ingest-transcription", Optional.empty(),
                HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "STUDENT")
    void testIngestTranscriptionInPyrisWithoutPermission() throws Exception {
        activateIrisFor(lecture1.getCourse());
        irisRequestMockProvider.mockTranscriptionIngestionWebhookRunResponse(dto -> {
            assertThat(dto.settings().authenticationToken()).isNotNull();
        });
        request.put("/api/courses/" + lecture1.getCourse().getId() + "/lecture/" + lecture1.getId() + "/lecture-unit/" + lectureUnit.getId() + "/ingest-transcription",
                Optional.empty(), HttpStatus.FORBIDDEN);
    }

}
