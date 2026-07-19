package de.tum.cit.aet.artemis.localvc.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.atomic.AtomicBoolean;

import jakarta.servlet.FilterChain;

import org.eclipse.jgit.http.server.ServletUtils;
import org.eclipse.jgit.lib.Repository;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import de.tum.cit.aet.artemis.core.exception.ConflictException;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseMutationGuard;

class LocalVCPushFilterTest {

    @Test
    void authorizedReceivePackHoldsMutationLeaseForCompleteFilterChain() throws Exception {
        LocalVCServletService localVCServletService = mock(LocalVCServletService.class);
        LocalVCPushFilter filter = new LocalVCPushFilter(localVCServletService);
        MockHttpServletRequest request = receivePackRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        ProgrammingExercise exercise = exercise();
        Repository repository = mock(Repository.class);
        AtomicBoolean released = new AtomicBoolean();
        var lease = new ProgrammingExerciseMutationGuard.MutationLease(() -> released.set(true));
        request.setAttribute(ServletUtils.ATTRIBUTE_REPOSITORY, repository);
        request.setAttribute(LocalVCServletService.AUTHORIZED_EXERCISE_ATTRIBUTE, exercise);
        when(localVCServletService.claimProgrammingExerciseMutation(repository, exercise)).thenReturn(lease);
        FilterChain filterChain = (_, _) -> assertThat(released).isFalse();

        filter.doFilterInternal(request, response, filterChain);

        assertThat(released).isTrue();
    }

    @Test
    void authorizedReceivePackReleasesMutationLeaseWhenFilterChainThrows() throws Exception {
        LocalVCServletService localVCServletService = mock(LocalVCServletService.class);
        LocalVCPushFilter filter = new LocalVCPushFilter(localVCServletService);
        MockHttpServletRequest request = receivePackRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        ProgrammingExercise exercise = exercise();
        Repository repository = mock(Repository.class);
        AtomicBoolean released = new AtomicBoolean();
        request.setAttribute(ServletUtils.ATTRIBUTE_REPOSITORY, repository);
        request.setAttribute(LocalVCServletService.AUTHORIZED_EXERCISE_ATTRIBUTE, exercise);
        when(localVCServletService.claimProgrammingExerciseMutation(repository, exercise)).thenReturn(new ProgrammingExerciseMutationGuard.MutationLease(() -> released.set(true)));
        FilterChain filterChain = (_, _) -> {
            throw new IllegalStateException("disconnect");
        };

        assertThatThrownBy(() -> filter.doFilterInternal(request, response, filterChain)).isInstanceOf(IllegalStateException.class).hasMessage("disconnect");
        assertThat(released).isTrue();
    }

    @Test
    void busyExerciseReturnsRetryableConflictWithoutEnteringReceivePack() throws Exception {
        LocalVCServletService localVCServletService = mock(LocalVCServletService.class);
        LocalVCPushFilter filter = new LocalVCPushFilter(localVCServletService);
        MockHttpServletRequest request = receivePackRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        ProgrammingExercise exercise = exercise();
        Repository repository = mock(Repository.class);
        FilterChain filterChain = mock(FilterChain.class);
        request.setAttribute(ServletUtils.ATTRIBUTE_REPOSITORY, repository);
        request.setAttribute(LocalVCServletService.AUTHORIZED_EXERCISE_ATTRIBUTE, exercise);
        when(localVCServletService.claimProgrammingExerciseMutation(repository, exercise))
                .thenThrow(new ConflictException("Exercise generation is running", "programmingExercise", "exerciseGenerationRunning"));

        filter.doFilterInternal(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(409);
        assertThat(response.getErrorMessage()).contains("retry");
        verify(filterChain, never()).doFilter(request, response);
    }

    private static MockHttpServletRequest receivePackRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest(HttpMethod.POST.name(), "/git/TEST/test-exercise.git/git-receive-pack");
        request.addHeader("Authorization", "Basic credentials");
        return request;
    }

    private static ProgrammingExercise exercise() {
        ProgrammingExercise exercise = new ProgrammingExercise();
        exercise.setId(1L);
        return exercise;
    }
}
