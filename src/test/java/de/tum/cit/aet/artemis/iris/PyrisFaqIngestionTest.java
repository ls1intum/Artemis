package de.tum.cit.aet.artemis.iris;

import static de.tum.cit.aet.artemis.communication.FaqFactory.generateFaq;
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

import de.tum.cit.aet.artemis.communication.FaqFactory;
import de.tum.cit.aet.artemis.communication.domain.Faq;
import de.tum.cit.aet.artemis.communication.domain.FaqState;
import de.tum.cit.aet.artemis.communication.repository.FaqRepository;
import de.tum.cit.aet.artemis.core.config.Constants;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.user.util.UserUtilService;
import de.tum.cit.aet.artemis.core.util.CourseUtilService;
import de.tum.cit.aet.artemis.iris.domain.settings.IrisCourseSettings;
import de.tum.cit.aet.artemis.iris.repository.IrisSettingsRepository;
import de.tum.cit.aet.artemis.iris.service.pyris.PyrisJobService;
import de.tum.cit.aet.artemis.iris.service.pyris.PyrisWebhookService;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.faqingestionwebhook.PyrisFaqIngestionStatusUpdateDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.status.PyrisStageDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.status.PyrisStageState;

class PyrisFaqIngestionTest extends AbstractIrisIntegrationTest {

    private static final String TEST_PREFIX = "pyrisfaqingestiontest";

    @Autowired
    private PyrisWebhookService pyrisWebhookService;

    @Autowired
    private FaqRepository faqRepository;

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private CourseUtilService courseUtilService;

    @Autowired
    protected PyrisJobService pyrisJobService;

    @Autowired
    protected IrisSettingsRepository irisSettingsRepository;

    private Faq faq1;

    private Course course1;

    @BeforeEach
    void initTestCase() throws Exception {
        userUtilService.addUsers(TEST_PREFIX, 2, 1, 0, 1);
        List<Course> courses = courseUtilService.createCoursesWithExercisesAndLectures(TEST_PREFIX, true, true, 1);
        this.course1 = this.courseRepository.findByIdWithExercisesAndExerciseDetailsAndLecturesElseThrow(courses.getFirst().getId());
        this.faq1 = generateFaq(course1, FaqState.ACCEPTED, "Faq 1 title", "Faq 1 content");
        faqRepository.save(faq1);
        // Add users that are not in the course
        userUtilService.createAndSaveUser(TEST_PREFIX + "student42");
        userUtilService.createAndSaveUser(TEST_PREFIX + "tutor42");
        userUtilService.createAndSaveUser(TEST_PREFIX + "instructor42");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void autoIngestionWhenFaqIsCreatedAndAutoUpdateEnabled() throws Exception {
        activateIrisFor(faq1.getCourse());
        IrisCourseSettings courseSettings = irisSettingsService.getRawIrisSettingsFor(faq1.getCourse());
        courseSettings.getIrisFaqIngestionSettings().setAutoIngestOnFaqCreation(true);
        this.irisSettingsRepository.save(courseSettings);
        irisRequestMockProvider.mockFaqIngestionWebhookRunResponse(dto -> assertThat(dto.settings().authenticationToken()).isNotNull());
        Faq newFaq = FaqFactory.generateFaq(course1, FaqState.ACCEPTED, "title", "answer");
        Faq returnedFaq = request.postWithResponseBody("/api/communication/courses/" + course1.getId() + "/faqs", newFaq, Faq.class, HttpStatus.CREATED);
        assertThat(returnedFaq.getId()).isNotNull();
        // TODO Add more assertions
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void noAutoIngestionWhenFaqIsCreatedAndAutoUpdateEnabled() throws Exception {

        irisRequestMockProvider.mockFaqIngestionWebhookRunResponse(dto -> assertThat(dto.settings().authenticationToken()).isNotNull());
        Faq newFaq = FaqFactory.generateFaq(course1, FaqState.ACCEPTED, "title", "answer");
        Faq returnedFaq = request.postWithResponseBody("/api/communication/courses/" + faq1.getCourse().getId() + "/faqs", newFaq, Faq.class, HttpStatus.CREATED);
        assertThat(returnedFaq.getId()).isNotNull();
        // TODO Add more assertions
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testIngestFaqButtonInPyris() throws Exception {
        activateIrisFor(faq1.getCourse());
        irisRequestMockProvider.mockFaqIngestionWebhookRunResponse(dto -> assertThat(dto.settings().authenticationToken()).isNotNull());
        request.postWithResponseBody("/api/communication/courses/" + faq1.getCourse().getId() + "/faqs/ingest", Optional.empty(), boolean.class, HttpStatus.OK);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testDeleteFaqFromPyrisDatabaseWithCourseSettingsEnabled() {
        activateIrisFor(faq1.getCourse());
        IrisCourseSettings courseSettings = irisSettingsService.getRawIrisSettingsFor(faq1.getCourse());
        this.irisSettingsRepository.save(courseSettings);
        irisRequestMockProvider.mockFaqDeletionWebhookRunResponse(dto -> assertThat(dto.settings().authenticationToken()).isNotNull());
        String jobToken = pyrisWebhookService.deleteFaq(faq1);
        assertThat(jobToken).isNotNull();

    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testAddFaqToPyrisDBAddJobWithCourseSettingsEnabled() throws Exception {
        activateIrisFor(faq1.getCourse());
        irisRequestMockProvider.mockFaqIngestionWebhookRunResponse(dto -> assertThat(dto.settings().authenticationToken()).isNotNull());
        request.postWithResponseBody("/api/communication/courses/" + faq1.getCourse().getId() + "/faqs/ingest", Optional.empty(), boolean.class, HttpStatus.OK);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testAddSpecificFaqToPyrisDBAddJobWithCourseSettingsEnabled() throws Exception {
        activateIrisFor(faq1.getCourse());
        irisRequestMockProvider.mockFaqIngestionWebhookRunResponse(dto -> assertThat(dto.settings().authenticationToken()).isNotNull());
        request.postWithResponseBody("/api/communication/courses/" + faq1.getCourse().getId() + "/faqs/ingest?faqId=" + faq1.getId(), Optional.empty(), boolean.class,
                HttpStatus.OK);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testAllStagesDoneIngestionStateDone() throws Exception {
        activateIrisFor(faq1.getCourse());
        irisRequestMockProvider.mockFaqIngestionWebhookRunResponse(dto -> assertThat(dto.settings().authenticationToken()).isNotNull());
        String jobToken = pyrisWebhookService.addFaq(faq1);
        PyrisStageDTO doneStage = new PyrisStageDTO("done", 1, PyrisStageState.DONE, "Stage completed successfully.");
        PyrisFaqIngestionStatusUpdateDTO statusUpdate = new PyrisFaqIngestionStatusUpdateDTO("Success", List.of(doneStage), faq1.getId());
        var headers = new HttpHeaders(new LinkedMultiValueMap<>(Map.of(HttpHeaders.AUTHORIZATION, List.of(Constants.BEARER_PREFIX + jobToken))));
        request.postWithoutResponseBody("/api/iris/internal/webhooks/ingestion/faqs/runs/" + jobToken + "/status", statusUpdate, HttpStatus.OK, headers);

    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testAllStagesDoneRemovesDeletionIngestionJob() throws Exception {
        activateIrisFor(faq1.getCourse());
        irisRequestMockProvider.mockFaqDeletionWebhookRunResponse(dto -> assertThat(dto.settings().authenticationToken()).isNotNull());
        String jobToken = pyrisWebhookService.deleteFaq(faq1);
        PyrisStageDTO doneStage = new PyrisStageDTO("done", 1, PyrisStageState.DONE, "Stage completed successfully.");
        PyrisFaqIngestionStatusUpdateDTO statusUpdate = new PyrisFaqIngestionStatusUpdateDTO("Success", List.of(doneStage), faq1.getId());
        var headers = new HttpHeaders(new LinkedMultiValueMap<>(Map.of(HttpHeaders.AUTHORIZATION, List.of(Constants.BEARER_PREFIX + jobToken))));
        request.postWithoutResponseBody("/api/iris/internal/webhooks/ingestion/faqs/runs/" + jobToken + "/status", statusUpdate, HttpStatus.OK, headers);
        assertThat(pyrisJobService.getJob(jobToken)).isNull();

    }

    @Test
    void testStageNotDoneKeepsAdditionIngestionJob() throws Exception {
        activateIrisFor(faq1.getCourse());
        irisRequestMockProvider.mockFaqIngestionWebhookRunResponse(dto -> assertThat(dto.settings().authenticationToken()).isNotNull());
        String jobToken = pyrisWebhookService.addFaq(faq1);
        PyrisStageDTO doneStage = new PyrisStageDTO("done", 1, PyrisStageState.DONE, "Stage completed successfully.");
        PyrisStageDTO inProgressStage = new PyrisStageDTO("inProgressStage", 1, PyrisStageState.IN_PROGRESS, "Stage completed successfully.");
        PyrisFaqIngestionStatusUpdateDTO statusUpdate = new PyrisFaqIngestionStatusUpdateDTO("Success", List.of(doneStage, inProgressStage), faq1.getId());
        var headers = new HttpHeaders(new LinkedMultiValueMap<>(Map.of(HttpHeaders.AUTHORIZATION, List.of(Constants.BEARER_PREFIX + jobToken))));
        request.postWithoutResponseBody("/api/iris/internal/webhooks/ingestion/faqs/runs/" + jobToken + "/status", statusUpdate, HttpStatus.OK, headers);
        assertThat(pyrisJobService.getJob(jobToken)).isNotNull();

    }

    @Test
    void testStageNotDoneKeepsDeletionIngestionJob() throws Exception {
        activateIrisFor(faq1.getCourse());
        irisRequestMockProvider.mockFaqDeletionWebhookRunResponse(dto -> assertThat(dto.settings().authenticationToken()).isNotNull());

        String jobToken = pyrisWebhookService.deleteFaq(faq1);
        PyrisStageDTO doneStage = new PyrisStageDTO("done", 1, PyrisStageState.DONE, "Stage completed successfully.");
        PyrisStageDTO inProgressStage = new PyrisStageDTO("inProgressStage", 1, PyrisStageState.IN_PROGRESS, "Stage completed successfully.");
        PyrisFaqIngestionStatusUpdateDTO statusUpdate = new PyrisFaqIngestionStatusUpdateDTO("Success", List.of(doneStage, inProgressStage), faq1.getId());
        var headers = new HttpHeaders(new LinkedMultiValueMap<>(Map.of(HttpHeaders.AUTHORIZATION, List.of(Constants.BEARER_PREFIX + jobToken))));
        request.postWithoutResponseBody("/api/iris/internal/webhooks/ingestion/faqs/runs/" + jobToken + "/status", statusUpdate, HttpStatus.OK, headers);
        assertThat(pyrisJobService.getJob(jobToken)).isNotNull();

    }

    @Test
    void testErrorStageRemovesDeletionIngestionJob() throws Exception {
        activateIrisFor(faq1.getCourse());
        irisRequestMockProvider.mockFaqDeletionWebhookRunResponse(dto -> assertThat(dto.settings().authenticationToken()).isNotNull());

        String jobToken = pyrisWebhookService.deleteFaq(faq1);
        PyrisStageDTO errorStage = new PyrisStageDTO("error", 1, PyrisStageState.ERROR, "Stage not broke due to error.");
        PyrisFaqIngestionStatusUpdateDTO statusUpdate = new PyrisFaqIngestionStatusUpdateDTO("Success", List.of(errorStage), faq1.getId());
        var headers = new HttpHeaders(new LinkedMultiValueMap<>(Map.of(HttpHeaders.AUTHORIZATION, List.of(Constants.BEARER_PREFIX + jobToken))));
        request.postWithoutResponseBody("/api/iris/internal/webhooks/ingestion/faqs/runs/" + jobToken + "/status", statusUpdate, HttpStatus.OK, headers);
        assertThat(pyrisJobService.getJob(jobToken)).isNull();

    }

    @Test
    void testErrorStageRemovesAdditionIngestionJob() throws Exception {
        activateIrisFor(faq1.getCourse());
        irisRequestMockProvider.mockFaqIngestionWebhookRunResponse(dto -> assertThat(dto.settings().authenticationToken()).isNotNull());
        String jobToken = pyrisWebhookService.addFaq(faq1);
        PyrisStageDTO errorStage = new PyrisStageDTO("error", 1, PyrisStageState.ERROR, "Stage not broke due to error.");
        PyrisFaqIngestionStatusUpdateDTO statusUpdate = new PyrisFaqIngestionStatusUpdateDTO("Success", List.of(errorStage), faq1.getId());
        var headers = new HttpHeaders(new LinkedMultiValueMap<>(Map.of(HttpHeaders.AUTHORIZATION, List.of(Constants.BEARER_PREFIX + jobToken))));
        request.postWithoutResponseBody("/api/iris/internal/webhooks/ingestion/faqs/runs/" + jobToken + "/status", statusUpdate, HttpStatus.OK, headers);
        assertThat(pyrisJobService.getJob(jobToken)).isNull();

    }

    @Test
    void testRunIdIngestionJob() throws Exception {
        activateIrisFor(faq1.getCourse());
        irisRequestMockProvider.mockFaqIngestionWebhookRunResponse(dto -> assertThat(dto.settings().authenticationToken()).isNotNull());
        String newJobToken = pyrisJobService.addFaqIngestionWebhookJob(123L, faq1.getId());
        String chatJobToken = pyrisJobService.addCourseChatJob(123L, 123L, 123L);
        PyrisStageDTO errorStage = new PyrisStageDTO("error", 1, PyrisStageState.ERROR, "Stage not broke due to error.");
        PyrisFaqIngestionStatusUpdateDTO statusUpdate = new PyrisFaqIngestionStatusUpdateDTO("Success", List.of(errorStage), faq1.getId());
        var headers = new HttpHeaders(new LinkedMultiValueMap<>(Map.of(HttpHeaders.AUTHORIZATION, List.of(Constants.BEARER_PREFIX + chatJobToken))));
        MockHttpServletResponse response = request.postWithoutResponseBody("/api/iris/internal/webhooks/ingestion/runs/" + newJobToken + "/status", statusUpdate,
                HttpStatus.CONFLICT, headers);
        assertThat(response.getContentAsString()).contains("Run ID in URL does not match run ID in request body");
        response = request.postWithoutResponseBody("/api/iris/internal/webhooks/ingestion/faqs/runs/" + chatJobToken + "/status", statusUpdate, HttpStatus.CONFLICT, headers);
        assertThat(response.getContentAsString()).contains("Run ID is not an ingestion job");
    }

    @Test
    void testIngestionJobDone() throws Exception {
        activateIrisFor(faq1.getCourse());
        irisRequestMockProvider.mockFaqIngestionWebhookRunResponse(dto -> assertThat(dto.settings().authenticationToken()).isNotNull());
        String newJobToken = pyrisJobService.addFaqIngestionWebhookJob(123L, faq1.getId());
        String chatJobToken = pyrisJobService.addCourseChatJob(123L, 123L, 123L);
        PyrisStageDTO errorStage = new PyrisStageDTO("error", 1, PyrisStageState.ERROR, "Stage not broke due to error.");
        PyrisFaqIngestionStatusUpdateDTO statusUpdate = new PyrisFaqIngestionStatusUpdateDTO("Success", List.of(errorStage), faq1.getId());
        var headers = new HttpHeaders(new LinkedMultiValueMap<>(Map.of(HttpHeaders.AUTHORIZATION, List.of(Constants.BEARER_PREFIX + chatJobToken))));
        MockHttpServletResponse response = request.postWithoutResponseBody("/api/iris/internal/webhooks/ingestion/runs/" + newJobToken + "/status", statusUpdate,
                HttpStatus.CONFLICT, headers);
        assertThat(response.getContentAsString()).contains("Run ID in URL does not match run ID in request body");
        response = request.postWithoutResponseBody("/api/iris/internal/webhooks/ingestion/faqs/runs/" + chatJobToken + "/status", statusUpdate, HttpStatus.CONFLICT, headers);
        assertThat(response.getContentAsString()).contains("Run ID is not an ingestion job");
    }
}
