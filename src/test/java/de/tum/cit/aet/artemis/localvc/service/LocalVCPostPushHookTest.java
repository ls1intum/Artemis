package de.tum.cit.aet.artemis.localvc.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import org.apache.sshd.server.session.ServerSession;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.eclipse.jgit.transport.ReceivePack;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.localvc.service.ssh.SshConstants;
import de.tum.cit.aet.artemis.programming.domain.AuthenticationMechanism;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.exception.VersionControlException;

class LocalVCPostPushHookTest {

    private static final ObjectId OLD_ID = ObjectId.fromString("89abcdef0123456789abcdef0123456789abcdef");

    private static final ObjectId NEW_ID = ObjectId.fromString("0123456789abcdef0123456789abcdef01234567");

    private final LocalVCServletService localVCServletService = mock(LocalVCServletService.class);

    private final LocalVCPushProcessingService pushProcessingService = mock(LocalVCPushProcessingService.class);

    private final PasswordAuthenticationNoticeService noticeService = mock(PasswordAuthenticationNoticeService.class);

    private final ReceivePack receivePack = mock(ReceivePack.class);

    private LocalVCPostPushHook httpsHook(User user, AuthenticationMechanism mechanism, Consumer<String> pushedCommitRecorder) {
        return new LocalVCPostPushHook(localVCServletService, pushProcessingService, noticeService, user, () -> mechanism, pushedCommitRecorder);
    }

    @Test
    void aPushWithAPasswordPrintsTheNotice() {
        when(noticeService.getPasswordAuthenticationPushNotice()).thenReturn("please switch");

        // A push that updates nothing returns early, and must still tell the user.
        httpsHook(new User(), AuthenticationMechanism.PASSWORD, commitHash -> {
        }).onPostReceive(receivePack, List.of());

        verify(receivePack).sendMessage("please switch");
    }

    @Test
    void aPushWithATokenPrintsNothing() {
        httpsHook(new User(), AuthenticationMechanism.USER_VCS_ACCESS_TOKEN, commitHash -> {
        }).onPostReceive(receivePack, List.of());

        verify(receivePack, never()).sendMessage(anyString());
    }

    @Test
    void readsTheMechanismOnlyOnceTheRequestWasAuthorized() {
        when(noticeService.getPasswordAuthenticationPushNotice()).thenReturn("please switch");
        AtomicReference<AuthenticationMechanism> mechanism = new AtomicReference<>();
        // JGit builds the hook before the push filter has recorded the mechanism on the request.
        LocalVCPostPushHook hook = new LocalVCPostPushHook(localVCServletService, pushProcessingService, noticeService, new User(), mechanism::get, commitHash -> {
        });
        mechanism.set(AuthenticationMechanism.PASSWORD);

        hook.onPostReceive(receivePack, List.of());

        verify(receivePack).sendMessage("please switch");
    }

    @Test
    void aPushThatUpdatesNothingRecordsNoCommit() {
        List<String> recorded = new ArrayList<>();

        httpsHook(new User(), AuthenticationMechanism.USER_VCS_ACCESS_TOKEN, recorded::add).onPostReceive(receivePack, List.of());

        assertThat(recorded).isEmpty();
    }

    @Test
    void anAcceptedUpdateOverHttpsIsHandedToThePushProcessingWithoutCachedState() {
        ReceiveCommand update = new ReceiveCommand(OLD_ID, NEW_ID, "refs/heads/main");
        Repository repository = mock(Repository.class);
        when(receivePack.getRepository()).thenReturn(repository);
        User user = new User();

        httpsHook(user, AuthenticationMechanism.USER_VCS_ACCESS_TOKEN, commitHash -> {
        }).onPostReceive(receivePack, List.of(update));

        verify(pushProcessingService).processNewPush(NEW_ID.name(), repository, user, Optional.empty(), Optional.empty());
    }

    @Test
    void anAcceptedUpdateOverSshIsHandedToThePushProcessingWithTheStateOfTheSession() {
        ReceiveCommand update = new ReceiveCommand(OLD_ID, NEW_ID, "refs/heads/main");
        Repository repository = mock(Repository.class);
        when(receivePack.getRepository()).thenReturn(repository);
        User user = new User();
        var participation = new ProgrammingExerciseStudentParticipation();
        var exercise = new ProgrammingExercise();
        ServerSession session = sessionWith(participation, exercise);

        new LocalVCPostPushHook(localVCServletService, pushProcessingService, noticeService, session, user, new GitOperationAudit()).onPostReceive(receivePack, List.of(update));

        verify(pushProcessingService).processNewPush(NEW_ID.name(), repository, user, Optional.of(exercise), Optional.of(participation));
    }

    @Test
    void theCommitOfAnAcceptedPushIsRecordedBeforeThePushIsProcessed() {
        ReceiveCommand update = new ReceiveCommand(OLD_ID, NEW_ID, "refs/heads/main");
        Repository repository = mock(Repository.class);
        when(receivePack.getRepository()).thenReturn(repository);
        User user = new User();
        @SuppressWarnings("unchecked")
        Consumer<String> recorder = mock(Consumer.class);

        httpsHook(user, AuthenticationMechanism.USER_VCS_ACCESS_TOKEN, recorder).onPostReceive(receivePack, List.of(update));

        InOrder inOrder = inOrder(recorder, pushProcessingService);
        inOrder.verify(recorder).accept(NEW_ID.name());
        inOrder.verify(pushProcessingService).processNewPush(NEW_ID.name(), repository, user, Optional.empty(), Optional.empty());
    }

    @Test
    void aPushThatCreatesABranchIsRecordedAlthoughItIsNoSubmission() {
        ReceiveCommand create = new ReceiveCommand(ObjectId.zeroId(), NEW_ID, "refs/heads/feature");
        List<String> recorded = new ArrayList<>();

        httpsHook(new User(), AuthenticationMechanism.USER_VCS_ACCESS_TOKEN, recorded::add).onPostReceive(receivePack, List.of(create));

        // It is part of the repository, so it is part of the access log, and the push processing never hears of it.
        assertThat(recorded).containsExactly(NEW_ID.name());
        verify(receivePack).sendError(anyString());
        verify(pushProcessingService, never()).processNewPush(anyString(), any(), any(), any(), any());
    }

    @Test
    void aPushThatTheProcessingRejectsIsStillRecorded() {
        ReceiveCommand update = new ReceiveCommand(OLD_ID, NEW_ID, "refs/heads/other");
        Repository repository = mock(Repository.class);
        when(receivePack.getRepository()).thenReturn(repository);
        when(localVCServletService.isBranchingAllowedForRepository(repository)).thenReturn(true);
        doThrow(new VersionControlException("not the default branch")).when(pushProcessingService).processNewPush(anyString(), any(), any(), any(), any());
        List<String> recorded = new ArrayList<>();

        httpsHook(new User(), AuthenticationMechanism.USER_VCS_ACCESS_TOKEN, recorded::add).onPostReceive(receivePack, List.of(update));

        assertThat(recorded).containsExactly(NEW_ID.name());
    }

    @Test
    void theCommitOfAPushOverSshIsHandedToTheAuditOfTheCommand() {
        ReceiveCommand update = new ReceiveCommand(OLD_ID, NEW_ID, "refs/heads/main");
        when(receivePack.getRepository()).thenReturn(mock(Repository.class));
        ServerSession session = sessionWith(new ProgrammingExerciseStudentParticipation(), new ProgrammingExercise());
        var audit = new GitOperationAudit();

        new LocalVCPostPushHook(localVCServletService, pushProcessingService, noticeService, session, new User(), audit).onPostReceive(receivePack, List.of(update));

        assertThat(audit.getPushedCommitHash()).isEqualTo(NEW_ID.name());
    }

    private ServerSession sessionWith(ProgrammingExerciseStudentParticipation participation, ProgrammingExercise exercise) {
        ServerSession session = mock(ServerSession.class);
        when(session.getAttribute(SshConstants.PARTICIPATION_KEY)).thenReturn(participation);
        when(session.getAttribute(SshConstants.REPOSITORY_EXERCISE_KEY)).thenReturn(exercise);
        return session;
    }
}
