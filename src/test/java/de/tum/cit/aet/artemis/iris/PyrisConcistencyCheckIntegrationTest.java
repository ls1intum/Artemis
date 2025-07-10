package de.tum.cit.aet.artemis.iris;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_IRIS;
import static de.tum.cit.aet.artemis.iris.util.IrisLLMMock.getMockLLMCosts;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.LLMRequest;
import de.tum.cit.aet.artemis.core.user.util.UserUtilService;
import de.tum.cit.aet.artemis.exercise.util.ExerciseUtilService;
import de.tum.cit.aet.artemis.iris.service.IrisConsistencyCheckService;
import de.tum.cit.aet.artemis.iris.service.pyris.PyrisDTOService;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.consistencyCheck.PyrisConsistencyCheckStatusUpdateDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.data.PyrisProgrammingExerciseDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.status.PyrisStageDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.status.PyrisStageState;
import de.tum.cit.aet.artemis.iris.service.pyris.job.ConsistencyCheckJob;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;

@Profile(PROFILE_IRIS)
class PyrisConsistencyCheckIntegrationTest extends AbstractIrisIntegrationTest {

    private static final String TEST_PREFIX = "pyrisconsistencytest";

    @Autowired
    private UserUtilService userUtilService;

    @Mock
    private PyrisDTOService pyrisDTOService;

    @Autowired
    private IrisConsistencyCheckService irisConsistencyCheckService;

    private Course course;

    private ProgrammingExercise programmingExercise;

    @BeforeEach
    void initTestCase() {
        userUtilService.addUsers(TEST_PREFIX, 2, 1, 1, 2);
        course = programmingExerciseUtilService.addCourseWithOneProgrammingExercise();
        this.programmingExercise = ExerciseUtilService.getFirstExerciseWithType(course, ProgrammingExercise.class);
        activateIrisFor(course);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void callConsistencyCheckAsInstructor_shouldSucceed() throws Exception {
        PyrisProgrammingExerciseDTO exerciseDto = createPyrisProgrammingExerciseDTO(programmingExercise);
        irisRequestMockProvider.mockProgrammingConsistencyCheckResponse(dto -> {
        });

        when(pyrisDTOService.toPyrisProgrammingExerciseDTO(any())).thenReturn(exerciseDto);

        request.postWithoutResponseBody("/api/iris/consistency-check/exercises/" + exerciseDto.id(), exerciseDto, HttpStatus.OK);

        // in the normal system, at some point we receive a websocket message with the result
        List<PyrisStageDTO> stages = List.of(new PyrisStageDTO("Generating Consistency Check", 10, PyrisStageState.DONE, null));
        String jobId = "testJobId";
        String userLogin = TEST_PREFIX + "editor1";
        ConsistencyCheckJob job = new ConsistencyCheckJob(jobId, course.getId(), exerciseDto.id(), userUtilService.getUserByLogin(userLogin).getId());

        List<LLMRequest> tokens = getMockLLMCosts("IRIS_CHAT_EXERCISE_MESSAGE");
        String rewritingResult = "result";

        simulateWebsocketMessageWithResult(job, tokens, stages, rewritingResult);
        // Make sure that the websocket message returned to the user contains the proper values (we need to intercept the returned message with the argumentCaptor)
        ArgumentCaptor<PyrisConsistencyCheckStatusUpdateDTO> argumentCaptor = ArgumentCaptor.forClass(PyrisConsistencyCheckStatusUpdateDTO.class);

        verify(websocketMessagingService, timeout(200).times(3)).sendMessageToUser(eq(TEST_PREFIX + "editor1"), eq("/topic/iris/consistency-check/exercises/" + exerciseDto.id()),
                argumentCaptor.capture());
        List<PyrisConsistencyCheckStatusUpdateDTO> allValues = argumentCaptor.getAllValues();

        assertThat(allValues.get(0).stages()).hasSize(2);
        assertThat(allValues.get(0).result()).isNull();

        assertThat(allValues.get(1).stages()).hasSize(2);
        assertThat(allValues.get(1).result()).isNull();

        assertThat(allValues.get(2).stages()).hasSize(1);
        assertThat(allValues.get(2).result()).isEqualTo(rewritingResult);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void callConsistencyCheckAsAsStudentShouldThrowForbidden() throws Exception {
        PyrisProgrammingExerciseDTO exerciseDto = createPyrisProgrammingExerciseDTO(programmingExercise);
        irisRequestMockProvider.mockProgrammingConsistencyCheckResponse(dto -> {
            assertThat(dto.exercise().id()).isEqualTo(exerciseDto.id());
        });
        request.postWithoutResponseBody("/api/iris/consistency-check/exercises/" + exerciseDto.id(), exerciseDto, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void callConsistencyCheckAsAsTutorShouldThrowForbidden() throws Exception {
        PyrisProgrammingExerciseDTO exerciseDto = createPyrisProgrammingExerciseDTO(programmingExercise);
        irisRequestMockProvider.mockProgrammingConsistencyCheckResponse(dto -> {
            assertThat(dto.exercise().id()).isEqualTo(exerciseDto.id());
        });
        request.postWithoutResponseBody("/api/iris/consistency-check/exercises/" + exerciseDto.id(), exerciseDto, HttpStatus.FORBIDDEN);
    }

    private PyrisProgrammingExerciseDTO createPyrisProgrammingExerciseDTO(ProgrammingExercise exercise) {
        return new PyrisProgrammingExerciseDTO(exercise.getId(), "Patterns of SE", ProgrammingLanguage.JAVA,
                Map.of("Main.java", "public class Main {}", "Helper.java", "public class Helper {}"), Map.of("Solution.java", "public class Solution {}"),
                Map.of("Test.java", "import static org.junit.jupiter.api.*; // tests"), "Implement a design pattern of your choice.", Instant.now(),
                Instant.now().plusSeconds(604800));
    }

    /**
     * Simulate the websocket message that would be sent by Pyris
     * This is a simulation of the PyrisConsistencyCheckStatusUpdateDTO that would be sent to the user
     * It contains the stages and the result of the consistency check
     *
     * @param job    the job that is being processed
     * @param tokens the LLM requests that were made during the rewriting process
     * @param stages the stages of the consistency process
     * @param result the result of the consistency check process
     */
    private void simulateWebsocketMessageWithResult(ConsistencyCheckJob job, List<LLMRequest> tokens, List<PyrisStageDTO> stages, String result) {
        irisConsistencyCheckService.handleStatusUpdate(job, new PyrisConsistencyCheckStatusUpdateDTO(stages, result, tokens));
    }

}
