package de.tum.cit.aet.artemis.iris;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_IRIS;
import static de.tum.cit.aet.artemis.iris.utils.IrisLLMMock.getMockLLMCosts;
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
        userUtilService.addUsers(TEST_PREFIX, 2, 1, 0, 2);
        this.course = programmingExerciseUtilService.addCourseWithOneProgrammingExercise();

        userUtilService.createAndSaveUser(TEST_PREFIX + "student42");
        userUtilService.createAndSaveUser(TEST_PREFIX + "tutor42");
        userUtilService.createAndSaveUser(TEST_PREFIX + "instructor42");
        activateIrisFor(course);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void callRewritingPipeline() throws Exception {
        irisRequestMockProvider.mockRewritingPipelineResponse(dto -> {
            assertThat(dto.toBeRewritten()).contains("test");
        });
        PyrisRewriteTextRequestDTO requestDTO = new PyrisRewriteTextRequestDTO("test", RewritingVariant.FAQ);
        request.postWithoutResponseBody("/api/iris/courses/" + course.getId() + "/rewrite-text", requestDTO, HttpStatus.OK);

        // in the normal system, at some point we receive a websocket message with the result

        List<PyrisStageDTO> stages = List.of(new PyrisStageDTO("Generating Rewriting", 10, PyrisStageState.DONE, null));
        String jobId = "testJobId";
        String userLogin = TEST_PREFIX + "instructor1";
        RewritingJob job = new RewritingJob(jobId, course.getId(), userUtilService.getUserByLogin(userLogin).getId());

        List<LLMRequest> tokens = getMockLLMCosts();
        irisRewritingService.handleStatusUpdate(job, new PyrisRewritingStatusUpdateDTO(stages, "result", tokens));
        ArgumentCaptor<PyrisRewritingStatusUpdateDTO> argumentCaptor = ArgumentCaptor.forClass(PyrisRewritingStatusUpdateDTO.class);

        verify(websocketMessagingService, timeout(200).times(3)).sendMessageToUser(eq(TEST_PREFIX + "instructor1"), eq("/topic/iris/rewriting/" + course.getId()),
                argumentCaptor.capture());
        List<PyrisRewritingStatusUpdateDTO> allValues = argumentCaptor.getAllValues();

        assertThat(allValues.get(0).stages()).hasSize(2);
        assertThat(allValues.get(0).result()).isNull();

        assertThat(allValues.get(1).stages()).hasSize(2);
        assertThat(allValues.get(1).result()).isNull();

        assertThat(allValues.get(2).stages()).hasSize(1);
        assertThat(allValues.get(2).result()).isEqualTo("result");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void callRewritingPipelineAsStudentShouldThrowForbidden() throws Exception {
        irisRequestMockProvider.mockRewritingPipelineResponse(dto -> {
        });
        PyrisRewriteTextRequestDTO requestDTO = new PyrisRewriteTextRequestDTO("", RewritingVariant.FAQ);
        request.postWithoutResponseBody("/api/iris/courses/" + course.getId() + "/rewrite-text", requestDTO, HttpStatus.FORBIDDEN);

    }

}
