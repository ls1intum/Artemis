package de.tum.cit.aet.artemis.iris;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_IRIS;
import static de.tum.cit.aet.artemis.iris.utils.IrisLLMMock.getMockLLMCosts;
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
        userUtilService.addUsers(TEST_PREFIX, 2, 1, 0, 2);
        course = programmingExerciseUtilService.addCourseWithOneProgrammingExercise();
        this.programmingExercise = course.getExercises().stream().filter(ProgrammingExercise.class::isInstance).map(ProgrammingExercise.class::cast).findFirst().orElse(null);

        userUtilService.createAndSaveUser(TEST_PREFIX + "student42");
        userUtilService.createAndSaveUser(TEST_PREFIX + "tutor42");
        userUtilService.createAndSaveUser(TEST_PREFIX + "instructor42");
        activateIrisFor(course);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void callConsistencyCheckAsInstructor_shouldSucceed() throws Exception {
        PyrisProgrammingExerciseDTO exerciseDto = createPyrisProgrammingExerciseDTO(programmingExercise);
        irisRequestMockProvider.mockProgrammingConsistencyCheckResponse(dto -> {
            assertThat(dto.exercise().id()).isEqualTo(exerciseDto.id());
            assertThat(dto.exercise().name()).isEqualTo("Programming");
            assertThat(dto.exercise().programmingLanguage()).isEqualTo(ProgrammingLanguage.JAVA);
        }, exerciseDto.id());

        when(pyrisDTOService.toPyrisProgrammingExerciseDTO(any())).thenReturn(exerciseDto);

        request.postWithoutResponseBody("/api/iris/consistency-check/exercises/" + exerciseDto.id(), exerciseDto, HttpStatus.OK);

        // in the normal system, at some point we receive a websocket message with the result

        List<PyrisStageDTO> stages = List.of(new PyrisStageDTO("Generating Consistency Check", 10, PyrisStageState.DONE, null));
        String jobId = "testJobId";
        String userLogin = TEST_PREFIX + "instructor1";
        ConsistencyCheckJob job = new ConsistencyCheckJob(jobId, course.getId(), exerciseDto.id(), userUtilService.getUserByLogin(userLogin).getId());

        List<LLMRequest> tokens = getMockLLMCosts();
        irisConsistencyCheckService.handleStatusUpdate(job, new PyrisConsistencyCheckStatusUpdateDTO(stages, "result", tokens));
        ArgumentCaptor<PyrisConsistencyCheckStatusUpdateDTO> argumentCaptor = ArgumentCaptor.forClass(PyrisConsistencyCheckStatusUpdateDTO.class);

        verify(websocketMessagingService, timeout(200).times(3)).sendMessageToUser(eq(TEST_PREFIX + "instructor1"),
                eq("/topic/iris/consistency-check/exercises/" + exerciseDto.id()), argumentCaptor.capture());
        List<PyrisConsistencyCheckStatusUpdateDTO> allValues = argumentCaptor.getAllValues();

        assertThat(allValues.get(0).stages()).hasSize(2);
        assertThat(allValues.get(0).result()).isNull();

        assertThat(allValues.get(1).stages()).hasSize(2);
        assertThat(allValues.get(1).result()).isNull();

        assertThat(allValues.get(2).stages()).hasSize(1);
        assertThat(allValues.get(2).result()).isEqualTo("result");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void callConsistencyCheckAsAsStudentShouldThrowForbidden() throws Exception {
        PyrisProgrammingExerciseDTO exerciseDto = createPyrisProgrammingExerciseDTO(programmingExercise);
        irisRequestMockProvider.mockProgrammingConsistencyCheckResponse(dto -> {
            assertThat(dto.exercise().id()).isEqualTo(exerciseDto.id());
        }, exerciseDto.id());
        request.postWithoutResponseBody("/api/iris/consistency-check/exercises/" + exerciseDto.id(), exerciseDto, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void callConsistencyCheckAsAsTutorShouldThrowForbidden() throws Exception {
        PyrisProgrammingExerciseDTO exerciseDto = createPyrisProgrammingExerciseDTO(programmingExercise);
        irisRequestMockProvider.mockProgrammingConsistencyCheckResponse(dto -> {
            assertThat(dto.exercise().id()).isEqualTo(exerciseDto.id());
        }, exerciseDto.id());
        request.postWithoutResponseBody("/api/iris/consistency-check/exercises/" + exerciseDto.id(), exerciseDto, HttpStatus.FORBIDDEN);
    }

    private PyrisProgrammingExerciseDTO createPyrisProgrammingExerciseDTO(ProgrammingExercise exercise) {
        return new PyrisProgrammingExerciseDTO(exercise.getId(), "Patterns of SE", ProgrammingLanguage.JAVA,
                Map.of("Main.java", "public class Main {}", "Helper.java", "public class Helper {}"), Map.of("Solution.java", "public class Solution {}"),
                Map.of("Test.java", "import static org.junit.jupiter.api.*; // tests"), "Implement a design pattern of your choice.", Instant.now(),
                Instant.now().plusSeconds(604800));
    }

}
