package de.tum.in.www1.artemis.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.net.URISyntaxException;

import org.junit.jupiter.api.Test;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.domain.VcsRepositoryUrl;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseStudentParticipation;
import de.tum.in.www1.artemis.domain.participation.SolutionProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.domain.participation.TemplateProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.exception.VersionControlException;

class UrlServiceTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    private final VcsRepositoryUrl repositoryUrl1 = new VcsRepositoryUrl("https://ab123cd@bitbucket.ase.in.tum.de/scm/EIST2016RME/RMEXERCISE-ab123cd");

    private final VcsRepositoryUrl repositoryUrl2 = new VcsRepositoryUrl("https://ab123cd@repobruegge.in.tum.de/scm/EIST2016RME/RMEXERCISE-ab123cd.git");

    private final VcsRepositoryUrl repositoryUrl3 = new VcsRepositoryUrl("https://artemistest2gitlab.ase.in.tum.de/TESTADAPTER/testadapter-exercise.git");

    private final VcsRepositoryUrl repositoryUrl4 = new VcsRepositoryUrl("https://username@artemistest2gitlab.ase.in.tum.de/FTCSCAGRADING1/ftcscagrading1-username");

    private final VcsRepositoryUrl repositoryUrl5 = new VcsRepositoryUrl("ssh://git@bitbucket.ase.in.tum.de:7999/eist20l06e03/eist20l06e03-ab123cd.git");

    private final VcsRepositoryUrl fileRepositoryUrl1 = new VcsRepositoryUrl(new File("C:/Users/Admin/AppData/Local/Temp/studentOriginRepo1644180397872264950"));

    private final VcsRepositoryUrl fileRepositoryUrl2 = new VcsRepositoryUrl(new File("/var/folders/vc/sk85td_s54v7w9tjq07b0_q80000gn/T/studentTeamOriginRepo420037178325056205"));

    /**
     * empty constructor for exception handling
     * @throws URISyntaxException exception in case the above URLs would be malformed
     */
    public UrlServiceTest() throws URISyntaxException {
        // Intentionally empty, see JavaDoc
    }

    @Test
    void testGetRepositorySlugFromRepositoryUrl() {
        String repoSlug = urlService.getRepositorySlugFromRepositoryUrl(repositoryUrl1);
        assertThat(repoSlug).isEqualTo("RMEXERCISE-ab123cd");
        repoSlug = urlService.getRepositorySlugFromRepositoryUrl(repositoryUrl2);
        assertThat(repoSlug).isEqualTo("RMEXERCISE-ab123cd");
        repoSlug = urlService.getRepositorySlugFromRepositoryUrl(repositoryUrl3);
        assertThat(repoSlug).isEqualTo("testadapter-exercise");
        repoSlug = urlService.getRepositorySlugFromRepositoryUrl(repositoryUrl4);
        assertThat(repoSlug).isEqualTo("ftcscagrading1-username");

        assertThrows(VersionControlException.class, () -> urlService.getRepositorySlugFromRepositoryUrl(new VcsRepositoryUrl("https://bitbucket.ase.in.tum.de")));
    }

    @Test
    void testGetRepositoryPathFromRepositoryUrl() {
        String repoSlug = urlService.getRepositoryPathFromRepositoryUrl(repositoryUrl1);
        assertThat(repoSlug).isEqualTo("EIST2016RME/RMEXERCISE-ab123cd");
        repoSlug = urlService.getRepositoryPathFromRepositoryUrl(repositoryUrl2);
        assertThat(repoSlug).isEqualTo("EIST2016RME/RMEXERCISE-ab123cd");
        repoSlug = urlService.getRepositoryPathFromRepositoryUrl(repositoryUrl3);
        assertThat(repoSlug).isEqualTo("TESTADAPTER/testadapter-exercise");
        repoSlug = urlService.getRepositoryPathFromRepositoryUrl(repositoryUrl4);
        assertThat(repoSlug).isEqualTo("FTCSCAGRADING1/ftcscagrading1-username");

        assertThrows(VersionControlException.class, () -> urlService.getRepositoryPathFromRepositoryUrl(new VcsRepositoryUrl("https://bitbucket.ase.in.tum.de")));
    }

    @Test
    void testProjectKeyFromRepositoryUrl() {
        String repoSlug = urlService.getProjectKeyFromRepositoryUrl(repositoryUrl1);
        assertThat(repoSlug).isEqualTo("EIST2016RME");
        repoSlug = urlService.getProjectKeyFromRepositoryUrl(repositoryUrl2);
        assertThat(repoSlug).isEqualTo("EIST2016RME");
        repoSlug = urlService.getProjectKeyFromRepositoryUrl(repositoryUrl3);
        assertThat(repoSlug).isEqualTo("TESTADAPTER");
        repoSlug = urlService.getProjectKeyFromRepositoryUrl(repositoryUrl4);
        assertThat(repoSlug).isEqualTo("FTCSCAGRADING1");

        assertThrows(VersionControlException.class, () -> urlService.getProjectKeyFromRepositoryUrl(new VcsRepositoryUrl("https://bitbucket.ase.in.tum.de")));
    }

    @Test
    void testGetFolderNameForRepositoryUrl() {
        assertThat(repositoryUrl1.folderNameForRepositoryUrl()).isEqualTo("/EIST2016RME/RMEXERCISE-ab123cd");
        assertThat(repositoryUrl2.folderNameForRepositoryUrl()).isEqualTo("/EIST2016RME/RMEXERCISE-ab123cd");
        assertThat(repositoryUrl3.folderNameForRepositoryUrl()).isEqualTo("/TESTADAPTER/testadapter-exercise");
        assertThat(repositoryUrl4.folderNameForRepositoryUrl()).isEqualTo("/FTCSCAGRADING1/ftcscagrading1-username");
        assertThat(repositoryUrl5.folderNameForRepositoryUrl()).isEqualTo("/eist20l06e03/eist20l06e03-ab123cd");
        assertThat(fileRepositoryUrl1.folderNameForRepositoryUrl()).isEqualTo("studentOriginRepo1644180397872264950");
        assertThat(fileRepositoryUrl2.folderNameForRepositoryUrl()).isEqualTo("studentTeamOriginRepo420037178325056205");
    }

    @Test
    void testUserIndependentRepositoryUrl() {
        var solutionProgrammingExerciseParticipation = new SolutionProgrammingExerciseParticipation();
        solutionProgrammingExerciseParticipation.setRepositoryUrl(repositoryUrl1.toString());
        assertThat(solutionProgrammingExerciseParticipation.getUserIndependentRepositoryUrl()).isEqualTo("https://bitbucket.ase.in.tum.de/scm/EIST2016RME/RMEXERCISE-ab123cd");

        var templateProgrammingExerciseParticipation = new TemplateProgrammingExerciseParticipation();
        templateProgrammingExerciseParticipation.setRepositoryUrl(repositoryUrl2.toString());
        assertThat(templateProgrammingExerciseParticipation.getUserIndependentRepositoryUrl()).isEqualTo("https://repobruegge.in.tum.de/scm/EIST2016RME/RMEXERCISE-ab123cd.git");

        var studentParticipation1 = new ProgrammingExerciseStudentParticipation();
        studentParticipation1.setRepositoryUrl(repositoryUrl3.toString());
        assertThat(studentParticipation1.getUserIndependentRepositoryUrl()).isEqualTo("https://artemistest2gitlab.ase.in.tum.de/TESTADAPTER/testadapter-exercise.git");

        var studentParticipation2 = new ProgrammingExerciseStudentParticipation();
        studentParticipation2.setRepositoryUrl(repositoryUrl4.toString());
        assertThat(studentParticipation2.getUserIndependentRepositoryUrl()).isEqualTo("https://artemistest2gitlab.ase.in.tum.de/FTCSCAGRADING1/ftcscagrading1-username");

        assertThat(new ProgrammingExerciseStudentParticipation().getUserIndependentRepositoryUrl()).isNull();

        var studentParticipation3 = new ProgrammingExerciseStudentParticipation();
        studentParticipation3.setRepositoryUrl("http://localhost:8080/Assignment/rest/words/{name}/protection");
        assertThat(studentParticipation3.getUserIndependentRepositoryUrl()).isNull();
    }
}
