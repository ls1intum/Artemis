package de.tum.cit.aet.artemis.programming.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import de.tum.cit.aet.artemis.core.exception.GitException;
import de.tum.cit.aet.artemis.programming.domain.Repository;
import de.tum.cit.aet.artemis.programming.service.localvc.LocalVCRepositoryUri;
import de.tum.cit.aet.artemis.programming.service.localvc.VcsAccessLogService;

@ExtendWith(MockitoExtension.class)
class RepositoryServiceTest {

    @Mock
    private GitService gitService;

    @Mock
    private VcsAccessLogService vcsAccessLogService;

    private RepositoryService repositoryService;

    private LocalVCRepositoryUri repositoryUri;

    @BeforeEach
    void setUp() {
        repositoryService = org.mockito.Mockito.spy(new RepositoryService(gitService, Optional.of(vcsAccessLogService)));
        repositoryUri = new LocalVCRepositoryUri(URI.create("http://localhost"), "TEST", "TEST-student");
    }

    @Test
    void getFilesContentFromBareRepositoryForLastCommitShouldFallbackToCheckedOutRepository() throws Exception {
        when(gitService.getBareRepository(repositoryUri, false)).thenThrow(new GitException("missing bare repo"));

        Repository checkedOutRepository = mock(Repository.class);
        when(gitService.getOrCheckoutRepository(repositoryUri, true, false)).thenReturn(checkedOutRepository);

        Map<String, String> expected = Map.of("Test.java", "class Test {}");
        doReturn(expected).when(repositoryService).getFilesContentFromBareRepositoryForLastCommit(checkedOutRepository);

        Map<String, String> files = repositoryService.getFilesContentFromBareRepositoryForLastCommit(repositoryUri);

        assertThat(files).isEqualTo(expected);
        verify(gitService).getOrCheckoutRepository(repositoryUri, true, false);
    }

    @Test
    void getFilesContentFromBareRepositoryForLastCommitBeforeOrAtShouldFallbackToCheckedOutRepository() throws Exception {
        ZonedDateTime deadline = ZonedDateTime.now();
        when(gitService.getBareRepository(repositoryUri, false)).thenThrow(new GitException("missing bare repo"));

        Repository checkedOutRepository = mock(Repository.class);
        when(gitService.getOrCheckoutRepository(repositoryUri, true, false)).thenReturn(checkedOutRepository);

        Map<String, String> expected = Map.of("Other.java", "class Other {}");
        doReturn(expected).when(repositoryService).getFilesContentFromBareRepositoryForLastCommitBeforeOrAt(checkedOutRepository, deadline);

        Map<String, String> files = repositoryService.getFilesContentFromBareRepositoryForLastCommitBeforeOrAt(repositoryUri, deadline);

        assertThat(files).isEqualTo(expected);
        verify(gitService).getOrCheckoutRepository(repositoryUri, true, false);
    }
}
