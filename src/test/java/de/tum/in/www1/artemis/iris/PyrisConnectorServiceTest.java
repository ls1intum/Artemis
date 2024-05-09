package de.tum.in.www1.artemis.iris;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.participation.ParticipationUtilService;
import de.tum.in.www1.artemis.service.connectors.pyris.PyrisConnectorException;
import de.tum.in.www1.artemis.service.connectors.pyris.PyrisConnectorService;
import de.tum.in.www1.artemis.service.connectors.pyris.PyrisPipelineService;
import de.tum.in.www1.artemis.service.iris.exception.IrisForbiddenException;
import de.tum.in.www1.artemis.service.iris.exception.IrisInternalPyrisErrorException;
import de.tum.in.www1.artemis.service.iris.session.IrisChatSessionService;
import de.tum.in.www1.artemis.util.IrisUtilTestService;
import de.tum.in.www1.artemis.util.LocalRepository;

class PyrisConnectorServiceTest extends AbstractIrisIntegrationTest {

    private static final String TEST_PREFIX = "pyrisconnectorservice";

    @Autowired
    private PyrisConnectorService pyrisConnectorService;

    @Autowired
    private PyrisPipelineService pyrisPipelineService;

    @Autowired
    private IrisUtilTestService irisUtilTestService;

    @Autowired
    private ParticipationUtilService participationUtilService;

    @Autowired
    private IrisChatSessionService irisChatSessionService;

    private static Stream<Arguments> irisExceptions() {
        // @formatter:off
        return Stream.of(
                Arguments.of(400, IrisInternalPyrisErrorException.class),
                Arguments.of(401, IrisForbiddenException.class),
                Arguments.of(403, IrisForbiddenException.class),
                Arguments.of(404, IrisInternalPyrisErrorException.class), // TODO: Change with more specific exception
                Arguments.of(500, IrisInternalPyrisErrorException.class),
                Arguments.of(418, IrisInternalPyrisErrorException.class) // Test default case
        );
        // @formatter:on
    }

    @ParameterizedTest
    @MethodSource("irisExceptions")
    void testExceptionV2(int httpStatus, Class<?> exceptionClass) throws Exception {
        userUtilService.addUsers(TEST_PREFIX, 1, 0, 0, 0);

        var course = programmingExerciseUtilService.addCourseWithOneProgrammingExerciseAndTestCases();
        var exercise = exerciseUtilService.getFirstExerciseWithType(course, ProgrammingExercise.class);
        activateIrisGlobally();
        activateIrisFor(course);
        activateIrisFor(exercise);
        var repository = new LocalRepository("main");

        exercise = irisUtilTestService.setupTemplate(exercise, repository);
        exercise = irisUtilTestService.setupSolution(exercise, repository);
        exercise = irisUtilTestService.setupTest(exercise, repository);
        var exerciseParticipation = participationUtilService.addStudentParticipationForProgrammingExercise(exercise, TEST_PREFIX + "student1");
        irisUtilTestService.setupStudentParticipation(exerciseParticipation, repository);

        var irisSession = irisChatSessionService.createChatSessionForProgrammingExercise(exercise, userUtilService.getUserByLogin(TEST_PREFIX + "student1"));

        irisRequestMockProvider.mockRunError(httpStatus);

        ProgrammingExercise finalExercise = programmingExerciseRepository.findByIdWithTemplateAndSolutionParticipationElseThrow(exercise.getId());
        assertThatThrownBy(() -> pyrisPipelineService.executeTutorChatPipeline("default", Optional.empty(), finalExercise, irisSession)).isInstanceOf(exceptionClass);
    }

    @Test
    void testOfferedModels() throws Exception {
        irisRequestMockProvider.mockModelsResponse();

        var offeredModels = pyrisConnectorService.getOfferedModels();
        assertThat(offeredModels).hasSize(1);
        assertThat(offeredModels.get(0).id()).isEqualTo("TEST_MODEL");
    }

    @Test
    void testOfferedModelsError() {
        irisRequestMockProvider.mockModelsError();

        assertThatThrownBy(() -> pyrisConnectorService.getOfferedModels()).isInstanceOf(PyrisConnectorException.class);
    }
}
