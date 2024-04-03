package de.tum.in.www1.artemis.localvcci;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.util.ReflectionTestUtils;

import com.google.gson.Gson;

import de.tum.in.www1.artemis.AbstractSpringIntegrationLocalCILocalVCTest;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.enumeration.ProgrammingLanguage;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseStudentParticipation;
import de.tum.in.www1.artemis.exercise.ExerciseUtilService;
import de.tum.in.www1.artemis.exercise.programmingexercise.ProgrammingExerciseUtilService;
import de.tum.in.www1.artemis.participation.ParticipationUtilService;
import de.tum.in.www1.artemis.service.connectors.BuildScriptProviderService;
import de.tum.in.www1.artemis.service.connectors.aeolus.AeolusTemplateService;
import de.tum.in.www1.artemis.service.connectors.aeolus.Windfile;
import de.tum.in.www1.artemis.service.connectors.ci.ContinuousIntegrationService.BuildStatus;

class LocalCIServiceTest extends AbstractSpringIntegrationLocalCILocalVCTest {

    private static final String TEST_PREFIX = "localciservice";

    @Autowired
    private ProgrammingExerciseUtilService programmingExerciseUtilService;

    @Autowired
    private ExerciseUtilService exerciseUtilService;

    @Autowired
    private ParticipationUtilService participationUtilService;

    @Autowired
    private BuildScriptProviderService buildScriptProviderService;

    @Autowired
    private AeolusTemplateService aeolusTemplateService;

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testReturnCorrectBuildStatus() {
        userUtilService.addUsers(TEST_PREFIX, 1, 0, 0, 1);
        Course course = programmingExerciseUtilService.addCourseWithOneProgrammingExercise();
        ProgrammingExercise exercise = exerciseUtilService.getFirstExerciseWithType(course, ProgrammingExercise.class);
        ProgrammingExerciseStudentParticipation participation = participationUtilService.addStudentParticipationForProgrammingExercise(exercise, TEST_PREFIX + "student1");
        assertThat(continuousIntegrationService.getBuildStatus(participation)).isEqualTo(BuildStatus.INACTIVE);
    }

    @Test
    void testHealth() {
        var health = continuousIntegrationService.health();
        assertThat(health.isUp()).isTrue();
    }

    @Test
    void testRecreateBuildPlanForExercise() throws IOException {
        String script = "echo 'Hello, World!'";
        Course course = programmingExerciseUtilService.addCourseWithOneProgrammingExercise();
        ProgrammingExercise exercise = exerciseUtilService.getFirstExerciseWithType(course, ProgrammingExercise.class);
        exercise.setBuildScript(script);
        exercise.setBuildPlanConfiguration(null);
        continuousIntegrationService.recreateBuildPlansForExercise(exercise);
        script = buildScriptProviderService.getScriptFor(exercise.getProgrammingLanguage(), Optional.of(exercise.getProjectType()), exercise.isStaticCodeAnalysisEnabled(),
                exercise.hasSequentialTestRuns(), exercise.isTestwiseCoverageEnabled());
        Windfile windfile = aeolusTemplateService.getDefaultWindfileFor(exercise);
        assertThat(exercise.getBuildPlanConfiguration()).isEqualTo(new Gson().toJson(windfile));
        assertThat(exercise.getBuildScript()).isEqualTo(script);
        // test that the method does not throw an exception when the exercise is null
        continuousIntegrationService.recreateBuildPlansForExercise(null);
    }

    @Test
    void testGetScriptForWithoutCache() {
        ReflectionTestUtils.setField(buildScriptProviderService, "scriptCache", new ConcurrentHashMap<>());
        ProgrammingExercise programmingExercise = new ProgrammingExercise();
        programmingExercise.setProgrammingLanguage(ProgrammingLanguage.HASKELL);
        programmingExercise.setProjectType(null);
        programmingExercise.setStaticCodeAnalysisEnabled(false);
        programmingExercise.setSequentialTestRuns(false);
        programmingExercise.setTestwiseCoverageEnabled(false);
        String script = buildScriptProviderService.getScriptFor(programmingExercise);
        assertThat(script).isNotNull();
    }

    @Test
    void testUnsupportedMethods() {
        continuousIntegrationService.givePlanPermissions(null, null);
        continuousIntegrationService.enablePlan(null, null);
        continuousIntegrationService.updatePlanRepository(null, null, null, null, null, null, null);
        assertThat(continuousIntegrationService.getPlanKey(null)).isNull();
        assertThat(continuousIntegrationService.getWebHookUrl(null, null)).isEmpty();
        ResponseEntity<byte[]> latestArtifactResponse = continuousIntegrationService.retrieveLatestArtifact(null);
        assertThat(latestArtifactResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(latestArtifactResponse.getBody()).hasSize(0);
    }
}
