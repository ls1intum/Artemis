package de.tum.in.www1.artemis.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import java.net.MalformedURLException;

import org.junit.jupiter.api.Test;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.domain.VcsRepositoryUrl;
import de.tum.in.www1.artemis.exception.VersionControlException;

public class UrlServiceTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Test
    public void testGetRepositorySlugFromRepositoryUrl() throws MalformedURLException {
        String repoSlug = UrlService.getRepositorySlugFromRepositoryUrl(new VcsRepositoryUrl("https://ga42xab@bitbucket.ase.in.tum.de/scm/EIST2016RME/RMEXERCISE-ga42xab"));
        assertThat(repoSlug).isEqualTo("RMEXERCISE-ga42xab");
        repoSlug = UrlService.getRepositorySlugFromRepositoryUrl(new VcsRepositoryUrl("https://ga63fup@repobruegge.in.tum.de/scm/EIST2016RME/RMEXERCISE-ga63fup.git"));
        assertThat(repoSlug).isEqualTo("RMEXERCISE-ga63fup");
        repoSlug = UrlService.getRepositorySlugFromRepositoryUrl(new VcsRepositoryUrl("https://artemistest2gitlab.ase.in.tum.de/TESTADAPTER/testadapter-exercise.git"));
        assertThat(repoSlug).isEqualTo("testadapter-exercise");
        repoSlug = UrlService.getRepositorySlugFromRepositoryUrl(new VcsRepositoryUrl("https://turdiu@artemistest2gitlab.ase.in.tum.de/FTCSCAGRADING1/ftcscagrading1-turdiu"));
        assertThat(repoSlug).isEqualTo("ftcscagrading1-turdiu");

        assertThrows(VersionControlException.class, () -> UrlService.getRepositorySlugFromRepositoryUrl(new VcsRepositoryUrl("https://bitbucket.ase.in.tum.de")));
    }

    @Test
    public void testGetRepositoryPathFromRepositoryUrl() throws MalformedURLException {
        String repoSlug = UrlService.getRepositoryPathFromRepositoryUrl(new VcsRepositoryUrl("https://ga42xab@bitbucket.ase.in.tum.de/scm/EIST2016RME/RMEXERCISE-ga42xab.git"));
        assertThat(repoSlug).isEqualTo("EIST2016RME/RMEXERCISE-ga42xab");
        repoSlug = UrlService.getRepositoryPathFromRepositoryUrl(new VcsRepositoryUrl("https://ga63fup@repobruegge.in.tum.de/scm/EIST2016RME/RMEXERCISE-ga63fup.git"));
        assertThat(repoSlug).isEqualTo("EIST2016RME/RMEXERCISE-ga63fup");
        repoSlug = UrlService.getRepositoryPathFromRepositoryUrl(new VcsRepositoryUrl("https://artemistest2gitlab.ase.in.tum.de/TESTADAPTER/testadapter-exercise.git"));
        assertThat(repoSlug).isEqualTo("TESTADAPTER/testadapter-exercise");
        repoSlug = UrlService.getRepositoryPathFromRepositoryUrl(new VcsRepositoryUrl("https://turdiu@artemistest2gitlab.ase.in.tum.de/FTCSCAGRADING1/ftcscagrading1-turdiu.git"));
        assertThat(repoSlug).isEqualTo("FTCSCAGRADING1/ftcscagrading1-turdiu");

        assertThrows(VersionControlException.class, () -> UrlService.getRepositoryPathFromRepositoryUrl(new VcsRepositoryUrl("https://bitbucket.ase.in.tum.de")));
    }

    @Test
    public void testProjectKeyFromRepositoryUrl() throws MalformedURLException {
        String repoSlug = UrlService.getProjectKeyFromRepositoryUrl(new VcsRepositoryUrl("https://ga42xab@bitbucket.ase.in.tum.de/scm/EIST2016RME/RMEXERCISE-ga42xab.git"));
        assertThat(repoSlug).isEqualTo("EIST2016RME");
        repoSlug = UrlService.getProjectKeyFromRepositoryUrl(new VcsRepositoryUrl("https://ga63fup@repobruegge.in.tum.de/scm/EIST2016RME/RMEXERCISE-ga63fup.git"));
        assertThat(repoSlug).isEqualTo("EIST2016RME");
        repoSlug = UrlService.getProjectKeyFromRepositoryUrl(new VcsRepositoryUrl("https://artemistest2gitlab.ase.in.tum.de/TESTADAPTER/testadapter-exercise.git"));
        assertThat(repoSlug).isEqualTo("TESTADAPTER");
        repoSlug = UrlService.getProjectKeyFromRepositoryUrl(new VcsRepositoryUrl("https://turdiu@artemistest2gitlab.ase.in.tum.de/FTCSCAGRADING1/ftcscagrading1-turdiu.git"));
        assertThat(repoSlug).isEqualTo("FTCSCAGRADING1");

        assertThrows(VersionControlException.class, () -> UrlService.getProjectKeyFromRepositoryUrl(new VcsRepositoryUrl("https://bitbucket.ase.in.tum.de")));
    }
}
