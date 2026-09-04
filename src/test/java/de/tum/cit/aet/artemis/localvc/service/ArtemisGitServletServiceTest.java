package de.tum.cit.aet.artemis.localvc.service;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.file.Path;

import jakarta.servlet.http.HttpServletRequest;

import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.transport.resolver.ReceivePackFactory;
import org.eclipse.jgit.transport.resolver.ServiceNotAuthorizedException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;

import de.tum.cit.aet.artemis.localvc.exception.LocalVCAuthException;

class ArtemisGitServletServiceTest {

    @TempDir
    private Path tempPath;

    @Test
    void authorizedPostWithoutFilterAuthenticationContextFailsClosed() throws Exception {
        LocalVCServletService localVCServletService = mock(LocalVCServletService.class);
        ArtemisGitServletService servletService = new ArtemisGitServletService(localVCServletService);
        servletService.init();
        Object gitFilter = ReflectionTestUtils.getField(servletService, "gitFilter");
        @SuppressWarnings("unchecked")
        ReceivePackFactory<HttpServletRequest> receivePackFactory = (ReceivePackFactory<HttpServletRequest>) ReflectionTestUtils.getField(gitFilter, "receivePackFactory");
        MockHttpServletRequest request = new MockHttpServletRequest(HttpMethod.POST.name(), "/git/TEST/test-exercise.git/git-receive-pack");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Basic credentials");
        when(localVCServletService.getUserByAuthHeader("Basic credentials")).thenThrow(new LocalVCAuthException("unknown user"));

        try (Repository repository = FileRepositoryBuilder.create(tempPath.resolve("test.git").toFile())) {
            repository.create(true);

            assertThatExceptionOfType(ServiceNotAuthorizedException.class).isThrownBy(() -> receivePackFactory.create(request, repository));
        }
    }
}
