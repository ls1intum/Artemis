package de.tum.cit.aet.artemis.iris;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.repository.CourseRepository;
import de.tum.cit.aet.artemis.core.service.LLMTokenUsageService;
import de.tum.cit.aet.artemis.core.user.util.UserUtilService;
import de.tum.cit.aet.artemis.iris.service.IrisRephrasingService;
import de.tum.cit.aet.artemis.iris.service.pyris.PyrisJobService;
import de.tum.cit.aet.artemis.iris.service.pyris.PyrisPipelineService;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.rephrasing.PyrisRephrasingDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.rephrasing.PyrisRephrasingStatusUpdateDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.rephrasing.RephrasingVariant;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.status.PyrisStageDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.status.PyrisStageState;
import de.tum.cit.aet.artemis.iris.service.pyris.job.RephrasingJob;
import de.tum.cit.aet.artemis.iris.service.websocket.IrisWebsocketService;

class PyrisRephrasingTest extends AbstractIrisIntegrationTest {

    private static final String TEST_PREFIX = "pyrisrephrasingtest";

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    IrisRephrasingService irisRephrasingService;

    @Autowired
    private PyrisPipelineService pyrisPipelineService;

    @Autowired
    private LLMTokenUsageService llmTokenUsageService;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private IrisWebsocketService websocketService;

    @Autowired
    private PyrisJobService pyrisJobService;

    private Course course;

    @BeforeEach
    void initTestCase() throws Exception {
        course = courseUtilService.createCourse();
        activateIrisGlobally();
        activateIrisFor(course);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void generateRephrasing_asEditor_shouldSucceed() throws Exception {
        String toBeRephrased = "CoolRephrasing";

        // Expect that a request is sent to Pyris having the following characteristics
        irisRequestMockProvider.mockRunRephrasingResponseAnd(dto -> {
            var token = dto.execution().settings().authenticationToken();
            assertThat(token).isNotNull();
        });

        // Send a request to the Artemis server as if the user had clicked the button in the UI
        request.postWithoutResponseBody("/api/courses/" + course.getId() + "/rephrase-text?toBeRephrased=" + toBeRephrased + "&variant=FAQ",
                new PyrisRephrasingDTO(toBeRephrased, RephrasingVariant.FAQ), HttpStatus.ACCEPTED);

        PyrisRephrasingDTO expected = new PyrisRephrasingDTO("Test Rephrasing", RephrasingVariant.FAQ);
        List<PyrisStageDTO> stages = List.of(new PyrisStageDTO("Generating Rephrasing", 10, PyrisStageState.DONE, null));

        // In the real system, this would be triggered by Pyris via a REST call to the Artemis server
        String jobId = "testJobId";
        String userLogin = TEST_PREFIX + "editor1";
        RephrasingJob job = new RephrasingJob(jobId, course.getId(), userUtilService.getUserByLogin(userLogin).getId());
        irisRephrasingService.handleStatusUpdate(job, new PyrisRephrasingStatusUpdateDTO(stages, "Rephrasing", null));

        ArgumentCaptor<PyrisRephrasingStatusUpdateDTO> argumentCaptor = ArgumentCaptor.forClass(PyrisRephrasingStatusUpdateDTO.class);
        verify(websocketMessagingService, timeout(200).times(1)).sendMessageToUser(eq(TEST_PREFIX + "editor1"), eq("/topic/iris/rephrasing/" + course.getId()),
                argumentCaptor.capture());

        List<PyrisRephrasingStatusUpdateDTO> allValues = argumentCaptor.getAllValues();
        assertThat(allValues.getFirst().stages()).hasSize(2);
        assertThat(allValues.getFirst().result()).isEqualTo("Rephrasing");
    }

}
