package de.tum.in.www1.artemis.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.io.File;
import java.net.URISyntaxException;

import org.junit.jupiter.api.Test;

import de.tum.in.www1.artemis.AbstractSpringIntegrationIndependentTest;
import de.tum.in.www1.artemis.domain.VcsRepositoryUri;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseStudentParticipation;
import de.tum.in.www1.artemis.domain.participation.SolutionProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.domain.participation.TemplateProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.exception.VersionControlException;

class UriServiceTest extends AbstractSpringIntegrationIndependentTest {

    private final VcsRepositoryUri repositoryUri1 = new VcsRepositoryUri("https://ab12cde@repobruegge.in.tum.de/scm/EIST2016RME/RMEXERCISE-ab12cde.git");

    private final VcsRepositoryUri repositoryUri2 = new VcsRepositoryUri("https://artemistest2gitlab.ase.in.tum.de/TESTADAPTER/testadapter-exercise.git");

    private final VcsRepositoryUri repositoryUri3 = new VcsRepositoryUri("https://username@artemistest2gitlab.ase.in.tum.de/FTCSCAGRADING1/ftcscagrading1-username");

    private final VcsRepositoryUri fileRepositoryUri1 = new VcsRepositoryUri(new File("C:/Users/Admin/AppData/Local/Temp/studentOriginRepo1644180397872264950"));

    private final VcsRepositoryUri fileRepositoryUri2 = new VcsRepositoryUri(new File("/var/folders/vc/sk85td_s54v7w9tjq07b0_q80000gn/T/studentTeamOriginRepo420037178325056205"));

    /**
     * empty constructor for exception handling
     *
     * @throws URISyntaxException exception in case the above URLs would be malformed
     */
    public UriServiceTest() throws URISyntaxException {
        // Intentionally empty, see JavaDoc
    }

    @Test
    void testGetRepositorySlugFromRepositoryUri() {
        String repoSlug = uriService.getRepositorySlugFromRepositoryUri(repositoryUri1);
        assertThat(repoSlug).isEqualTo("RMEXERCISE-ab12cde");
        repoSlug = uriService.getRepositorySlugFromRepositoryUri(repositoryUri2);
        assertThat(repoSlug).isEqualTo("testadapter-exercise");
        repoSlug = uriService.getRepositorySlugFromRepositoryUri(repositoryUri3);
        assertThat(repoSlug).isEqualTo("ftcscagrading1-username");

        assertThatExceptionOfType(VersionControlException.class)
                .isThrownBy(() -> uriService.getRepositorySlugFromRepositoryUri(new VcsRepositoryUri("https://artemistest2gitlab.ase.in.tum.de")));
    }

    @Test
    void testGetRepositoryPathFromRepositoryUri() {
        String repoSlug = uriService.getRepositoryPathFromRepositoryUri(repositoryUri1);
        assertThat(repoSlug).isEqualTo("EIST2016RME/RMEXERCISE-ab12cde");
        repoSlug = uriService.getRepositoryPathFromRepositoryUri(repositoryUri2);
        assertThat(repoSlug).isEqualTo("TESTADAPTER/testadapter-exercise");
        repoSlug = uriService.getRepositoryPathFromRepositoryUri(repositoryUri3);
        assertThat(repoSlug).isEqualTo("FTCSCAGRADING1/ftcscagrading1-username");

        assertThatExceptionOfType(VersionControlException.class)
                .isThrownBy(() -> uriService.getRepositoryPathFromRepositoryUri(new VcsRepositoryUri("https://artemistest2gitlab.ase.in.tum.de")));
    }

    @Test
    void testProjectKeyFromRepositoryUri() {
        String repoSlug = uriService.getProjectKeyFromRepositoryUri(repositoryUri1);
        assertThat(repoSlug).isEqualTo("EIST2016RME");
        repoSlug = uriService.getProjectKeyFromRepositoryUri(repositoryUri2);
        assertThat(repoSlug).isEqualTo("TESTADAPTER");
        repoSlug = uriService.getProjectKeyFromRepositoryUri(repositoryUri3);
        assertThat(repoSlug).isEqualTo("FTCSCAGRADING1");

        assertThatExceptionOfType(VersionControlException.class)
                .isThrownBy(() -> uriService.getProjectKeyFromRepositoryUri(new VcsRepositoryUri("https://artemistest2gitlab.ase.in.tum.de")));
    }

    @Test
    void testGetFolderNameForRepositoryUri() {
        assertThat(repositoryUri1.folderNameForRepositoryUri()).isEqualTo("/EIST2016RME/RMEXERCISE-ab12cde");
        assertThat(repositoryUri2.folderNameForRepositoryUri()).isEqualTo("/TESTADAPTER/testadapter-exercise");
        assertThat(repositoryUri3.folderNameForRepositoryUri()).isEqualTo("/FTCSCAGRADING1/ftcscagrading1-username");
        assertThat(fileRepositoryUri1.folderNameForRepositoryUri()).isEqualTo("studentOriginRepo1644180397872264950");
        assertThat(fileRepositoryUri2.folderNameForRepositoryUri()).isEqualTo("studentTeamOriginRepo420037178325056205");
    }

    @Test
    void testUserIndependentRepositoryUri() {
        var solutionProgrammingExerciseParticipation = new SolutionProgrammingExerciseParticipation();
        solutionProgrammingExerciseParticipation.setRepositoryUri(repositoryUri2.toString());
        assertThat(solutionProgrammingExerciseParticipation.getUserIndependentRepositoryUri())
                .isEqualTo("https://artemistest2gitlab.ase.in.tum.de/TESTADAPTER/testadapter-exercise.git");

        var templateProgrammingExerciseParticipation = new TemplateProgrammingExerciseParticipation();
        templateProgrammingExerciseParticipation.setRepositoryUri(repositoryUri1.toString());
        assertThat(templateProgrammingExerciseParticipation.getUserIndependentRepositoryUri()).isEqualTo("https://repobruegge.in.tum.de/scm/EIST2016RME/RMEXERCISE-ab12cde.git");

        var studentParticipation1 = new ProgrammingExerciseStudentParticipation();
        studentParticipation1.setRepositoryUri(repositoryUri2.toString());
        assertThat(studentParticipation1.getUserIndependentRepositoryUri()).isEqualTo("https://artemistest2gitlab.ase.in.tum.de/TESTADAPTER/testadapter-exercise.git");

        var studentParticipation2 = new ProgrammingExerciseStudentParticipation();
        studentParticipation2.setRepositoryUri(repositoryUri3.toString());
        assertThat(studentParticipation2.getUserIndependentRepositoryUri()).isEqualTo("https://artemistest2gitlab.ase.in.tum.de/FTCSCAGRADING1/ftcscagrading1-username");

        assertThat(new ProgrammingExerciseStudentParticipation().getUserIndependentRepositoryUri()).isNull();

        var studentParticipation3 = new ProgrammingExerciseStudentParticipation();
        studentParticipation3.setRepositoryUri("http://localhost:8080/Assignment/rest/words/{name}/protection");
        assertThat(studentParticipation3.getUserIndependentRepositoryUri()).isNull();
    }

    @Test
    void testGetPlainUrl() {
        assertThat(uriService.getPlainUriFromRepositoryUri(repositoryUri1)).isEqualTo("https://repobruegge.in.tum.de/scm/EIST2016RME/RMEXERCISE-ab12cde.git");
        assertThat(uriService.getPlainUriFromRepositoryUri(repositoryUri2)).isEqualTo("https://artemistest2gitlab.ase.in.tum.de/TESTADAPTER/testadapter-exercise.git");
        assertThat(uriService.getPlainUriFromRepositoryUri(repositoryUri3)).isEqualTo("https://artemistest2gitlab.ase.in.tum.de/FTCSCAGRADING1/ftcscagrading1-username");
    }
}
