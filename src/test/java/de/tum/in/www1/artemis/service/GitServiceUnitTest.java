package de.tum.in.www1.artemis.service;

import org.junit.Test;

import java.net.MalformedURLException;
import java.net.URL;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Created by Josias Montag on 11.10.16.
 */
public class GitServiceUnitTest {


    @Test
    public void testFolderNameForRepositoryUrl() throws MalformedURLException {

        GitService gitService = new GitService();

        URL repoUrl = new URL("https://ga68dic@repobruegge.in.tum.de/scm/EIST2016RME/RMEXERCISE-ga68dic.git");

        String folderName = gitService.folderNameForRepositoryUrl(repoUrl);
        assertThat(folderName).isEqualTo("EIST2016RME/RMEXERCISE-ga68dic");


    }
}
