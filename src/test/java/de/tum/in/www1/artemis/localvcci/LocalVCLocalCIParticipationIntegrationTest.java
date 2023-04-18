package de.tum.in.www1.artemis.localvcci;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZonedDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.AbstractSpringIntegrationLocalCILocalVCTest;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.domain.participation.TemplateProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.service.connectors.localvc.LocalVCRepositoryUrl;
import de.tum.in.www1.artemis.util.LocalRepository;

class LocalVCLocalCIParticipationIntegrationTest extends AbstractSpringIntegrationLocalCILocalVCTest {

    private static final String TEST_PREFIX = "participationlocalvclocalci";

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testStartParticipation() throws Exception {
        database.addUsers(TEST_PREFIX, 1, 0, 0, 0);
        Course course = database.addCourseWithOneProgrammingExercise();
        ProgrammingExercise programmingExercise = database.getFirstExerciseWithType(course, ProgrammingExercise.class);
        String projectKey = programmingExercise.getProjectKey();
        programmingExercise.setStartDate(ZonedDateTime.now().minusHours(1));
        programmingExerciseRepository.save(programmingExercise);
        programmingExercise = programmingExerciseRepository.findWithAllParticipationsById(programmingExercise.getId()).orElseThrow();

        // Prepare the template repository to copy the student assignment repository from.
        String templateRepositorySlug = projectKey.toLowerCase() + "-exercise";
        TemplateProgrammingExerciseParticipation templateParticipation = programmingExercise.getTemplateParticipation();
        templateParticipation.setRepositoryUrl(localVCBaseUrl + "/git/" + projectKey + "/" + templateRepositorySlug + ".git");
        templateProgrammingExerciseParticipationRepository.save(templateParticipation);
        Path remoteTemplateRepositoryFolder = localVCLocalCITestService.createRepositoryFolderInTempDirectory(projectKey, templateRepositorySlug);
        LocalRepository templateRepository = new LocalRepository(defaultBranch);
        templateRepository.configureRepos("localTemplate", remoteTemplateRepositoryFolder);

        User user = database.getUserByLogin(TEST_PREFIX + "student1");

        StudentParticipation participation = request.postWithResponseBody("/api/exercises/" + programmingExercise.getId() + "/participations", null, StudentParticipation.class,
                HttpStatus.CREATED);
        assertThat(participation).isNotNull();
        assertThat(participation.isTestRun()).isFalse();
        assertThat(participation.getStudent()).contains(user);
        LocalVCRepositoryUrl studentAssignmentRepositoryUrl = new LocalVCRepositoryUrl(projectKey, projectKey.toLowerCase() + "-" + TEST_PREFIX + "student1", localVCBaseUrl);
        assertThat(Files.exists(studentAssignmentRepositoryUrl.getLocalRepositoryPath(localVCBasePath))).isTrue();

        templateRepository.resetLocalRepo();
    }
}
