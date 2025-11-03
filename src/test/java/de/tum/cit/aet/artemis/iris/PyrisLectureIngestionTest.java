package de.tum.cit.aet.artemis.iris;

import static de.tum.cit.aet.artemis.core.config.Constants.ARTEMIS_FILE_PATH_PREFIX;
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
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.LinkedMultiValueMap;

import de.tum.cit.aet.artemis.core.config.Constants;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.user.util.UserUtilService;
import de.tum.cit.aet.artemis.core.util.CourseUtilService;
import de.tum.cit.aet.artemis.iris.service.pyris.PyrisJobService;
import de.tum.cit.aet.artemis.iris.service.pyris.PyrisWebhookService;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.lectureingestionwebhook.PyrisLectureIngestionStatusUpdateDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.status.PyrisStageDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.status.PyrisStageState;
import de.tum.cit.aet.artemis.lecture.domain.Attachment;
import de.tum.cit.aet.artemis.lecture.domain.AttachmentVideoUnit;
import de.tum.cit.aet.artemis.lecture.domain.Lecture;
import de.tum.cit.aet.artemis.lecture.domain.LectureTranscription;
import de.tum.cit.aet.artemis.lecture.domain.LectureTranscriptionSegment;
import de.tum.cit.aet.artemis.lecture.repository.LectureTranscriptionRepository;
import de.tum.cit.aet.artemis.lecture.test_repository.AttachmentVideoUnitTestRepository;
import de.tum.cit.aet.artemis.lecture.test_repository.LectureTestRepository;
import de.tum.cit.aet.artemis.lecture.util.LectureFactory;
import de.tum.cit.aet.artemis.lecture.util.LectureUtilService;

class PyrisLectureIngestionTest extends AbstractIrisIntegrationTest {

    private static final String TEST_PREFIX = "pyrislectureingestiontest";

    @Autowired
    private PyrisWebhookService pyrisWebhookService;

    @Autowired
    private LectureTestRepository lectureRepository;

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private CourseUtilService courseUtilService;

    @Autowired
    private LectureUtilService lectureUtilService;

    @Autowired
    protected PyrisJobService pyrisJobService;

    private Attachment attachment;

    private Lecture lecture1;

    @Autowired
    private AttachmentVideoUnitTestRepository attachmentVideoUnitTestRepository;

    @Autowired
    private LectureTranscriptionRepository lectureTranscriptionRepository;

    @BeforeEach
    void initTestCase() throws Exception {
        userUtilService.addUsers(TEST_PREFIX, 2, 1, 0, 1);
        List<Course> courses = courseUtilService.createCoursesWithExercisesAndLectures(TEST_PREFIX, true, true, 1);
        Course course1 = this.courseRepository.findByIdWithExercisesAndExerciseDetailsAndLecturesElseThrow(courses.getFirst().getId());
        this.lecture1 = course1.getLectures().stream().findFirst().orElseThrow();
        this.lecture1.setTitle("Lecture " + lecture1.getId()); // needed for search by title
        this.lecture1 = lectureRepository.save(this.lecture1);
        // Add users that are not in the course
        userUtilService.createAndSaveUser(TEST_PREFIX + "student42");
        userUtilService.createAndSaveUser(TEST_PREFIX + "tutor42");
        userUtilService.createAndSaveUser(TEST_PREFIX + "instructor42");
        activateIrisGlobally();

        int numberOfSlides = 2;
        AttachmentVideoUnit pdfAttachmentVideoUnitWithSlides = lectureUtilService.createAttachmentVideoUnitWithSlidesAndFile(numberOfSlides, true);
        AttachmentVideoUnit imageAttachmentVideoUnitWithSlides = lectureUtilService.createAttachmentVideoUnitWithSlidesAndFile(numberOfSlides, false);
        lecture1 = lectureUtilService.addLectureUnitsToLecture(lecture1, List.of(pdfAttachmentVideoUnitWithSlides, imageAttachmentVideoUnitWithSlides));
        this.lecture1 = lectureRepository.findByIdWithLectureUnitsAndAttachmentsElseThrow(lecture1.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void autoIngestionWhenAttachmentVideoUnitCreatedAndAutoUpdateEnabled() {
        this.attachment = LectureFactory.generateAttachment(null);
        this.attachment.setName("          LoremIpsum              ");
        this.attachment.setLink("temp/example.txt");
        this.lecture1 = lectureUtilService.createCourseWithLecture(true);
        AttachmentVideoUnit attachmentVideoUnit = new AttachmentVideoUnit();
        attachmentVideoUnit.setDescription("Lorem Ipsum");
        enableIrisFor(lecture1.getCourse());
        irisRequestMockProvider.mockIngestionWebhookRunResponse(dto -> assertThat(dto.settings().authenticationToken()).isNotNull());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void noAutoIngestionWhenAttachmentVideoUnitCreatedAndAutoUpdateDisabled() {
        this.attachment = LectureFactory.generateAttachment(null);
        this.attachment.setName("          LoremIpsum              ");
        this.attachment.setLink("temp/example.txt");
        this.lecture1 = lectureUtilService.createCourseWithLecture(true);
        AttachmentVideoUnit attachmentVideoUnit = new AttachmentVideoUnit();
        attachmentVideoUnit.setDescription("Lorem Ipsum");
        irisRequestMockProvider.mockIngestionWebhookRunResponse(dto -> assertThat(dto.settings().authenticationToken()).isNotNull());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testIngestLecturesButtonInPyris() throws Exception {
        enableIrisFor(lecture1.getCourse());
        irisRequestMockProvider.mockIngestionWebhookRunResponse(dto -> assertThat(dto.settings().authenticationToken()).isNotNull());
        request.postWithResponseBody("/api/lecture/courses/" + lecture1.getCourse().getId() + "/ingest", Optional.empty(), boolean.class, HttpStatus.OK);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testIngestLectureUnitButtonInPyris() {
        activateIrisFor(lecture1.getCourse());
        irisRequestMockProvider.mockIngestionWebhookRunResponse(dto -> assertThat(dto.settings().authenticationToken()).isNotNull());

    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testDeleteLecturefromPyrisDatabaseWithCourseSettingsEnabled() {
        enableIrisFor(lecture1.getCourse());
        irisRequestMockProvider.mockDeletionWebhookRunResponse(dto -> assertThat(dto.settings().authenticationToken()).isNotNull());
        String jobToken = pyrisWebhookService.deleteLectureFromPyrisDB(List.of((AttachmentVideoUnit) lecture1.getLectureUnits().getFirst()));
        assertThat(jobToken).isNotNull();
        jobToken = pyrisWebhookService.deleteLectureFromPyrisDB(List.of((AttachmentVideoUnit) lecture1.getLectureUnits().getLast()));
        assertThat(jobToken).isNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testAddLectureToPyrisDBAddJobWithCourseSettingsEnabled() throws Exception {
        activateIrisFor(lecture1.getCourse());
        irisRequestMockProvider.mockIngestionWebhookRunResponse(dto -> assertThat(dto.settings().authenticationToken()).isNotNull());
        request.postWithResponseBody("/api/lecture/courses/" + lecture1.getCourse().getId() + "/ingest", Optional.empty(), boolean.class, HttpStatus.OK);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testAllStagesDoneIngestionStateDone() throws Exception {
        activateIrisFor(lecture1.getCourse());
        irisRequestMockProvider.mockIngestionWebhookRunResponse(dto -> assertThat(dto.settings().authenticationToken()).isNotNull());
        if (lecture1.getLectureUnits().getFirst() instanceof AttachmentVideoUnit unit) {
            String jobToken = pyrisWebhookService.addLectureUnitToPyrisDB(unit);
            PyrisStageDTO doneStage = new PyrisStageDTO("done", 1, PyrisStageState.DONE, "Stage completed successfully.", false);
            PyrisLectureIngestionStatusUpdateDTO statusUpdate = new PyrisLectureIngestionStatusUpdateDTO("Success", List.of(doneStage),
                    lecture1.getLectureUnits().getFirst().getId());
            var headers = new HttpHeaders(new LinkedMultiValueMap<>(Map.of(HttpHeaders.AUTHORIZATION, List.of(Constants.BEARER_PREFIX + jobToken))));
            request.postWithoutResponseBody("/api/iris/internal/webhooks/ingestion/runs/" + jobToken + "/status", statusUpdate, HttpStatus.OK, headers);
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testAllStagesDoneRemovesDeletionIngestionJob() throws Exception {
        activateIrisFor(lecture1.getCourse());
        irisRequestMockProvider.mockDeletionWebhookRunResponse(dto -> assertThat(dto.settings().authenticationToken()).isNotNull());
        if (lecture1.getLectureUnits().getFirst() instanceof AttachmentVideoUnit unit) {
            String jobToken = pyrisWebhookService.deleteLectureFromPyrisDB(List.of(unit));
            PyrisStageDTO doneStage = new PyrisStageDTO("done", 1, PyrisStageState.DONE, "Stage completed successfully.", false);
            PyrisLectureIngestionStatusUpdateDTO statusUpdate = new PyrisLectureIngestionStatusUpdateDTO("Success", List.of(doneStage),
                    lecture1.getLectureUnits().getFirst().getId());
            var headers = new HttpHeaders(new LinkedMultiValueMap<>(Map.of(HttpHeaders.AUTHORIZATION, List.of(Constants.BEARER_PREFIX + jobToken))));
            request.postWithoutResponseBody("/api/iris/internal/webhooks/ingestion/runs/" + jobToken + "/status", statusUpdate, HttpStatus.OK, headers);
            assertThat(pyrisJobService.getJob(jobToken)).isNull();
        }
    }

    @Test
    void testStageNotDoneKeepsAdditionIngestionJob() throws Exception {
        activateIrisFor(lecture1.getCourse());
        irisRequestMockProvider.mockIngestionWebhookRunResponse(dto -> assertThat(dto.settings().authenticationToken()).isNotNull());
        if (lecture1.getLectureUnits().getFirst() instanceof AttachmentVideoUnit unit) {
            String jobToken = pyrisWebhookService.addLectureUnitToPyrisDB(unit);
            PyrisStageDTO doneStage = new PyrisStageDTO("done", 1, PyrisStageState.DONE, "Stage completed successfully.", false);
            PyrisStageDTO inProgressStage = new PyrisStageDTO("inProgressStage", 1, PyrisStageState.IN_PROGRESS, "Stage completed successfully.", false);
            PyrisLectureIngestionStatusUpdateDTO statusUpdate = new PyrisLectureIngestionStatusUpdateDTO("Success", List.of(doneStage, inProgressStage),
                    lecture1.getLectureUnits().getFirst().getId());
            var headers = new HttpHeaders(new LinkedMultiValueMap<>(Map.of(HttpHeaders.AUTHORIZATION, List.of(Constants.BEARER_PREFIX + jobToken))));
            request.postWithoutResponseBody("/api/iris/internal/webhooks/ingestion/runs/" + jobToken + "/status", statusUpdate, HttpStatus.OK, headers);
            assertThat(pyrisJobService.getJob(jobToken)).isNotNull();
        }
    }

    @Test
    void testStageNotDoneKeepsDeletionIngestionJob() throws Exception {
        activateIrisFor(lecture1.getCourse());
        irisRequestMockProvider.mockDeletionWebhookRunResponse(dto -> assertThat(dto.settings().authenticationToken()).isNotNull());
        if (lecture1.getLectureUnits().getFirst() instanceof AttachmentVideoUnit unit) {
            String jobToken = pyrisWebhookService.deleteLectureFromPyrisDB(List.of(unit));
            PyrisStageDTO doneStage = new PyrisStageDTO("done", 1, PyrisStageState.DONE, "Stage completed successfully.", false);
            PyrisStageDTO inProgressStage = new PyrisStageDTO("inProgressStage", 1, PyrisStageState.IN_PROGRESS, "Stage completed successfully.", false);
            PyrisLectureIngestionStatusUpdateDTO statusUpdate = new PyrisLectureIngestionStatusUpdateDTO("Success", List.of(doneStage, inProgressStage),
                    lecture1.getLectureUnits().getFirst().getId());
            var headers = new HttpHeaders(new LinkedMultiValueMap<>(Map.of(HttpHeaders.AUTHORIZATION, List.of(Constants.BEARER_PREFIX + jobToken))));
            request.postWithoutResponseBody("/api/iris/internal/webhooks/ingestion/runs/" + jobToken + "/status", statusUpdate, HttpStatus.OK, headers);
            assertThat(pyrisJobService.getJob(jobToken)).isNotNull();
        }
    }

    @Test
    void testErrorStageRemovesDeletionIngestionJob() throws Exception {
        activateIrisFor(lecture1.getCourse());
        irisRequestMockProvider.mockDeletionWebhookRunResponse(dto -> assertThat(dto.settings().authenticationToken()).isNotNull());
        if (lecture1.getLectureUnits().getFirst() instanceof AttachmentVideoUnit unit) {
            String jobToken = pyrisWebhookService.deleteLectureFromPyrisDB(List.of(unit));
            PyrisStageDTO errorStage = new PyrisStageDTO("error", 1, PyrisStageState.ERROR, "Stage not broke due to error.", false);
            PyrisLectureIngestionStatusUpdateDTO statusUpdate = new PyrisLectureIngestionStatusUpdateDTO("Success", List.of(errorStage),
                    lecture1.getLectureUnits().getFirst().getId());
            var headers = new HttpHeaders(new LinkedMultiValueMap<>(Map.of(HttpHeaders.AUTHORIZATION, List.of(Constants.BEARER_PREFIX + jobToken))));
            request.postWithoutResponseBody("/api/iris/internal/webhooks/ingestion/runs/" + jobToken + "/status", statusUpdate, HttpStatus.OK, headers);
            assertThat(pyrisJobService.getJob(jobToken)).isNull();
        }
    }

    @Test
    void testErrorStageRemovesAdditionIngestionJob() throws Exception {
        activateIrisFor(lecture1.getCourse());
        irisRequestMockProvider.mockIngestionWebhookRunResponse(dto -> assertThat(dto.settings().authenticationToken()).isNotNull());
        if (lecture1.getLectureUnits().getFirst() instanceof AttachmentVideoUnit unit) {
            String jobToken = pyrisWebhookService.addLectureUnitToPyrisDB(unit);
            PyrisStageDTO errorStage = new PyrisStageDTO("error", 1, PyrisStageState.ERROR, "Stage not broke due to error.", false);
            PyrisLectureIngestionStatusUpdateDTO statusUpdate = new PyrisLectureIngestionStatusUpdateDTO("Success", List.of(errorStage),
                    lecture1.getLectureUnits().getFirst().getId());
            var headers = new HttpHeaders(new LinkedMultiValueMap<>(Map.of(HttpHeaders.AUTHORIZATION, List.of(Constants.BEARER_PREFIX + jobToken))));
            request.postWithoutResponseBody("/api/iris/internal/webhooks/ingestion/runs/" + jobToken + "/status", statusUpdate, HttpStatus.OK, headers);
            assertThat(pyrisJobService.getJob(jobToken)).isNull();
        }
    }

    @Test
    void testRunIdIngestionJob() throws Exception {
        activateIrisFor(lecture1.getCourse());
        irisRequestMockProvider.mockIngestionWebhookRunResponse(dto -> assertThat(dto.settings().authenticationToken()).isNotNull());
        String newJobToken = pyrisJobService.addLectureIngestionWebhookJob(123L, lecture1.getId(), lecture1.getLectureUnits().getFirst().getId());
        String chatJobToken = pyrisJobService.addCourseChatJob(123L, 123L, 123L);
        PyrisStageDTO errorStage = new PyrisStageDTO("error", 1, PyrisStageState.ERROR, "Stage not broke due to error.", false);
        PyrisLectureIngestionStatusUpdateDTO statusUpdate = new PyrisLectureIngestionStatusUpdateDTO("Success", List.of(errorStage), lecture1.getLectureUnits().getFirst().getId());
        var headers = new HttpHeaders(new LinkedMultiValueMap<>(Map.of(HttpHeaders.AUTHORIZATION, List.of(Constants.BEARER_PREFIX + chatJobToken))));
        MockHttpServletResponse response = request.postWithoutResponseBody("/api/iris/internal/webhooks/ingestion/runs/" + newJobToken + "/status", statusUpdate,
                HttpStatus.CONFLICT, headers);
        assertThat(response.getContentAsString()).contains("Run ID in URL does not match run ID in request body");
        response = request.postWithoutResponseBody("/api/iris/internal/webhooks/ingestion/runs/" + chatJobToken + "/status", statusUpdate, HttpStatus.CONFLICT, headers);
        assertThat(response.getContentAsString()).contains("Run ID is not an ingestion job");
    }

    @Test
    void testIngestionJobDone() throws Exception {
        activateIrisFor(lecture1.getCourse());
        irisRequestMockProvider.mockIngestionWebhookRunResponse(dto -> assertThat(dto.settings().authenticationToken()).isNotNull());
        String newJobToken = pyrisJobService.addLectureIngestionWebhookJob(123L, lecture1.getId(), lecture1.getLectureUnits().getFirst().getId());
        String chatJobToken = pyrisJobService.addCourseChatJob(123L, 123L, 123L);
        PyrisStageDTO errorStage = new PyrisStageDTO("error", 1, PyrisStageState.ERROR, "Stage not broke due to error.", false);
        PyrisLectureIngestionStatusUpdateDTO statusUpdate = new PyrisLectureIngestionStatusUpdateDTO("Success", List.of(errorStage), lecture1.getLectureUnits().getFirst().getId());
        var headers = new HttpHeaders(new LinkedMultiValueMap<>(Map.of(HttpHeaders.AUTHORIZATION, List.of(Constants.BEARER_PREFIX + chatJobToken))));
        MockHttpServletResponse response = request.postWithoutResponseBody("/api/iris/internal/webhooks/ingestion/runs/" + newJobToken + "/status", statusUpdate,
                HttpStatus.CONFLICT, headers);
        assertThat(response.getContentAsString()).contains("Run ID in URL does not match run ID in request body");
        response = request.postWithoutResponseBody("/api/iris/internal/webhooks/ingestion/runs/" + chatJobToken + "/status", statusUpdate, HttpStatus.CONFLICT, headers);
        assertThat(response.getContentAsString()).contains("Run ID is not an ingestion job");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testLectureUnitLinkConstruction() {
        activateIrisFor(lecture1.getCourse());

        AttachmentVideoUnit testUnit = lectureUtilService.createAttachmentVideoUnit(true);
        String attachmentLink = testUnit.getAttachment().getLink();
        testUnit.setLecture(lecture1);
        lecture1.getLectureUnits().add(testUnit);
        lecture1 = lectureRepository.save(lecture1);
        testUnit = attachmentVideoUnitTestRepository.save(testUnit);

        String expectedBaseUrl = "http://localhost:8080"; // Default test server URL
        ReflectionTestUtils.setField(pyrisWebhookService, "artemisBaseUrl", expectedBaseUrl);
        String expectedUrl = expectedBaseUrl + ARTEMIS_FILE_PATH_PREFIX + attachmentLink;

        irisRequestMockProvider.mockIngestionWebhookRunResponse(dto -> {
            String lectureUnitLink = dto.pyrisLectureUnit().lectureUnitLink();
            // Verify URL construction: baseURL + pathprefix + link
            assertThat(lectureUnitLink).isEqualTo(expectedUrl);
        });

        pyrisWebhookService.addLectureUnitToPyrisDB(testUnit);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testIngestTranscriptionForLectureUnit() throws Exception {
        activateIrisFor(lecture1.getCourse());
        AttachmentVideoUnit unitWithTranscription = lectureUtilService.createAttachmentVideoUnit(true);
        unitWithTranscription.setLecture(lecture1);
        unitWithTranscription.setVideoSource("https://example.com/video.mp4");
        lecture1.getLectureUnits().add(unitWithTranscription);
        lecture1 = lectureRepository.save(lecture1);
        unitWithTranscription = attachmentVideoUnitTestRepository.save(unitWithTranscription);
        LectureTranscription lectureTranscription = new LectureTranscription("de", List.of(new LectureTranscriptionSegment(0.0, 10.0, "Dies ist eine Beispieltranskription.", 1)),
                unitWithTranscription);
        lectureTranscriptionRepository.save(lectureTranscription);

        irisRequestMockProvider.mockIngestionWebhookRunResponse(dto -> {
            assertThat(dto.settings().authenticationToken()).isNotNull();
        });

        String jobToken = pyrisWebhookService.addLectureUnitToPyrisDB(unitWithTranscription);
        assertThat(jobToken).isNotNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testDeleteLectureUnitWithTranscriptionFromPyrisDB() throws Exception {
        activateIrisFor(lecture1.getCourse());
        AttachmentVideoUnit unitWithTranscription = lectureUtilService.createAttachmentVideoUnit(true);
        unitWithTranscription.setLecture(lecture1);
        unitWithTranscription.setVideoSource("https://example.com/video.mp4");
        lecture1.getLectureUnits().add(unitWithTranscription);
        lecture1 = lectureRepository.save(lecture1);
        unitWithTranscription = attachmentVideoUnitTestRepository.save(unitWithTranscription);
        LectureTranscription lectureTranscription = new LectureTranscription("de", List.of(new LectureTranscriptionSegment(0.0, 10.0, "Dies ist eine Beispieltranskription.", 1)),
                unitWithTranscription);
        lectureTranscriptionRepository.save(lectureTranscription);

        irisRequestMockProvider.mockDeletionWebhookRunResponse(dto -> {
            assertThat(dto.settings().authenticationToken()).isNotNull();
        });

        String jobToken = pyrisWebhookService.deleteLectureFromPyrisDB(List.of(unitWithTranscription));
        assertThat(jobToken).isNotNull();

        PyrisStageDTO doneStage = new PyrisStageDTO("done", 1, PyrisStageState.DONE, "Stage completed successfully.", false);
        PyrisLectureIngestionStatusUpdateDTO statusUpdate = new PyrisLectureIngestionStatusUpdateDTO("Success", List.of(doneStage), unitWithTranscription.getId());
        var headers = new HttpHeaders(new LinkedMultiValueMap<>(Map.of(HttpHeaders.AUTHORIZATION, List.of(Constants.BEARER_PREFIX + jobToken))));
        request.postWithoutResponseBody("/api/iris/internal/webhooks/ingestion/runs/" + jobToken + "/status", statusUpdate, HttpStatus.OK, headers);

        assertThat(pyrisJobService.getJob(jobToken)).isNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testDeleteLectureUnitWithTranscriptionKeepsJobIfNotDone() throws Exception {
        activateIrisFor(lecture1.getCourse());
        AttachmentVideoUnit unitWithTranscription = lectureUtilService.createAttachmentVideoUnit(true);
        unitWithTranscription.setLecture(lecture1);
        unitWithTranscription.setVideoSource("https://example.com/video.mp4");
        lecture1.getLectureUnits().add(unitWithTranscription);
        lecture1 = lectureRepository.save(lecture1);
        unitWithTranscription = attachmentVideoUnitTestRepository.save(unitWithTranscription);
        LectureTranscription lectureTranscription = new LectureTranscription("de", List.of(new LectureTranscriptionSegment(0.0, 10.0, "Dies ist eine Beispieltranskription.", 1)),
                unitWithTranscription);
        lectureTranscriptionRepository.save(lectureTranscription);

        irisRequestMockProvider.mockDeletionWebhookRunResponse(dto -> {
            assertThat(dto.settings().authenticationToken()).isNotNull();
        });

        String jobToken = pyrisWebhookService.deleteLectureFromPyrisDB(List.of(unitWithTranscription));
        assertThat(jobToken).isNotNull();

        PyrisStageDTO inProgressStage = new PyrisStageDTO("inProgressStage", 1, PyrisStageState.IN_PROGRESS, "Stage läuft noch.", false);
        PyrisLectureIngestionStatusUpdateDTO statusUpdate = new PyrisLectureIngestionStatusUpdateDTO("Success", List.of(inProgressStage), unitWithTranscription.getId());
        var headers = new HttpHeaders(new LinkedMultiValueMap<>(Map.of(HttpHeaders.AUTHORIZATION, List.of(Constants.BEARER_PREFIX + jobToken))));
        request.postWithoutResponseBody("/api/iris/internal/webhooks/ingestion/runs/" + jobToken + "/status", statusUpdate, HttpStatus.OK, headers);

        assertThat(pyrisJobService.getJob(jobToken)).isNotNull();
    }
}
