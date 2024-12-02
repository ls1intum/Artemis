package de.tum.cit.aet.artemis.iris;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.util.LinkedMultiValueMap;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.user.util.UserUtilService;
import de.tum.cit.aet.artemis.core.util.CourseUtilService;
import de.tum.cit.aet.artemis.iris.domain.settings.IrisCourseSettings;
import de.tum.cit.aet.artemis.iris.repository.IrisSettingsRepository;
import de.tum.cit.aet.artemis.iris.service.pyris.PyrisJobService;
import de.tum.cit.aet.artemis.iris.service.pyris.PyrisStatusUpdateService;
import de.tum.cit.aet.artemis.iris.service.pyris.PyrisWebhookService;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.lectureingestionwebhook.PyrisLectureIngestionStatusUpdateDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.status.PyrisStageDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.status.PyrisStageState;
import de.tum.cit.aet.artemis.lecture.domain.Attachment;
import de.tum.cit.aet.artemis.lecture.domain.AttachmentUnit;
import de.tum.cit.aet.artemis.lecture.domain.Lecture;
import de.tum.cit.aet.artemis.lecture.repository.LectureRepository;
import de.tum.cit.aet.artemis.lecture.repository.LectureUnitRepository;
import de.tum.cit.aet.artemis.lecture.util.LectureFactory;
import de.tum.cit.aet.artemis.lecture.util.LectureUtilService;

class PyrisLectureIngestionTest extends AbstractIrisIntegrationTest {

    private static final String TEST_PREFIX = "pyrislectureingestiontest";

    @Autowired
    private PyrisWebhookService pyrisWebhookService;

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

    @Autowired
    protected LectureUnitRepository lectureUnitRepository;

    private Attachment attachment;

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
        AttachmentUnit pdfAttachmentUnitWithSlides = lectureUtilService.createAttachmentUnitWithSlidesAndFile(numberOfSlides, true);
        AttachmentUnit imageAttachmentUnitWithSlides = lectureUtilService.createAttachmentUnitWithSlidesAndFile(numberOfSlides, false);
        lecture1 = lectureUtilService.addLectureUnitsToLecture(lecture1, List.of(pdfAttachmentUnitWithSlides, imageAttachmentUnitWithSlides));
        this.lecture1 = lectureRepository.findByIdWithLectureUnitsAndAttachmentsElseThrow(lecture1.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void autoIngestionWhenAttachmentUnitCreatedAndAutoUpdateEnabled() {
        this.attachment = LectureFactory.generateAttachment(null);
        this.attachment.setName("          LoremIpsum              ");
        this.attachment.setLink("/api/files/temp/example.txt");
        this.lecture1 = lectureUtilService.createCourseWithLecture(true);
        AttachmentUnit attachmentUnit = new AttachmentUnit();
        attachmentUnit.setDescription("Lorem Ipsum");
        activateIrisFor(lecture1.getCourse());
        IrisCourseSettings courseSettings = irisSettingsService.getRawIrisSettingsFor(lecture1.getCourse());
        courseSettings.getIrisLectureIngestionSettings().setAutoIngestOnLectureAttachmentUpload(true);
        this.irisSettingsRepository.save(courseSettings);
        irisRequestMockProvider.mockIngestionWebhookRunResponse(dto -> {
            assertThat(dto.settings().authenticationToken()).isNotNull();
        });
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void noAutoIngestionWhenAttachmentUnitCreatedAndAutoUpdateDisabled() {
        this.attachment = LectureFactory.generateAttachment(null);
        this.attachment.setName("          LoremIpsum              ");
        this.attachment.setLink("/api/files/temp/example.txt");
        this.lecture1 = lectureUtilService.createCourseWithLecture(true);
        AttachmentUnit attachmentUnit = new AttachmentUnit();
        attachmentUnit.setDescription("Lorem Ipsum");
        irisRequestMockProvider.mockIngestionWebhookRunResponse(dto -> {
            assertThat(dto.settings().authenticationToken()).isNotNull();
        });
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testIngestLecturesButtonInPyris() throws Exception {
        activateIrisFor(lecture1.getCourse());
        irisRequestMockProvider.mockIngestionWebhookRunResponse(dto -> {
            assertThat(dto.settings().authenticationToken()).isNotNull();
        });
        request.postWithResponseBody("/api/courses/" + lecture1.getCourse().getId() + "/ingest", Optional.empty(), boolean.class, HttpStatus.OK);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testIngestLectureUnitButtonInPyris() {
        activateIrisFor(lecture1.getCourse());
        irisRequestMockProvider.mockIngestionWebhookRunResponse(dto -> {
            assertThat(dto.settings().authenticationToken()).isNotNull();
        });

    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testDeleteLecturefromPyrisDatabaseWithCourseSettingsEnabled() {
        activateIrisFor(lecture1.getCourse());
        IrisCourseSettings courseSettings = irisSettingsService.getRawIrisSettingsFor(lecture1.getCourse());
        this.irisSettingsRepository.save(courseSettings);
        irisRequestMockProvider.mockDeletionWebhookRunResponse(dto -> {
            assertThat(dto.settings().authenticationToken()).isNotNull();
        });
        String jobToken = pyrisWebhookService.deleteLectureFromPyrisDB(List.of((AttachmentUnit) lecture1.getLectureUnits().getFirst()));
        assertThat(jobToken).isNotNull();
        jobToken = pyrisWebhookService.deleteLectureFromPyrisDB(List.of((AttachmentUnit) lecture1.getLectureUnits().getLast()));
        assertThat(jobToken).isNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testAddLectureToPyrisDBAddJobWithCourseSettingsEnabled() throws Exception {
        activateIrisFor(lecture1.getCourse());
        irisRequestMockProvider.mockIngestionWebhookRunResponse(dto -> {
            assertThat(dto.settings().authenticationToken()).isNotNull();
        });
        request.postWithResponseBody("/api/courses/" + lecture1.getCourse().getId() + "/ingest", Optional.empty(), boolean.class, HttpStatus.OK);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testAllStagesDoneIngestionStateDone() throws Exception {
        activateIrisFor(lecture1.getCourse());
        irisRequestMockProvider.mockIngestionWebhookRunResponse(dto -> {
            assertThat(dto.settings().authenticationToken()).isNotNull();
        });
        if (lecture1.getLectureUnits().getFirst() instanceof AttachmentUnit unit) {
            String jobToken = pyrisWebhookService.addLectureUnitToPyrisDB(unit);
            PyrisStageDTO doneStage = new PyrisStageDTO("done", 1, PyrisStageState.DONE, "Stage completed successfully.");
            PyrisLectureIngestionStatusUpdateDTO statusUpdate = new PyrisLectureIngestionStatusUpdateDTO("Success", List.of(doneStage),
                    lecture1.getLectureUnits().getFirst().getId());
            var headers = new HttpHeaders(new LinkedMultiValueMap<>(Map.of("Authorization", List.of("Bearer " + jobToken))));
            request.postWithoutResponseBody("/api/public/pyris/webhooks/ingestion/runs/" + jobToken + "/status", statusUpdate, HttpStatus.OK, headers);
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testAllStagesDoneRemovesDeletionIngestionJob() throws Exception {
        activateIrisFor(lecture1.getCourse());
        irisRequestMockProvider.mockDeletionWebhookRunResponse(dto -> {
            assertThat(dto.settings().authenticationToken()).isNotNull();
        });
        if (lecture1.getLectureUnits().getFirst() instanceof AttachmentUnit unit) {
            String jobToken = pyrisWebhookService.deleteLectureFromPyrisDB(List.of(unit));
            PyrisStageDTO doneStage = new PyrisStageDTO("done", 1, PyrisStageState.DONE, "Stage completed successfully.");
            PyrisLectureIngestionStatusUpdateDTO statusUpdate = new PyrisLectureIngestionStatusUpdateDTO("Success", List.of(doneStage),
                    lecture1.getLectureUnits().getFirst().getId());
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
        if (lecture1.getLectureUnits().getFirst() instanceof AttachmentUnit unit) {
            String jobToken = pyrisWebhookService.addLectureUnitToPyrisDB(unit);
            PyrisStageDTO doneStage = new PyrisStageDTO("done", 1, PyrisStageState.DONE, "Stage completed successfully.");
            PyrisStageDTO inProgressStage = new PyrisStageDTO("inProgressStage", 1, PyrisStageState.IN_PROGRESS, "Stage completed successfully.");
            PyrisLectureIngestionStatusUpdateDTO statusUpdate = new PyrisLectureIngestionStatusUpdateDTO("Success", List.of(doneStage, inProgressStage),
                    lecture1.getLectureUnits().getFirst().getId());
            var headers = new HttpHeaders(new LinkedMultiValueMap<>(Map.of("Authorization", List.of("Bearer " + jobToken))));
            request.postWithoutResponseBody("/api/public/pyris/webhooks/ingestion/runs/" + jobToken + "/status", statusUpdate, HttpStatus.OK, headers);
            assertThat(pyrisJobService.getJob(jobToken)).isNotNull();
        }
    }

    @Test
    void testStageNotDoneKeepsDeletionIngestionJob() throws Exception {
        activateIrisFor(lecture1.getCourse());
        irisRequestMockProvider.mockDeletionWebhookRunResponse(dto -> {
            assertThat(dto.settings().authenticationToken()).isNotNull();
        });
        if (lecture1.getLectureUnits().getFirst() instanceof AttachmentUnit unit) {
            String jobToken = pyrisWebhookService.deleteLectureFromPyrisDB(List.of(unit));
            PyrisStageDTO doneStage = new PyrisStageDTO("done", 1, PyrisStageState.DONE, "Stage completed successfully.");
            PyrisStageDTO inProgressStage = new PyrisStageDTO("inProgressStage", 1, PyrisStageState.IN_PROGRESS, "Stage completed successfully.");
            PyrisLectureIngestionStatusUpdateDTO statusUpdate = new PyrisLectureIngestionStatusUpdateDTO("Success", List.of(doneStage, inProgressStage),
                    lecture1.getLectureUnits().getFirst().getId());
            var headers = new HttpHeaders(new LinkedMultiValueMap<>(Map.of("Authorization", List.of("Bearer " + jobToken))));
            request.postWithoutResponseBody("/api/public/pyris/webhooks/ingestion/runs/" + jobToken + "/status", statusUpdate, HttpStatus.OK, headers);
            assertThat(pyrisJobService.getJob(jobToken)).isNotNull();
        }
    }

    @Test
    void testErrorStageRemovesDeletionIngestionJob() throws Exception {
        activateIrisFor(lecture1.getCourse());
        irisRequestMockProvider.mockDeletionWebhookRunResponse(dto -> {
            assertThat(dto.settings().authenticationToken()).isNotNull();
        });
        if (lecture1.getLectureUnits().getFirst() instanceof AttachmentUnit unit) {
            String jobToken = pyrisWebhookService.deleteLectureFromPyrisDB(List.of(unit));
            PyrisStageDTO errorStage = new PyrisStageDTO("error", 1, PyrisStageState.ERROR, "Stage not broke due to error.");
            PyrisLectureIngestionStatusUpdateDTO statusUpdate = new PyrisLectureIngestionStatusUpdateDTO("Success", List.of(errorStage),
                    lecture1.getLectureUnits().getFirst().getId());
            var headers = new HttpHeaders(new LinkedMultiValueMap<>(Map.of("Authorization", List.of("Bearer " + jobToken))));
            request.postWithoutResponseBody("/api/public/pyris/webhooks/ingestion/runs/" + jobToken + "/status", statusUpdate, HttpStatus.OK, headers);
            assertThat(pyrisJobService.getJob(jobToken)).isNull();
        }
    }

    @Test
    void testErrorStageRemovesAdditionIngestionJob() throws Exception {
        activateIrisFor(lecture1.getCourse());
        irisRequestMockProvider.mockIngestionWebhookRunResponse(dto -> {
            assertThat(dto.settings().authenticationToken()).isNotNull();
        });
        if (lecture1.getLectureUnits().getFirst() instanceof AttachmentUnit unit) {
            String jobToken = pyrisWebhookService.addLectureUnitToPyrisDB(unit);
            PyrisStageDTO errorStage = new PyrisStageDTO("error", 1, PyrisStageState.ERROR, "Stage not broke due to error.");
            PyrisLectureIngestionStatusUpdateDTO statusUpdate = new PyrisLectureIngestionStatusUpdateDTO("Success", List.of(errorStage),
                    lecture1.getLectureUnits().getFirst().getId());
            var headers = new HttpHeaders(new LinkedMultiValueMap<>(Map.of("Authorization", List.of("Bearer " + jobToken))));
            request.postWithoutResponseBody("/api/public/pyris/webhooks/ingestion/runs/" + jobToken + "/status", statusUpdate, HttpStatus.OK, headers);
            assertThat(pyrisJobService.getJob(jobToken)).isNull();
        }
    }

    @Test
    void testRunIdIngestionJob() throws Exception {
        activateIrisFor(lecture1.getCourse());
        irisRequestMockProvider.mockIngestionWebhookRunResponse(dto -> {
            assertThat(dto.settings().authenticationToken()).isNotNull();
        });
        String newJobToken = pyrisJobService.addIngestionWebhookJob(123L, lecture1.getId(), lecture1.getLectureUnits().getFirst().getId());
        String chatJobToken = pyrisJobService.addCourseChatJob(123L, 123L);
        PyrisStageDTO errorStage = new PyrisStageDTO("error", 1, PyrisStageState.ERROR, "Stage not broke due to error.");
        PyrisLectureIngestionStatusUpdateDTO statusUpdate = new PyrisLectureIngestionStatusUpdateDTO("Success", List.of(errorStage), lecture1.getLectureUnits().getFirst().getId());
        var headers = new HttpHeaders(new LinkedMultiValueMap<>(Map.of("Authorization", List.of("Bearer " + chatJobToken))));
        MockHttpServletResponse response = request.postWithoutResponseBody("/api/public/pyris/webhooks/ingestion/runs/" + newJobToken + "/status", statusUpdate,
                HttpStatus.CONFLICT, headers);
        assertThat(response.getContentAsString()).contains("Run ID in URL does not match run ID in request body");
        response = request.postWithoutResponseBody("/api/public/pyris/webhooks/ingestion/runs/" + chatJobToken + "/status", statusUpdate, HttpStatus.CONFLICT, headers);
        assertThat(response.getContentAsString()).contains("Run ID is not an ingestion job");
    }

    @Test
    void testIngestionJobDone() throws Exception {
        activateIrisFor(lecture1.getCourse());
        irisRequestMockProvider.mockIngestionWebhookRunResponse(dto -> {
            assertThat(dto.settings().authenticationToken()).isNotNull();
        });
        String newJobToken = pyrisJobService.addIngestionWebhookJob(123L, lecture1.getId(), lecture1.getLectureUnits().getFirst().getId());
        String chatJobToken = pyrisJobService.addCourseChatJob(123L, 123L);
        PyrisStageDTO errorStage = new PyrisStageDTO("error", 1, PyrisStageState.ERROR, "Stage not broke due to error.");
        PyrisLectureIngestionStatusUpdateDTO statusUpdate = new PyrisLectureIngestionStatusUpdateDTO("Success", List.of(errorStage), lecture1.getLectureUnits().getFirst().getId());
        var headers = new HttpHeaders(new LinkedMultiValueMap<>(Map.of("Authorization", List.of("Bearer " + chatJobToken))));
        MockHttpServletResponse response = request.postWithoutResponseBody("/api/public/pyris/webhooks/ingestion/runs/" + newJobToken + "/status", statusUpdate,
                HttpStatus.CONFLICT, headers);
        assertThat(response.getContentAsString()).contains("Run ID in URL does not match run ID in request body");
        response = request.postWithoutResponseBody("/api/public/pyris/webhooks/ingestion/runs/" + chatJobToken + "/status", statusUpdate, HttpStatus.CONFLICT, headers);
        assertThat(response.getContentAsString()).contains("Run ID is not an ingestion job");
    }
}
