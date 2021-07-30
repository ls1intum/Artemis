package de.tum.in.www1.artemis.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Iterator;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.programmingexercise.MockDelegate;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.service.dto.ConsistencyErrorDTO;
import de.tum.in.www1.artemis.util.DatabaseUtilService;
import de.tum.in.www1.artemis.util.ModelFactory;
import de.tum.in.www1.artemis.util.RequestUtilService;

/**
 * Note: this class should be independent of the actual VCS and CIS and contains common test logic for both scenarios:
 * 1) Bamboo + Bitbucket
 * 2) Jenkins + Gitlab
 */
@Service
public class ConsistencyCheckServiceTest {

    @Autowired
    private DatabaseUtilService database;

    @Autowired
    private RequestUtilService request;

    @Autowired
    private ProgrammingExerciseRepository programmingExerciseRepository;

    public Course course;

    private MockDelegate mockDelegate;

    public void setup(MockDelegate mockDelegate) throws Exception {
        this.mockDelegate = mockDelegate;
        course = database.addCourseWithOneProgrammingExercise();
    }

    /**
     * Test consistencyCheck feature with programming exercise without
     * inconsistencies
     * @throws Exception if an error occurs
     */
    public void checkConsistencyOfProgrammingExercise_noErrors() throws Exception {
        var exercise = (ProgrammingExercise) course.getExercises().iterator().next();
        exercise = programmingExerciseRepository.findByIdWithTemplateAndSolutionParticipationElseThrow(exercise.getId());

        mockDelegate.mockCheckIfProjectExistsInVcs(exercise, true);
        mockDelegate.mockRepositoryUrlIsValid(exercise.getVcsTemplateRepositoryUrl(), exercise.getProjectKey(), true);
        mockDelegate.mockRepositoryUrlIsValid(exercise.getVcsTestRepositoryUrl(), exercise.getProjectKey(), true);
        mockDelegate.mockRepositoryUrlIsValid(exercise.getVcsSolutionRepositoryUrl(), exercise.getProjectKey(), true);
        mockDelegate.mockCheckIfBuildPlanExists(exercise.getProjectKey(), exercise.getTemplateBuildPlanId(), true, false);
        mockDelegate.mockCheckIfBuildPlanExists(exercise.getProjectKey(), exercise.getSolutionBuildPlanId(), true, false);

        var consistencyErrors = request.getList("/api/consistency-check/exercise/" + exercise.getId(), HttpStatus.OK, ConsistencyErrorDTO.class);
        assertThat(consistencyErrors.size()).isEqualTo(0);
    }

    /**
     * Test consistencyCheck feature with programming exercise
     * with missing VCS project
     * @throws Exception if an error occurs
     */
    public void checkConsistencyOfProgrammingExercise_missingVCSProject() throws Exception {
        var exercise = (ProgrammingExercise) course.getExercises().iterator().next();
        exercise = programmingExerciseRepository.findByIdWithTemplateAndSolutionParticipationElseThrow(exercise.getId());

        mockDelegate.mockCheckIfProjectExistsInVcs(exercise, false);
        mockDelegate.mockCheckIfBuildPlanExists(exercise.getProjectKey(), exercise.getTemplateBuildPlanId(), true, false);
        mockDelegate.mockCheckIfBuildPlanExists(exercise.getProjectKey(), exercise.getSolutionBuildPlanId(), true, false);

        var consistencyErrors = request.getList("/api/consistency-check/exercise/" + exercise.getId(), HttpStatus.OK, ConsistencyErrorDTO.class);
        assertThat(consistencyErrors.size()).isEqualTo(1);
        assertThat(consistencyErrors.get(0).getType()).isEqualTo(ConsistencyErrorDTO.ErrorType.VCS_PROJECT_MISSING);
    }

    /**
     * Test consistencyCheck feature with programming exercise
     * with missing VCS repositories
     * @throws Exception if an error occurs
     */
    public void checkConsistencyOfProgrammingExercise_missingVCSRepos() throws Exception {
        var exercise = (ProgrammingExercise) course.getExercises().iterator().next();
        exercise = programmingExerciseRepository.findByIdWithTemplateAndSolutionParticipationElseThrow(exercise.getId());

        mockDelegate.mockCheckIfProjectExistsInVcs(exercise, true);
        mockDelegate.mockRepositoryUrlIsValid(exercise.getVcsTemplateRepositoryUrl(), exercise.getProjectKey(), false);
        mockDelegate.mockRepositoryUrlIsValid(exercise.getVcsTestRepositoryUrl(), exercise.getProjectKey(), false);
        mockDelegate.mockRepositoryUrlIsValid(exercise.getVcsSolutionRepositoryUrl(), exercise.getProjectKey(), false);
        mockDelegate.mockCheckIfBuildPlanExists(exercise.getProjectKey(), exercise.getTemplateBuildPlanId(), true, false);
        mockDelegate.mockCheckIfBuildPlanExists(exercise.getProjectKey(), exercise.getSolutionBuildPlanId(), true, false);

        var consistencyErrors = request.getList("/api/consistency-check/exercise/" + exercise.getId(), HttpStatus.OK, ConsistencyErrorDTO.class);
        assertThat(consistencyErrors.size()).isEqualTo(3);
    }

    /**
     * Test consistencyCheck feature with programming exercise
     * with missing Build Plans
     * @throws Exception if an error occurs
     */
    public void checkConsistencyOfProgrammingExercise_buildPlansMissing() throws Exception {
        var exercise = (ProgrammingExercise) course.getExercises().iterator().next();
        exercise = programmingExerciseRepository.findByIdWithTemplateAndSolutionParticipationElseThrow(exercise.getId());

        mockDelegate.mockCheckIfProjectExistsInVcs(exercise, true);
        mockDelegate.mockRepositoryUrlIsValid(exercise.getVcsTemplateRepositoryUrl(), exercise.getProjectKey(), true);
        mockDelegate.mockRepositoryUrlIsValid(exercise.getVcsTestRepositoryUrl(), exercise.getProjectKey(), true);
        mockDelegate.mockRepositoryUrlIsValid(exercise.getVcsSolutionRepositoryUrl(), exercise.getProjectKey(), true);
        mockDelegate.mockCheckIfBuildPlanExists(exercise.getProjectKey(), exercise.getTemplateBuildPlanId(), false, false);
        mockDelegate.mockCheckIfBuildPlanExists(exercise.getProjectKey(), exercise.getSolutionBuildPlanId(), false, false);

        var consistencyErrors = request.getList("/api/consistency-check/exercise/" + exercise.getId(), HttpStatus.OK, ConsistencyErrorDTO.class);
        assertThat(consistencyErrors.size()).isEqualTo(2);
    }

    /**
     * Test consistencyCheck feature with a local simulation
     * of a programming exercise
     * @throws Exception if an error occurs
     */
    public void checkConsistencyOfProgrammingExercise_isLocalSimulation() throws Exception {
        var exercise = (ProgrammingExercise) course.getExercises().iterator().next();
        exercise.setTestRepositoryUrl("artemislocalhost/to/set/localSimulation/to/true");
        exercise = programmingExerciseRepository.save(exercise);

        var consistencyErrors = request.getList("/api/consistency-check/exercise/" + exercise.getId(), HttpStatus.OK, ConsistencyErrorDTO.class);
        assertThat(consistencyErrors.size()).isEqualTo(1);
        assertThat(consistencyErrors.get(0).getType()).isEqualTo(ConsistencyErrorDTO.ErrorType.IS_LOCAL_SIMULATION);
    }

    /**
     * Test consistencyCheck feature with a course
     * containing errors
     * @throws Exception if an error occurs
     */
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

        mockDelegate.mockCheckIfProjectExistsInVcs(exercise1, false);
        mockDelegate.mockCheckIfBuildPlanExists(exercise1.getProjectKey(), exercise1.getTemplateBuildPlanId(), false, false);
        mockDelegate.mockCheckIfBuildPlanExists(exercise1.getProjectKey(), exercise1.getSolutionBuildPlanId(), true, false);

        mockDelegate.mockCheckIfProjectExistsInVcs(exercise2, true);
        mockDelegate.mockRepositoryUrlIsValid(exercise2.getVcsTemplateRepositoryUrl(), exercise2.getProjectKey(), true);
        mockDelegate.mockRepositoryUrlIsValid(exercise2.getVcsTestRepositoryUrl(), exercise2.getProjectKey(), true);
        mockDelegate.mockRepositoryUrlIsValid(exercise2.getVcsSolutionRepositoryUrl(), exercise2.getProjectKey(), true);
        mockDelegate.mockCheckIfBuildPlanExists(exercise2.getProjectKey(), exercise2.getTemplateBuildPlanId(), true, false);
        mockDelegate.mockCheckIfBuildPlanExists(exercise2.getProjectKey(), exercise2.getSolutionBuildPlanId(), false, false);
        var consistencyErrors = request.getList("/api/consistency-check/course/" + course.getId(), HttpStatus.OK, ConsistencyErrorDTO.class);
        assertThat(consistencyErrors.size()).isEqualTo(3);
    }
}
