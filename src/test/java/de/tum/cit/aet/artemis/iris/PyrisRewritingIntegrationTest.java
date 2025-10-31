package de.tum.cit.aet.artemis.iris;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_IRIS;
import static de.tum.cit.aet.artemis.iris.util.IrisLLMMock.getMockLLMCosts;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.LLMRequest;
import de.tum.cit.aet.artemis.core.user.util.UserUtilService;
import de.tum.cit.aet.artemis.iris.service.IrisRewritingService;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.data.PyrisRewriteTextRequestDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.rewriting.PyrisRewritingStatusUpdateDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.rewriting.RewritingVariant;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.status.PyrisStageDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.status.PyrisStageState;
import de.tum.cit.aet.artemis.iris.service.pyris.job.RewritingJob;

@Profile(PROFILE_IRIS)
class PyrisRewritingIntegrationTest extends AbstractIrisIntegrationTest {

    private static final String TEST_PREFIX = "pyrisrewritingtest";

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private IrisRewritingService irisRewritingService;

    private Course course;

    @BeforeEach
    void initTestCase() throws Exception {
        userUtilService.addUsers(TEST_PREFIX, 2, 1, 1, 2);
        course = programmingExerciseUtilService.addCourseWithOneProgrammingExercise();
        activateIrisFor(course);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void callRewritingPipeline_shouldSucceed() throws Exception {
        PyrisRewriteTextRequestDTO requestDTO = new PyrisRewriteTextRequestDTO("test", RewritingVariant.FAQ);
        irisRequestMockProvider.mockRewritingPipelineResponse(dto -> {
        });

        request.postWithoutResponseBody("/api/iris/courses/" + course.getId() + "/rewrite-text", requestDTO, HttpStatus.OK);

        String jobId = "testJobId";
        String userLogin = TEST_PREFIX + "tutor1";
        Long userId = userUtilService.getUserByLogin(userLogin).getId();

        RewritingJob job = new RewritingJob(jobId, course.getId(), userId);
        List<PyrisStageDTO> stages = List.of(new PyrisStageDTO("Generating Rewriting", 10, PyrisStageState.DONE, null));
        List<LLMRequest> tokens = getMockLLMCosts("IRIS_CHAT_EXERCISE_MESSAGE");
        String result = "result";

        simulateWebsocketMessageWithResult(job, tokens, stages, result);

        ArgumentCaptor<PyrisRewritingStatusUpdateDTO> argumentCaptor = ArgumentCaptor.forClass(PyrisRewritingStatusUpdateDTO.class);
        verify(websocketMessagingService, timeout(200).times(3)).sendMessageToUser(eq(userLogin), eq("/topic/iris/rewriting/" + course.getId()), argumentCaptor.capture());

        List<PyrisRewritingStatusUpdateDTO> allValues = argumentCaptor.getAllValues();

        assertThat(allValues.get(0).stages()).hasSize(2);
        assertThat(allValues.get(0).result()).isNull();

        assertThat(allValues.get(1).stages()).hasSize(2);
        assertThat(allValues.get(1).result()).isNull();

        assertThat(allValues.get(2).stages()).hasSize(1);
        assertThat(allValues.get(2).result()).isEqualTo(result);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void callRewritingPipelineAsStudent_shouldThrowForbidden() throws Exception {
        PyrisRewriteTextRequestDTO requestDTO = new PyrisRewriteTextRequestDTO("irrelevant", RewritingVariant.FAQ);
        irisRequestMockProvider.mockRewritingPipelineResponse(dto -> {
        });
        request.postWithoutResponseBody("/api/iris/courses/" + course.getId() + "/rewrite-text", requestDTO, HttpStatus.FORBIDDEN);
    }

    /**
     * Simulate the websocket message that would be sent by Pyris
     * This is a simulation of the PyrisRewritingRequest that would be sent to the user
     * It contains the stages and the result of the consistency check
     *
     * @param job    the job that is being processed
     * @param tokens the LLM requests that were made during the rewriting process
     * @param stages the stages of the rewriting process
     * @param result the result of the rewriting process
     */
    private void simulateWebsocketMessageWithResult(RewritingJob job, List<LLMRequest> tokens, List<PyrisStageDTO> stages, String result) {
        irisRewritingService.handleStatusUpdate(job, new PyrisRewritingStatusUpdateDTO(stages, result, tokens, null, null, null));
    }
}
