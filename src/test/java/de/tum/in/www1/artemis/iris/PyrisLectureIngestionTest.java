package de.tum.in.www1.artemis.iris;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.course.CourseUtilService;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.Lecture;
import de.tum.in.www1.artemis.domain.lecture.AttachmentUnit;
import de.tum.in.www1.artemis.lecture.LectureUtilService;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.LectureRepository;
import de.tum.in.www1.artemis.service.connectors.pyris.PyrisJobService;
import de.tum.in.www1.artemis.service.connectors.pyris.PyrisWebhookService;
import de.tum.in.www1.artemis.user.UserUtilService;

class PyrisLectureIngestionTest extends AbstractIrisIntegrationTest {

    private static final String TEST_PREFIX = "pyrislectureingestiontest";

    @Autowired
    private PyrisWebhookService pyrisWebhookService;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private LectureRepository lectureRepository;

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private CourseUtilService courseUtilService;

    @Autowired
    private LectureUtilService lectureUtilService;

    @Autowired
    private PyrisJobService jobService;

    private Lecture lecture1;

    @BeforeEach
    void initTestCase() throws Exception {
        for (String job : jobService.getAllJobs().keySet()) {
            jobService.removeJob(job);
        }
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

        int numberOfSlides = 2;
        AttachmentUnit attachmentUnitWithSlides = lectureUtilService.createAttachmentUnitWithSlidesAndFile(numberOfSlides);
        lecture1 = lectureUtilService.addLectureUnitsToLecture(lecture1, List.of(attachmentUnitWithSlides));
        this.lecture1 = lectureRepository.findByIdWithLectureUnitsAndAttachmentsElseThrow(lecture1.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testPyrisLectureIngestionWebhookJob_isEmpty_When_subsettingsDisabled() throws Exception {
        Course course2 = courseUtilService.addEmptyCourse();
        courseUtilService.enableMessagingForCourse(course2);
        request.postWithResponseBody("/api/lectures/import/" + lecture1.getId() + "?courseId=" + course2.getId(), null, Lecture.class, HttpStatus.CREATED);
        if (lecture1.getLectureUnits().getFirst() instanceof AttachmentUnit attachmentUnit) {
            pyrisWebhookService.executeLectureIngestionPipeline(true, List.of(attachmentUnit));
        }
        Set<String> keySet = jobService.getAllJobs().keySet();
        assertThat(keySet).isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testExecuteLectureIngestionPipeline_isNotEmpty_When_subsettingsEnabled() throws Exception {
        Course course2 = courseUtilService.addEmptyCourse();
        courseUtilService.enableMessagingForCourse(course2);
        activateIrisFor(course2);
        irisRequestMockProvider.mockIngestionWebhookRunResponse(dto -> {
            assertThat(dto.settings().authenticationToken()).isNotNull();
        });
        request.postWithResponseBody("/api/lectures/import/" + lecture1.getId() + "?courseId=" + course2.getId(), null, Lecture.class, HttpStatus.CREATED);
        if (lecture1.getLectureUnits().getFirst() instanceof AttachmentUnit attachmentUnit) {
            pyrisWebhookService.executeLectureIngestionPipeline(true, List.of(attachmentUnit));
        }
        Set<String> keySet = jobService.getAllJobs().keySet();
        assertThat(keySet).isNotEmpty();
    }

}
