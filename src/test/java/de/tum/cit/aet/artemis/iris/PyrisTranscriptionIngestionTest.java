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
import de.tum.cit.aet.artemis.lecture.repository.LectureRepository;
import de.tum.cit.aet.artemis.lecture.repository.LectureTranscriptionRepository;

class PyrisLectureTranscriptionIngestionTest extends AbstractIrisIntegrationTest {

    private static final String TEST_PREFIX = "pyristranscriptioningestiontest";

    @Autowired
    private LectureTranscriptionRepository lecturetranscriptionRepository;

    @Autowired
    private LectureRepository lectureRepository;

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private CourseUtilService courseUtilService;

    private Lecture lecture1;

    @BeforeEach
    void initTestCase() throws Exception {
        userUtilService.addUsers(TEST_PREFIX, 1, 1, 0, 1);
        List<Course> courses = courseUtilService.createCoursesWithExercisesAndLectures(TEST_PREFIX, true, true, 1);
        Course course1 = this.courseRepository.findByIdWithExercisesAndExerciseDetailsAndLecturesElseThrow(courses.getFirst().getId());
        this.lecture1 = course1.getLectures().stream().findFirst().orElseThrow();
        this.lecture1.setTitle("Lecture " + lecture1.getId()); // needed for search by title
        this.lecture1 = lectureRepository.save(this.lecture1);
        // Add users that are not in the course
        userUtilService.createAndSaveUser(TEST_PREFIX + "student42");
        userUtilService.createAndSaveUser(TEST_PREFIX + "tutor42");
        userUtilService.createAndSaveUser(TEST_PREFIX + "instructor42");

        LectureTranscriptionSegment segment1 = new LectureTranscriptionSegment(0.0, 12.0, "Welcome to today's lecture", null, 1);
        LectureTranscriptionSegment segment2 = new LectureTranscriptionSegment(0.0, 12.0, "Today we will talk about Artemis", null, 1);
        LectureTranscription transcription = new LectureTranscription(this.lecture1, "en", List.of(new LectureTranscriptionSegment[] { segment1, segment2 }));

        lecturetranscriptionRepository.save(transcription);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testIngestTranscriptionInPyrisWithLectureId() throws Exception {
        activateIrisFor(lecture1.getCourse());
        irisRequestMockProvider.mockTranscriptionIngestionWebhookRunResponse(dto -> {
            assertThat(dto.settings().authenticationToken()).isNotNull();
        });
        request.put("/api/courses/" + lecture1.getCourse().getId() + "/lectures/" + lecture1.getId() + "/ingest-transcription", Optional.empty(), HttpStatus.OK);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testIngestTranscriptionWithInvalidLectureId() throws Exception {
        activateIrisFor(lecture1.getCourse());
        request.put("/api/courses/" + lecture1.getCourse().getId() + "/lectures/" + 9999L + "/ingest-transcription", Optional.empty(), HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "STUDENT")
    void testIngestTranscriptionInPyrisWithoutPermission() throws Exception {
        activateIrisFor(lecture1.getCourse());
        irisRequestMockProvider.mockTranscriptionIngestionWebhookRunResponse(dto -> {
            assertThat(dto.settings().authenticationToken()).isNotNull();
        });
        request.put("/api/courses/" + lecture1.getCourse().getId() + "/lectures/" + lecture1.getId() + "/ingest-transcription", Optional.empty(), HttpStatus.FORBIDDEN);
    }

}
