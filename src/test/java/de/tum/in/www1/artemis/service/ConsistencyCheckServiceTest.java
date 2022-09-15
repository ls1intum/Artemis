package de.tum.in.www1.artemis.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.*;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.programmingexercise.MockDelegate;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.service.dto.ConsistencyErrorDTO;
import de.tum.in.www1.artemis.util.DatabaseUtilService;
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

    @Autowired
    private UserRepository userRepository;

    public Course course;

    private MockDelegate mockDelegate;

    public void setup(MockDelegate mockDelegate) throws Exception {
        this.mockDelegate = mockDelegate;
        course = database.addCourseWithOneProgrammingExercise();
        User user = database.addUser("instructor1");
        Set<String> groups = new HashSet<>();
        groups.add(course.getInstructorGroupName());
        user.setGroups(groups);
        userRepository.save(user);
    }

    /**
     * Test consistencyCheck feature with programming exercise without
     * inconsistencies
     * @throws Exception if an error occurs
     */
    public void testCheckConsistencyOfProgrammingExercise_noErrors() throws Exception {
        var exercise = (ProgrammingExercise) course.getExercises().iterator().next();
        exercise = programmingExerciseRepository.findByIdWithTemplateAndSolutionParticipationElseThrow(exercise.getId());

        mockDelegate.mockCheckIfProjectExistsInVcs(exercise, true);
        mockDelegate.mockRepositoryUrlIsValid(exercise.getVcsTemplateRepositoryUrl(), exercise.getProjectKey(), true);
        mockDelegate.mockRepositoryUrlIsValid(exercise.getVcsTestRepositoryUrl(), exercise.getProjectKey(), true);
        mockDelegate.mockRepositoryUrlIsValid(exercise.getVcsSolutionRepositoryUrl(), exercise.getProjectKey(), true);
        mockDelegate.mockCheckIfBuildPlanExists(exercise.getProjectKey(), exercise.getTemplateBuildPlanId(), true, false);
        mockDelegate.mockCheckIfBuildPlanExists(exercise.getProjectKey(), exercise.getSolutionBuildPlanId(), true, false);

        var consistencyErrors = request.getList("/api/programming-exercises/" + exercise.getId() + "/consistency-check", HttpStatus.OK, ConsistencyErrorDTO.class);
        assertThat(consistencyErrors).isEmpty();
    }

    /**
     * Test consistencyCheck feature with programming exercise
     * with missing VCS project
     * @throws Exception if an error occurs
     */
    public void testCheckConsistencyOfProgrammingExercise_missingVCSProject() throws Exception {
        var exercise = (ProgrammingExercise) course.getExercises().iterator().next();
        exercise = programmingExerciseRepository.findByIdWithTemplateAndSolutionParticipationElseThrow(exercise.getId());

        mockDelegate.mockCheckIfProjectExistsInVcs(exercise, false);
        mockDelegate.mockCheckIfBuildPlanExists(exercise.getProjectKey(), exercise.getTemplateBuildPlanId(), true, false);
        mockDelegate.mockCheckIfBuildPlanExists(exercise.getProjectKey(), exercise.getSolutionBuildPlanId(), true, false);

        var consistencyErrors = request.getList("/api/programming-exercises/" + exercise.getId() + "/consistency-check", HttpStatus.OK, ConsistencyErrorDTO.class);
        assertThat(consistencyErrors).hasSize(1);
        assertThat(consistencyErrors.get(0).getType()).isEqualTo(ConsistencyErrorDTO.ErrorType.VCS_PROJECT_MISSING);
    }

    /**
     * Test consistencyCheck feature with programming exercise
     * with missing VCS repositories
     * @throws Exception if an error occurs
     */
    public void testCheckConsistencyOfProgrammingExercise_missingVCSRepos() throws Exception {
        var exercise = (ProgrammingExercise) course.getExercises().iterator().next();
        exercise = programmingExerciseRepository.findByIdWithTemplateAndSolutionParticipationElseThrow(exercise.getId());

        mockDelegate.mockCheckIfProjectExistsInVcs(exercise, true);
        mockDelegate.mockRepositoryUrlIsValid(exercise.getVcsTemplateRepositoryUrl(), exercise.getProjectKey(), false);
        mockDelegate.mockRepositoryUrlIsValid(exercise.getVcsTestRepositoryUrl(), exercise.getProjectKey(), false);
        mockDelegate.mockRepositoryUrlIsValid(exercise.getVcsSolutionRepositoryUrl(), exercise.getProjectKey(), false);
        mockDelegate.mockCheckIfBuildPlanExists(exercise.getProjectKey(), exercise.getTemplateBuildPlanId(), true, false);
        mockDelegate.mockCheckIfBuildPlanExists(exercise.getProjectKey(), exercise.getSolutionBuildPlanId(), true, false);

        List<ConsistencyErrorDTO> expectedErrors = new ArrayList<>();
        expectedErrors.add(new ConsistencyErrorDTO(exercise, ConsistencyErrorDTO.ErrorType.TEMPLATE_REPO_MISSING));
        expectedErrors.add(new ConsistencyErrorDTO(exercise, ConsistencyErrorDTO.ErrorType.SOLUTION_REPO_MISSING));
        expectedErrors.add(new ConsistencyErrorDTO(exercise, ConsistencyErrorDTO.ErrorType.TEST_REPO_MISSING));

        var consistencyErrors = request.getList("/api/programming-exercises/" + exercise.getId() + "/consistency-check", HttpStatus.OK, ConsistencyErrorDTO.class);
        assertThat(consistencyErrors).hasSize(3).containsAll(expectedErrors);
    }

    /**
     * Test consistencyCheck feature with programming exercise
     * with missing Build Plans
     * @throws Exception if an error occurs
     */
    public void testCheckConsistencyOfProgrammingExercise_buildPlansMissing() throws Exception {
        var exercise = (ProgrammingExercise) course.getExercises().iterator().next();
        exercise = programmingExerciseRepository.findByIdWithTemplateAndSolutionParticipationElseThrow(exercise.getId());

        mockDelegate.mockCheckIfProjectExistsInVcs(exercise, true);
        mockDelegate.mockRepositoryUrlIsValid(exercise.getVcsTemplateRepositoryUrl(), exercise.getProjectKey(), true);
        mockDelegate.mockRepositoryUrlIsValid(exercise.getVcsTestRepositoryUrl(), exercise.getProjectKey(), true);
        mockDelegate.mockRepositoryUrlIsValid(exercise.getVcsSolutionRepositoryUrl(), exercise.getProjectKey(), true);
        mockDelegate.mockCheckIfBuildPlanExists(exercise.getProjectKey(), exercise.getTemplateBuildPlanId(), false, false);
        mockDelegate.mockCheckIfBuildPlanExists(exercise.getProjectKey(), exercise.getSolutionBuildPlanId(), false, false);

        List<ConsistencyErrorDTO> expectedErrors = new ArrayList<>();
        expectedErrors.add(new ConsistencyErrorDTO(exercise, ConsistencyErrorDTO.ErrorType.TEMPLATE_BUILD_PLAN_MISSING));
        expectedErrors.add(new ConsistencyErrorDTO(exercise, ConsistencyErrorDTO.ErrorType.SOLUTION_BUILD_PLAN_MISSING));

        var consistencyErrors = request.getList("/api/programming-exercises/" + exercise.getId() + "/consistency-check", HttpStatus.OK, ConsistencyErrorDTO.class);
        assertThat(consistencyErrors).hasSize(2);
        assertThat(consistencyErrors).containsAll(expectedErrors);

    }

    /**
     * Test consistencyCheck feature with a local simulation
     * of a programming exercise
     * @throws Exception if an error occurs
     */
    public void testCheckConsistencyOfProgrammingExercise_isLocalSimulation() throws Exception {
        var exercise = (ProgrammingExercise) course.getExercises().iterator().next();
        exercise.setTestRepositoryUrl("artemislocalhost/to/set/localSimulation/to/true");
        exercise = programmingExerciseRepository.save(exercise);

        var consistencyErrors = request.getList("/api/programming-exercises/" + exercise.getId() + "/consistency-check", HttpStatus.OK, ConsistencyErrorDTO.class);
        assertThat(consistencyErrors).hasSize(1);
        assertThat(consistencyErrors.get(0).getType()).isEqualTo(ConsistencyErrorDTO.ErrorType.IS_LOCAL_SIMULATION);
    }

    /**
     * Test consistencyCheck REST Endpoint with unauthorized user
     *
     * @throws Exception if an error occurs
     */
    public void testCheckConsistencyOfProgrammingExercise_forbidden() throws Exception {
        // remove user from course group to simulate an unauthorized situation
        User notAuthorizedUser = userRepository.getUser();
        notAuthorizedUser.setGroups(new HashSet<>());
        userRepository.save(notAuthorizedUser);

        var exercise = (ProgrammingExercise) course.getExercises().iterator().next();
        request.get("/api/programming-exercises/" + exercise.getId() + "/consistency-check", HttpStatus.FORBIDDEN, ConsistencyErrorDTO.class);
    }
}
