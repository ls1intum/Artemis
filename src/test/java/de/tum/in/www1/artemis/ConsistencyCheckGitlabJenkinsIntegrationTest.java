package de.tum.in.www1.artemis;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Iterator;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.service.dto.ConsistencyErrorDTO;
import de.tum.in.www1.artemis.util.DatabaseUtilService;
import de.tum.in.www1.artemis.util.ModelFactory;
import de.tum.in.www1.artemis.util.RequestUtilService;

public class ConsistencyCheckGitlabJenkinsIntegrationTest extends AbstractSpringIntegrationJenkinsGitlabTest {

    @Autowired
    private DatabaseUtilService database;

    @Autowired
    private RequestUtilService request;

    @Autowired
    private ProgrammingExerciseRepository programmingExerciseRepository;

    public Course course;

    @BeforeEach
    void setup() throws Exception {
        jenkinsRequestMockProvider.enableMockingOfRequests(jenkinsServer);
        gitlabRequestMockProvider.enableMockingOfRequests();
        course = database.addCourseWithOneProgrammingExercise();
    }

    @AfterEach
    void tearDown() throws Exception {
        gitlabRequestMockProvider.reset();
        jenkinsRequestMockProvider.reset();
    }

    @Test
    @WithMockUser(username = "admin1", roles = "ADMIN")
    public void checkConsistencyOfProgrammingExercise_noErrors() throws Exception {
        var exercise = (ProgrammingExercise) course.getExercises().iterator().next();
        exercise = programmingExerciseRepository.findByIdWithTemplateAndSolutionParticipationElseThrow(exercise.getId());

        mockCheckIfProjectExistsInVcs(exercise, true);
        mockRepositoryUrlIsValid(exercise.getVcsTemplateRepositoryUrl(), exercise.getProjectKey(), true);
        mockRepositoryUrlIsValid(exercise.getVcsTestRepositoryUrl(), exercise.getProjectKey(), true);
        mockRepositoryUrlIsValid(exercise.getVcsSolutionRepositoryUrl(), exercise.getProjectKey(), true);
        mockCheckIfBuildPlanExists(exercise.getProjectKey(), exercise.getTemplateBuildPlanId(), true, false);
        mockCheckIfBuildPlanExists(exercise.getProjectKey(), exercise.getSolutionBuildPlanId(), true, false);

        var consistencyErrors = request.getList("/api/consistency-check/exercise/" + exercise.getId(), HttpStatus.OK, ConsistencyErrorDTO.class);
        assertThat(consistencyErrors.size()).isEqualTo(0);
    }

    @Test
    @WithMockUser(username = "admin1", roles = "ADMIN")
    public void checkConsistencyOfProgrammingExercise_missingVCSProject() throws Exception {
        var exercise = (ProgrammingExercise) course.getExercises().iterator().next();
        exercise = programmingExerciseRepository.findByIdWithTemplateAndSolutionParticipationElseThrow(exercise.getId());

        mockCheckIfProjectExistsInVcs(exercise, false);
        mockCheckIfBuildPlanExists(exercise.getProjectKey(), exercise.getTemplateBuildPlanId(), true, false);
        mockCheckIfBuildPlanExists(exercise.getProjectKey(), exercise.getSolutionBuildPlanId(), true, false);

        var consistencyErrors = request.getList("/api/consistency-check/exercise/" + exercise.getId(), HttpStatus.OK, ConsistencyErrorDTO.class);
        assertThat(consistencyErrors.size()).isEqualTo(1);
        assertThat(consistencyErrors.get(0).getType()).isEqualTo(ConsistencyErrorDTO.ErrorType.VCS_PROJECT_MISSING);
    }

    @Test
    @WithMockUser(username = "admin1", roles = "ADMIN")
    public void checkConsistencyOfProgrammingExercise_missingVCSRepos() throws Exception {
        var exercise = (ProgrammingExercise) course.getExercises().iterator().next();
        exercise = programmingExerciseRepository.findByIdWithTemplateAndSolutionParticipationElseThrow(exercise.getId());

        mockCheckIfProjectExistsInVcs(exercise, true);
        mockRepositoryUrlIsValid(exercise.getVcsTemplateRepositoryUrl(), exercise.getProjectKey(), false);
        mockRepositoryUrlIsValid(exercise.getVcsTestRepositoryUrl(), exercise.getProjectKey(), false);
        mockRepositoryUrlIsValid(exercise.getVcsSolutionRepositoryUrl(), exercise.getProjectKey(), false);
        mockCheckIfBuildPlanExists(exercise.getProjectKey(), exercise.getTemplateBuildPlanId(), true, false);
        mockCheckIfBuildPlanExists(exercise.getProjectKey(), exercise.getSolutionBuildPlanId(), true, false);

        var consistencyErrors = request.getList("/api/consistency-check/exercise/" + exercise.getId(), HttpStatus.OK, ConsistencyErrorDTO.class);
        assertThat(consistencyErrors.size()).isEqualTo(3);
    }

    @Test
    @WithMockUser(username = "admin1", roles = "ADMIN")
    public void checkConsistencyOfProgrammingExercise_buildPlansMissing() throws Exception {
        var exercise = (ProgrammingExercise) course.getExercises().iterator().next();
        exercise = programmingExerciseRepository.findByIdWithTemplateAndSolutionParticipationElseThrow(exercise.getId());

        mockCheckIfProjectExistsInVcs(exercise, true);
        mockRepositoryUrlIsValid(exercise.getVcsTemplateRepositoryUrl(), exercise.getProjectKey(), true);
        mockRepositoryUrlIsValid(exercise.getVcsTestRepositoryUrl(), exercise.getProjectKey(), true);
        mockRepositoryUrlIsValid(exercise.getVcsSolutionRepositoryUrl(), exercise.getProjectKey(), true);
        mockCheckIfBuildPlanExists(exercise.getProjectKey(), exercise.getTemplateBuildPlanId(), false, false);
        mockCheckIfBuildPlanExists(exercise.getProjectKey(), exercise.getSolutionBuildPlanId(), false, false);

        var consistencyErrors = request.getList("/api/consistency-check/exercise/" + exercise.getId(), HttpStatus.OK, ConsistencyErrorDTO.class);
        assertThat(consistencyErrors.size()).isEqualTo(2);
    }

    @Test
    @WithMockUser(username = "admin1", roles = "ADMIN")
    public void checkConsistencyOfProgrammingExercise_isLocalSimulation() throws Exception {
        var exercise = (ProgrammingExercise) course.getExercises().iterator().next();
        exercise.setTestRepositoryUrl("artemislocalhost/to/set/localSimulation/to/true");
        exercise = programmingExerciseRepository.save(exercise);

        var consistencyErrors = request.getList("/api/consistency-check/exercise/" + exercise.getId(), HttpStatus.OK, ConsistencyErrorDTO.class);
        assertThat(consistencyErrors.size()).isEqualTo(1);
        assertThat(consistencyErrors.get(0).getType()).isEqualTo(ConsistencyErrorDTO.ErrorType.IS_LOCAL_SIMULATION);
    }

    @Test
    @WithMockUser(username = "admin1", roles = "ADMIN")
    public void checkConsistencyOfCourse() throws Exception {
        var newExercise = ModelFactory.generateProgrammingExercise(null, null, course);
        newExercise.setShortName("Test2");
        newExercise.setTitle("Test2");
        newExercise = programmingExerciseRepository.save(newExercise);
        database.addTemplateParticipationForProgrammingExercise(newExercise);
        database.addSolutionParticipationForProgrammingExercise(newExercise);
        newExercise = programmingExerciseRepository.save(newExercise);
        course.addExercises(newExercise);

        Iterator<Exercise> iterator = course.getExercises().iterator();
        var exercise1 = (ProgrammingExercise) iterator.next();
        var exercise2 = (ProgrammingExercise) iterator.next();
        exercise1 = programmingExerciseRepository.findByIdWithTemplateAndSolutionParticipationElseThrow(exercise1.getId());
        exercise2 = programmingExerciseRepository.findByIdWithTemplateAndSolutionParticipationElseThrow(exercise2.getId());

        mockCheckIfProjectExistsInVcs(exercise2, true);
        mockRepositoryUrlIsValid(exercise2.getVcsTemplateRepositoryUrl(), exercise2.getProjectKey(), true);
        mockRepositoryUrlIsValid(exercise2.getVcsTestRepositoryUrl(), exercise2.getProjectKey(), true);
        mockRepositoryUrlIsValid(exercise2.getVcsSolutionRepositoryUrl(), exercise2.getProjectKey(), true);
        mockCheckIfBuildPlanExists(exercise2.getProjectKey(), exercise2.getTemplateBuildPlanId(), true, false);
        mockCheckIfBuildPlanExists(exercise2.getProjectKey(), exercise2.getSolutionBuildPlanId(), false, false);

        mockCheckIfProjectExistsInVcs(exercise1, false);
        mockCheckIfBuildPlanExists(exercise1.getProjectKey(), exercise1.getTemplateBuildPlanId(), false, false);
        mockCheckIfBuildPlanExists(exercise1.getProjectKey(), exercise1.getSolutionBuildPlanId(), true, false);
        var consistencyErrors = request.getList("/api/consistency-check/course/" + course.getId(), HttpStatus.OK, ConsistencyErrorDTO.class);
        assertThat(consistencyErrors.size()).isEqualTo(3);
    }

}
