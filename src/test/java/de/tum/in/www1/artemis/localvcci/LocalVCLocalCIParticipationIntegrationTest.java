package de.tum.in.www1.artemis.localvcci;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.AbstractSpringIntegrationLocalCILocalVCTest;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.domain.participation.TemplateProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.exercise.ExerciseUtilService;
import de.tum.in.www1.artemis.exercise.programmingexercise.ProgrammingExerciseUtilService;
import de.tum.in.www1.artemis.service.connectors.localvc.LocalVCRepositoryUri;
import de.tum.in.www1.artemis.util.LocalRepository;

class LocalVCLocalCIParticipationIntegrationTest extends AbstractSpringIntegrationLocalCILocalVCTest {

    private static final String TEST_PREFIX = "participationlocalvclocalci";

    @Autowired
    private ProgrammingExerciseUtilService programmingExerciseUtilService;

    @Autowired
    private ExerciseUtilService exerciseUtilService;

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testStartParticipation() throws Exception {
        userUtilService.addUsers(TEST_PREFIX, 1, 0, 0, 0);
        Course course = programmingExerciseUtilService.addCourseWithOneProgrammingExercise();
        ProgrammingExercise programmingExercise = exerciseUtilService.getFirstExerciseWithType(course, ProgrammingExercise.class);
        String projectKey = programmingExercise.getProjectKey();
        programmingExercise.setStartDate(ZonedDateTime.now().minusHours(1));
        // Set the branch to null to force the usage of LocalVCService#getDefaultBranchOfRepository().
        programmingExercise.setBranch(null);
        programmingExerciseRepository.save(programmingExercise);
        programmingExercise = programmingExerciseRepository.findWithAllParticipationsById(programmingExercise.getId()).orElseThrow();

        // Prepare the template repository to copy the student assignment repository from.
        String templateRepositorySlug = projectKey.toLowerCase() + "-exercise";
        TemplateProgrammingExerciseParticipation templateParticipation = programmingExercise.getTemplateParticipation();
        templateParticipation.setRepositoryUri(localVCBaseUrl + "/git/" + projectKey + "/" + templateRepositorySlug + ".git");
        templateProgrammingExerciseParticipationRepository.save(templateParticipation);
        LocalRepository templateRepository = localVCLocalCITestService.createAndConfigureLocalRepository(projectKey, templateRepositorySlug);

        User user = userUtilService.getUserByLogin(TEST_PREFIX + "student1");

        StudentParticipation participation = request.postWithResponseBody("/api/exercises/" + programmingExercise.getId() + "/participations", null, StudentParticipation.class,
                HttpStatus.CREATED);
        assertThat(participation).isNotNull();
        assertThat(participation.isPracticeMode()).isFalse();
        assertThat(participation.getStudent()).contains(user);
        LocalVCRepositoryUri studentAssignmentRepositoryUri = new LocalVCRepositoryUri(projectKey, projectKey.toLowerCase() + "-" + TEST_PREFIX + "student1", localVCBaseUrl);
        assertThat(studentAssignmentRepositoryUri.getLocalRepositoryPath(localVCBasePath)).exists();

        templateRepository.resetLocalRepo();
    }
}
