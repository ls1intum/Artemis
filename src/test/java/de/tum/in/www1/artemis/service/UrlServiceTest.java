package de.tum.in.www1.artemis.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import java.net.MalformedURLException;

import org.junit.jupiter.api.Test;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.domain.VcsRepositoryUrl;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseStudentParticipation;
import de.tum.in.www1.artemis.domain.participation.SolutionProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.domain.participation.TemplateProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.exception.VersionControlException;

public class UrlServiceTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    private final VcsRepositoryUrl repositoryUrl1 = new VcsRepositoryUrl("https://ga42xab@bitbucket.ase.in.tum.de/scm/EIST2016RME/RMEXERCISE-ga42xab");

    private final VcsRepositoryUrl repositoryUrl2 = new VcsRepositoryUrl("https://ga63fup@repobruegge.in.tum.de/scm/EIST2016RME/RMEXERCISE-ga63fup.git");

    private final VcsRepositoryUrl repositoryUrl3 = new VcsRepositoryUrl("https://artemistest2gitlab.ase.in.tum.de/TESTADAPTER/testadapter-exercise.git");

    private final VcsRepositoryUrl repositoryUrl4 = new VcsRepositoryUrl("https://turdiu@artemistest2gitlab.ase.in.tum.de/FTCSCAGRADING1/ftcscagrading1-turdiu");

    /**
     * empty constructor for exception handling
     * @throws MalformedURLException exception in case the above URLs would be malformed
     */
    public UrlServiceTest() throws MalformedURLException {
    }

    @Test
    public void testGetRepositorySlugFromRepositoryUrl() {
        String repoSlug = urlService.getRepositorySlugFromRepositoryUrl(repositoryUrl1);
        assertThat(repoSlug).isEqualTo("RMEXERCISE-ga42xab");
        repoSlug = urlService.getRepositorySlugFromRepositoryUrl(repositoryUrl2);
        assertThat(repoSlug).isEqualTo("RMEXERCISE-ga63fup");
        repoSlug = urlService.getRepositorySlugFromRepositoryUrl(repositoryUrl3);
        assertThat(repoSlug).isEqualTo("testadapter-exercise");
        repoSlug = urlService.getRepositorySlugFromRepositoryUrl(repositoryUrl4);
        assertThat(repoSlug).isEqualTo("ftcscagrading1-turdiu");

        assertThrows(VersionControlException.class, () -> urlService.getRepositorySlugFromRepositoryUrl(new VcsRepositoryUrl("https://bitbucket.ase.in.tum.de")));
    }

    @Test
    public void testGetRepositoryPathFromRepositoryUrl() {
        String repoSlug = urlService.getRepositoryPathFromRepositoryUrl(repositoryUrl1);
        assertThat(repoSlug).isEqualTo("EIST2016RME/RMEXERCISE-ga42xab");
        repoSlug = urlService.getRepositoryPathFromRepositoryUrl(repositoryUrl2);
        assertThat(repoSlug).isEqualTo("EIST2016RME/RMEXERCISE-ga63fup");
        repoSlug = urlService.getRepositoryPathFromRepositoryUrl(repositoryUrl3);
        assertThat(repoSlug).isEqualTo("TESTADAPTER/testadapter-exercise");
        repoSlug = urlService.getRepositoryPathFromRepositoryUrl(repositoryUrl4);
        assertThat(repoSlug).isEqualTo("FTCSCAGRADING1/ftcscagrading1-turdiu");

        assertThrows(VersionControlException.class, () -> urlService.getRepositoryPathFromRepositoryUrl(new VcsRepositoryUrl("https://bitbucket.ase.in.tum.de")));
    }

    @Test
    public void testProjectKeyFromRepositoryUrl() {
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
    public void testUserIndependentRepositoryUrl() {
        var solutionProgrammingExerciseParticipation = new SolutionProgrammingExerciseParticipation();
        solutionProgrammingExerciseParticipation.setRepositoryUrl(repositoryUrl1.toString());
        assertThat(solutionProgrammingExerciseParticipation.getUserIndependentRepositoryUrl()).isEqualTo("https://bitbucket.ase.in.tum.de/scm/EIST2016RME/RMEXERCISE-ga42xab");

        var templateProgrammingExerciseParticipation = new TemplateProgrammingExerciseParticipation();
        templateProgrammingExerciseParticipation.setRepositoryUrl(repositoryUrl2.toString());
        assertThat(templateProgrammingExerciseParticipation.getUserIndependentRepositoryUrl()).isEqualTo("https://repobruegge.in.tum.de/scm/EIST2016RME/RMEXERCISE-ga63fup.git");

        var studentParticipation1 = new ProgrammingExerciseStudentParticipation();
        studentParticipation1.setRepositoryUrl(repositoryUrl3.toString());
        assertThat(studentParticipation1.getUserIndependentRepositoryUrl()).isEqualTo("https://artemistest2gitlab.ase.in.tum.de/TESTADAPTER/testadapter-exercise.git");

        var studentParticipation2 = new ProgrammingExerciseStudentParticipation();
        studentParticipation2.setRepositoryUrl(repositoryUrl4.toString());
        assertThat(studentParticipation2.getUserIndependentRepositoryUrl()).isEqualTo("https://artemistest2gitlab.ase.in.tum.de/FTCSCAGRADING1/ftcscagrading1-turdiu");

        assertThat(new ProgrammingExerciseStudentParticipation().getUserIndependentRepositoryUrl()).isNull();

        var studentParticipation3 = new ProgrammingExerciseStudentParticipation();
        studentParticipation3.setRepositoryUrl("htps/abc.luka@bitbucket");
        assertThat(studentParticipation3.getUserIndependentRepositoryUrl()).isNull();
    }
}
