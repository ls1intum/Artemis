package de.tum.cit.aet.artemis.localvc.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.net.URI;
import java.nio.file.Path;
import java.util.Optional;

import org.eclipse.jgit.lib.Repository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.test.util.ReflectionTestUtils;

import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.admin.service.RateLimitService;
import de.tum.cit.aet.artemis.core.config.BuildAgentNetworkConfiguration;
import de.tum.cit.aet.artemis.core.config.BuildAgentNetworkPolicy;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.programming.repository.ParticipationVCSAccessTokenRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseBuildConfigRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;
import de.tum.cit.aet.artemis.programming.repository.RepositoryVCSAccessTokenRepository;
import de.tum.cit.aet.artemis.programming.service.AuxiliaryRepositoryService;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseParticipationService;
import de.tum.cit.aet.artemis.programming.service.RepositoryAccessService;

/**
 * The maximum size of the files of a repository depends on the kind of the repository: template and student repositories have the smaller limit, tests and solution
 * repositories the larger one, and a limit of zero disables the check.
 */
class LocalVCServletServiceRepositorySizeLimitTest {

    private static final long MEGABYTE = 1024 * 1024;

    private LocalVCRepositoryResolverService repositoryResolver;

    private LocalVCServletService service;

    @BeforeEach
    void setUp() {
        repositoryResolver = mock(LocalVCRepositoryResolverService.class);
        service = new LocalVCServletService(mock(AuthenticationManager.class), mock(UserRepository.class), mock(ProgrammingExerciseRepository.class),
                mock(ProgrammingExerciseBuildConfigRepository.class), mock(RepositoryAccessService.class), mock(ProgrammingExerciseParticipationService.class),
                mock(AuxiliaryRepositoryService.class), Optional.empty(), mock(ParticipationVCSAccessTokenRepository.class), mock(RepositoryVCSAccessTokenRepository.class),
                mock(AuthorizationCheckService.class), mock(RateLimitService.class), mock(UserVcsAccessTokenService.class), repositoryResolver,
                mock(LocalVCAccessLogRecorderService.class), mock(LocalVCBuildAgentAuthenticationService.class), mock(PasswordAuthenticationNoticeService.class),
                new BuildAgentNetworkPolicy(new BuildAgentNetworkConfiguration(), new MockEnvironment()));
        ReflectionTestUtils.setField(service, "maxRepositorySizeMb", 20L);
        ReflectionTestUtils.setField(service, "maxTestsAndSolutionRepositorySizeMb", 50L);
    }

    private Repository repositoryOfSlug(String slug) {
        Repository repository = mock(Repository.class);
        Path path = Path.of("/repos/ABC/" + slug + ".git");
        when(repository.getDirectory()).thenReturn(new File(path.toString()));
        when(repositoryResolver.parseRepositoryUri(path)).thenReturn(new LocalVCRepositoryUri(URI.create("https://artemis.example.com"), "ABC", slug));
        return repository;
    }

    @ParameterizedTest
    @CsvSource({ "abc-exercise, 20", "abc-ge12abc, 20", "abc-practice-ge12abc, 20", "abc-solution, 50", "abc-tests, 50" })
    void maxRepositorySize_dependsOnTheKindOfTheRepository(String slug, long expectedMegabytes) {
        assertThat(service.getMaxRepositorySizeBytes(repositoryOfSlug(slug))).isEqualTo(expectedMegabytes * MEGABYTE);
    }

    @Test
    void maxRepositorySize_isUnlimitedIfTheLimitIsZero() {
        ReflectionTestUtils.setField(service, "maxRepositorySizeMb", 0L);
        ReflectionTestUtils.setField(service, "maxTestsAndSolutionRepositorySizeMb", 0L);

        assertThat(service.getMaxRepositorySizeBytes(repositoryOfSlug("abc-ge12abc"))).isEqualTo(Long.MAX_VALUE);
        assertThat(service.getMaxRepositorySizeBytes(repositoryOfSlug("abc-tests"))).isEqualTo(Long.MAX_VALUE);
    }
}
