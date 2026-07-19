package de.tum.cit.aet.artemis.localvc.service.ssh;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.sshd.git.GitLocationResolver;
import org.apache.sshd.server.ExitCallback;
import org.apache.sshd.server.session.ServerSession;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.core.exception.ConflictException;
import de.tum.cit.aet.artemis.localvc.service.LocalVCServletService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseMutationGuard;

class SshGitCommandTest {

    @TempDir
    private Path tempPath;

    @Test
    void receivePackHoldsMutationLeaseUntilInputFailureAndThenReleasesIt() throws Exception {
        Path repositoryPath = tempPath.resolve("exercise.git");
        try (Repository repository = FileRepositoryBuilder.create(repositoryPath.toFile())) {
            repository.create(true);
        }
        LocalVCServletService localVCServletService = mock(LocalVCServletService.class);
        ProgrammingExercise exercise = new ProgrammingExercise();
        exercise.setId(1L);
        User user = new User();
        ServerSession session = mock(ServerSession.class);
        AtomicBoolean released = new AtomicBoolean();
        var lease = new ProgrammingExerciseMutationGuard.MutationLease(() -> released.set(true));
        GitLocationResolver resolver = (_, _, _, _) -> repositoryPath;
        SshGitCommand command = new SshGitCommand(resolver, "git-receive-pack 'exercise.git'", null, localVCServletService);
        when(session.getAttribute(SshConstants.USER_KEY)).thenReturn(user);
        when(session.getAttribute(SshConstants.REPOSITORY_EXERCISE_KEY)).thenReturn(exercise);
        when(localVCServletService.claimProgrammingExerciseMutation(org.mockito.ArgumentMatchers.any(Repository.class), org.mockito.ArgumentMatchers.same(exercise)))
                .thenReturn(lease);
        command.setSession(session);
        command.setOutputStream(new ByteArrayOutputStream());
        command.setErrorStream(new ByteArrayOutputStream());
        command.setExitCallback(mock(ExitCallback.class));
        command.setInputStream(new InputStream() {

            @Override
            public int read() throws IOException {
                assertThat(released).isFalse();
                throw new IOException("disconnect");
            }
        });

        command.run();

        assertThat(released).isTrue();
        verify(localVCServletService).claimProgrammingExerciseMutation(org.mockito.ArgumentMatchers.any(Repository.class), org.mockito.ArgumentMatchers.same(exercise));
    }

    @Test
    void receivePackReportsRetryableMutationConflict() throws Exception {
        Path repositoryPath = tempPath.resolve("conflict.git");
        try (Repository repository = FileRepositoryBuilder.create(repositoryPath.toFile())) {
            repository.create(true);
        }
        LocalVCServletService localVCServletService = mock(LocalVCServletService.class);
        ProgrammingExercise exercise = new ProgrammingExercise();
        User user = new User();
        ServerSession session = mock(ServerSession.class);
        ExitCallback exitCallback = mock(ExitCallback.class);
        ByteArrayOutputStream error = new ByteArrayOutputStream();
        SshGitCommand command = new SshGitCommand((_, _, _, _) -> repositoryPath, "git-receive-pack 'conflict.git'", null, localVCServletService);
        when(session.getAttribute(SshConstants.USER_KEY)).thenReturn(user);
        when(session.getAttribute(SshConstants.REPOSITORY_EXERCISE_KEY)).thenReturn(exercise);
        when(localVCServletService.claimProgrammingExerciseMutation(org.mockito.ArgumentMatchers.any(Repository.class), org.mockito.ArgumentMatchers.same(exercise)))
                .thenThrow(new ConflictException("Exercise generation is running", "programmingExercise", "exerciseGenerationRunning"));
        command.setSession(session);
        command.setInputStream(InputStream.nullInputStream());
        command.setOutputStream(new ByteArrayOutputStream());
        command.setErrorStream(error);
        command.setExitCallback(exitCallback);

        command.run();

        String message = "Push rejected: Exercise generation is running Please retry the push later.";
        assertThat(error.toString()).contains(message);
        verify(exitCallback).onExit(-1, message);
    }
}
