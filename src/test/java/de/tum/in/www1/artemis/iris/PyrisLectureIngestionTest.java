package de.tum.in.www1.artemis.iris;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.util.LinkedMultiValueMap;

import de.tum.in.www1.artemis.course.CourseUtilService;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.Lecture;
import de.tum.in.www1.artemis.domain.iris.settings.IrisCourseSettings;
import de.tum.in.www1.artemis.domain.lecture.AttachmentUnit;
import de.tum.in.www1.artemis.lecture.LectureUtilService;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.LectureRepository;
import de.tum.in.www1.artemis.repository.iris.IrisSettingsRepository;
import de.tum.in.www1.artemis.service.connectors.pyris.PyrisJobService;
import de.tum.in.www1.artemis.service.connectors.pyris.PyrisStatusUpdateService;
import de.tum.in.www1.artemis.service.connectors.pyris.PyrisWebhookService;
import de.tum.in.www1.artemis.service.connectors.pyris.dto.lectureingestionwebhook.PyrisLectureIngestionStatusUpdateDTO;
import de.tum.in.www1.artemis.service.connectors.pyris.dto.status.PyrisStageDTO;
import de.tum.in.www1.artemis.service.connectors.pyris.dto.status.PyrisStageState;
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
    protected PyrisStatusUpdateService pyrisStatusUpdateService;

    @Autowired
    protected PyrisJobService pyrisJobService;

    @Autowired
    protected IrisSettingsRepository irisSettingsRepository;

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

        int numberOfSlides = 2;
        AttachmentUnit attachmentUnitWithSlides = lectureUtilService.createAttachmentUnitWithSlidesAndFile(numberOfSlides);
        lecture1 = lectureUtilService.addLectureUnitsToLecture(lecture1, List.of(attachmentUnitWithSlides));
        this.lecture1 = lectureRepository.findByIdWithLectureUnitsAndAttachmentsElseThrow(lecture1.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testaddLectureToPyrisDatabaseWithCourseSettingsDisabled() {
        activateIrisFor(lecture1.getCourse());
        IrisCourseSettings courseSettings = irisSettingsService.getRawIrisSettingsFor(lecture1.getCourse());
        courseSettings.getIrisLectureIngestionSettings().setEnabled(false);
        this.irisSettingsRepository.save(courseSettings);
        irisRequestMockProvider.mockIngestionWebhookRunResponse(dto -> {
            assertThat(dto.settings().authenticationToken()).isNotNull();
        });
        if (lecture1.getLectureUnits().getFirst() instanceof AttachmentUnit attachmentUnit) {
            String jobToken = pyrisWebhookService.addLectureToPyrisDB(List.of(attachmentUnit));
            assertThat(jobToken).isNull();
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testdeleteLectureToPyrisDatabaseWithCourseSettingsDisabled() {
        activateIrisFor(lecture1.getCourse());
        IrisCourseSettings courseSettings = irisSettingsService.getRawIrisSettingsFor(lecture1.getCourse());
        courseSettings.getIrisLectureIngestionSettings().setEnabled(false);
        this.irisSettingsRepository.save(courseSettings);
        irisRequestMockProvider.mockIngestionWebhookRunResponse(dto -> {
            assertThat(dto.settings().authenticationToken()).isNotNull();
        });
        if (lecture1.getLectureUnits().getFirst() instanceof AttachmentUnit attachmentUnit) {
            String jobToken = pyrisWebhookService.deleteLectureFromPyrisDB(List.of(attachmentUnit));
            assertThat(jobToken).isNull();
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testaddLectureToPyrisDBAddJobWithCourseSettingsEnabled() {
        activateIrisFor(lecture1.getCourse());
        irisRequestMockProvider.mockIngestionWebhookRunResponse(dto -> {
            assertThat(dto.settings().authenticationToken()).isNotNull();
        });
        if (lecture1.getLectureUnits().getFirst() instanceof AttachmentUnit attachmentUnit) {
            String jobToken = pyrisWebhookService.addLectureToPyrisDB(List.of(attachmentUnit));
            assertThat(jobToken).isNotNull();
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testAllStagesDoneRemovesAdditionIngestionJob() throws Exception {
        activateIrisFor(lecture1.getCourse());
        irisRequestMockProvider.mockIngestionWebhookRunResponse(dto -> {
            assertThat(dto.settings().authenticationToken()).isNotNull();
        });
        if (lecture1.getLectureUnits().getFirst() instanceof AttachmentUnit attachmentUnit) {
            String jobToken = pyrisWebhookService.addLectureToPyrisDB(List.of(attachmentUnit));
            PyrisStageDTO doneStage = new PyrisStageDTO("done", 1, PyrisStageState.DONE, "Stage completed successfully.");
            PyrisLectureIngestionStatusUpdateDTO statusUpdate = new PyrisLectureIngestionStatusUpdateDTO("Success", List.of(doneStage));
            var headers = new HttpHeaders(new LinkedMultiValueMap<>(Map.of("Authorization", List.of("Bearer " + jobToken))));
            request.postWithoutResponseBody("/api/public/pyris/webhooks/ingestion/runs/" + jobToken + "/status", statusUpdate, HttpStatus.OK, headers);
            assertThat(pyrisJobService.getJob(jobToken)).isNull();
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testAllStagesDoneRemovesDeletionIngestionJob() throws Exception {
        activateIrisFor(lecture1.getCourse());
        irisRequestMockProvider.mockIngestionWebhookRunResponse(dto -> {
            assertThat(dto.settings().authenticationToken()).isNotNull();
        });
        if (lecture1.getLectureUnits().getFirst() instanceof AttachmentUnit attachmentUnit) {
            String jobToken = pyrisWebhookService.deleteLectureFromPyrisDB(List.of(attachmentUnit));
            PyrisStageDTO doneStage = new PyrisStageDTO("done", 1, PyrisStageState.DONE, "Stage completed successfully.");
            PyrisLectureIngestionStatusUpdateDTO statusUpdate = new PyrisLectureIngestionStatusUpdateDTO("Success", List.of(doneStage));
            var headers = new HttpHeaders(new LinkedMultiValueMap<>(Map.of("Authorization", List.of("Bearer " + jobToken))));
            request.postWithoutResponseBody("/api/public/pyris/webhooks/ingestion/runs/" + jobToken + "/status", statusUpdate, HttpStatus.OK, headers);
            assertThat(pyrisJobService.getJob(jobToken)).isNull();
        }
    }

    @Test
    void testStageNotDoneKeepsAdditionIngestionJob() throws Exception {
        activateIrisFor(lecture1.getCourse());
        irisRequestMockProvider.mockIngestionWebhookRunResponse(dto -> {
            assertThat(dto.settings().authenticationToken()).isNotNull();
        });
        if (lecture1.getLectureUnits().getFirst() instanceof AttachmentUnit attachmentUnit) {
            String jobToken = pyrisWebhookService.addLectureToPyrisDB(List.of(attachmentUnit));
            PyrisStageDTO doneStage = new PyrisStageDTO("done", 1, PyrisStageState.DONE, "Stage completed successfully.");
            PyrisStageDTO inProgressStage = new PyrisStageDTO("inProgressStage", 1, PyrisStageState.IN_PROGRESS, "Stage completed successfully.");
            PyrisLectureIngestionStatusUpdateDTO statusUpdate = new PyrisLectureIngestionStatusUpdateDTO("Success", List.of(doneStage, inProgressStage));
            var headers = new HttpHeaders(new LinkedMultiValueMap<>(Map.of("Authorization", List.of("Bearer " + jobToken))));
            request.postWithoutResponseBody("/api/public/pyris/webhooks/ingestion/runs/" + jobToken + "/status", statusUpdate, HttpStatus.OK, headers);
            assertThat(pyrisJobService.getJob(jobToken)).isNotNull();
        }
    }

    @Test
    void testStageNotDoneKeepsDeletionIngetionJob() throws Exception {
        activateIrisFor(lecture1.getCourse());
        irisRequestMockProvider.mockIngestionWebhookRunResponse(dto -> {
            assertThat(dto.settings().authenticationToken()).isNotNull();
        });
        if (lecture1.getLectureUnits().getFirst() instanceof AttachmentUnit attachmentUnit) {
            String jobToken = pyrisWebhookService.deleteLectureFromPyrisDB(List.of(attachmentUnit));
            PyrisStageDTO doneStage = new PyrisStageDTO("done", 1, PyrisStageState.DONE, "Stage completed successfully.");
            PyrisStageDTO inProgressStage = new PyrisStageDTO("inProgressStage", 1, PyrisStageState.IN_PROGRESS, "Stage completed successfully.");
            PyrisLectureIngestionStatusUpdateDTO statusUpdate = new PyrisLectureIngestionStatusUpdateDTO("Success", List.of(doneStage, inProgressStage));
            var headers = new HttpHeaders(new LinkedMultiValueMap<>(Map.of("Authorization", List.of("Bearer " + jobToken))));
            request.postWithoutResponseBody("/api/public/pyris/webhooks/ingestion/runs/" + jobToken + "/status", statusUpdate, HttpStatus.OK, headers);
            assertThat(pyrisJobService.getJob(jobToken)).isNotNull();
        }
    }

    @Test
    void testErrorStageRemovesDeletionIngetionJob() throws Exception {
        activateIrisFor(lecture1.getCourse());
        irisRequestMockProvider.mockIngestionWebhookRunResponse(dto -> {
            assertThat(dto.settings().authenticationToken()).isNotNull();
        });
        if (lecture1.getLectureUnits().getFirst() instanceof AttachmentUnit attachmentUnit) {
            String jobToken = pyrisWebhookService.deleteLectureFromPyrisDB(List.of(attachmentUnit));
            PyrisStageDTO errorStage = new PyrisStageDTO("error", 1, PyrisStageState.ERROR, "Stage not broke due to error.");
            PyrisLectureIngestionStatusUpdateDTO statusUpdate = new PyrisLectureIngestionStatusUpdateDTO("Success", List.of(errorStage));
            var headers = new HttpHeaders(new LinkedMultiValueMap<>(Map.of("Authorization", List.of("Bearer " + jobToken))));
            request.postWithoutResponseBody("/api/public/pyris/webhooks/ingestion/runs/" + jobToken + "/status", statusUpdate, HttpStatus.OK, headers);
            assertThat(pyrisJobService.getJob(jobToken)).isNull();
        }
    }

    @Test
    void testErrorStageRemovesAdditionIngetionJob() throws Exception {
        activateIrisFor(lecture1.getCourse());
        irisRequestMockProvider.mockIngestionWebhookRunResponse(dto -> {
            assertThat(dto.settings().authenticationToken()).isNotNull();
        });
        if (lecture1.getLectureUnits().getFirst() instanceof AttachmentUnit attachmentUnit) {
            String jobToken = pyrisWebhookService.addLectureToPyrisDB(List.of(attachmentUnit));
            PyrisStageDTO errorStage = new PyrisStageDTO("error", 1, PyrisStageState.ERROR, "Stage not broke due to error.");
            PyrisLectureIngestionStatusUpdateDTO statusUpdate = new PyrisLectureIngestionStatusUpdateDTO("Success", List.of(errorStage));
            var headers = new HttpHeaders(new LinkedMultiValueMap<>(Map.of("Authorization", List.of("Bearer " + jobToken))));
            request.postWithoutResponseBody("/api/public/pyris/webhooks/ingestion/runs/" + jobToken + "/status", statusUpdate, HttpStatus.OK, headers);
            assertThat(pyrisJobService.getJob(jobToken)).isNull();
        }
    }
}
