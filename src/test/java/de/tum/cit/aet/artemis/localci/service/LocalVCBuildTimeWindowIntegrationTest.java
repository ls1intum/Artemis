package de.tum.cit.aet.artemis.localci.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;

import java.io.IOException;
import java.net.URISyntaxException;
import java.time.ZonedDateTime;
import java.util.Optional;

import javax.naming.InvalidNameException;
import javax.naming.ldap.LdapName;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.account.service.ldap.LdapUserDto;
import de.tum.cit.aet.artemis.localvc.util.LocalVCTestRepository;
import de.tum.cit.aet.artemis.programming.AbstractProgrammingIntegrationLocalCILocalVCTestBase;
import de.tum.cit.aet.artemis.programming.domain.BuildTimeWindow;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;

/**
 * Pushes to a student repository outside the optional build time window of the exercise are kept, but neither create a submission nor queue a build.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Execution(ExecutionMode.SAME_THREAD)
class LocalVCBuildTimeWindowIntegrationTest extends AbstractProgrammingIntegrationLocalCILocalVCTestBase {

    private static final String TEST_PREFIX = "localvcbuildwindow";

    private LocalVCTestRepository assignmentRepository;

    @Override
    protected String getTestPrefix() {
        return TEST_PREFIX;
    }

    @BeforeAll
    void setupAll() {
        CredentialsProvider.setDefault(new UsernamePasswordCredentialsProvider(localVCUsername, localVCPassword));
    }

    @BeforeEach
    void initRepositories() throws GitAPIException, IOException, URISyntaxException, InvalidNameException {
        assignmentRepository = localVCLocalCITestService.createRepositoryWithWorkingCopy(projectKey1, assignmentRepositorySlug);

        for (String login : new String[] { TEST_PREFIX + "instructor1", TEST_PREFIX + "student1" }) {
            var ldapUser = new LdapUserDto().login(login);
            ldapUser.setUid(new LdapName("cn=" + login.substring(TEST_PREFIX.length()) + ",ou=test,o=lab"));
            doReturn(Optional.of(ldapUser)).when(ldapUserService).findByLogin(login);
        }
        var fakeUser = new LdapUserDto().login(localVCBaseUsername);
        fakeUser.setUid(new LdapName("cn=" + localVCBaseUsername + ",ou=test,o=lab"));
        doReturn(Optional.of(fakeUser)).when(ldapUserService).findByLogin(localVCBaseUsername);
        doReturn(true).when(ldapTemplate).compare(anyString(), anyString(), any());

        dockerClientTestService.mockInspectImage(dockerClient);
    }

    @AfterEach
    void removeRepository() throws IOException {
        // Guarded, because a failure inside initRepositories leaves the field unassigned and the resulting NullPointerException would replace the real cause in the report.
        if (assignmentRepository != null) {
            assignmentRepository.deleteWorkingCopy();
        }
    }

    private void setBuildTimeWindow(ZonedDateTime start, ZonedDateTime end) {
        var buildConfig = programmingExerciseBuildConfigRepository.findByProgrammingExerciseId(programmingExercise.getId()).orElseThrow();
        buildConfig.setBuildTimeWindow(new BuildTimeWindow(start, end));
        programmingExerciseBuildConfigRepository.save(buildConfig);
    }

    /**
     * Commits a file in the student's working copy, pushes it as the given user and reports whether a submission was created for the commit.
     */
    private PushOutcome commitAndPushAs(String pusherLogin) throws Exception {
        ProgrammingExerciseStudentParticipation participation = localVCLocalCITestService.createParticipation(programmingExercise, student1Login);
        String commit = localVCLocalCITestService.commitFile(assignmentRepository.workingCopyPath(), assignmentRepository.workingCopy());
        String repositoryUri = localVCLocalCITestService.buildLocalVCUri(pusherLogin, projectKey1, assignmentRepositorySlug);

        PushResult pushResult = assignmentRepository.workingCopy().push().setRemote(repositoryUri).setCredentialsProvider(LocalVCLocalCITestService.ONLY_THE_CREDENTIALS_IN_THE_URI)
                .call().iterator().next();
        return new PushOutcome(pushResult, participation.getId(), commit);
    }

    private record PushOutcome(PushResult pushResult, long participationId, String commit) {
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void pushBeforeTheWindowIsKeptButNotBuilt() throws Exception {
        setBuildTimeWindow(ZonedDateTime.now().plusHours(1), ZonedDateTime.now().plusHours(2));

        PushOutcome outcome = commitAndPushAs(student1Login);

        assertThat(outcome.pushResult().getRemoteUpdates()).allSatisfy(update -> assertThat(update.getStatus().name()).isEqualTo("OK"));
        assertThat(outcome.pushResult().getMessages()).contains("not built or graded").contains("builds run from");
        assertThat(programmingSubmissionRepository.existsByParticipationIdAndCommitHash(outcome.participationId(), outcome.commit())).isFalse();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void pushAfterTheWindowIsKeptButNotBuilt() throws Exception {
        setBuildTimeWindow(ZonedDateTime.now().minusHours(2), ZonedDateTime.now().minusHours(1));

        PushOutcome outcome = commitAndPushAs(student1Login);

        assertThat(outcome.pushResult().getMessages()).contains("not built or graded");
        assertThat(programmingSubmissionRepository.existsByParticipationIdAndCommitHash(outcome.participationId(), outcome.commit())).isFalse();
    }

    /**
     * Lets the window end before the due date of the exercise and gives the student of the test an individual due date, so that the extension is the time between both due
     * dates. Both due dates lie in the future, as students cannot push after their due date at all.
     */
    private void setWindowWithExtension(ZonedDateTime windowEnd, ZonedDateTime dueDate, ZonedDateTime individualDueDate) {
        setBuildTimeWindow(windowEnd.minusDays(1), windowEnd);
        programmingExercise.setDueDate(dueDate);
        programmingExerciseRepository.save(programmingExercise);
        var participation = localVCLocalCITestService.createParticipation(programmingExercise, student1Login);
        participation.setIndividualDueDate(individualDueDate);
        programmingExerciseStudentParticipationRepository.save(participation);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void pushAfterTheWindowIsBuiltWhileTheEndExtendedByTheIndividualDueDateLasts() throws Exception {
        // without the extension of 2 hours the window would have ended an hour ago, with it, it lasts for another hour
        setWindowWithExtension(ZonedDateTime.now().minusHours(1), ZonedDateTime.now().plusHours(1), ZonedDateTime.now().plusHours(3));

        PushOutcome outcome = commitAndPushAs(student1Login);

        assertThat(outcome.pushResult().getMessages()).doesNotContain("not built or graded");
        await().until(() -> programmingSubmissionRepository.existsByParticipationIdAndCommitHash(outcome.participationId(), outcome.commit()));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void pushAfterTheExtendedEndOfTheWindowIsKeptButNotBuilt() throws Exception {
        // the extension of an hour moves the end of the window to an hour ago
        setWindowWithExtension(ZonedDateTime.now().minusHours(2), ZonedDateTime.now().plusHours(1), ZonedDateTime.now().plusHours(2));

        PushOutcome outcome = commitAndPushAs(student1Login);

        assertThat(outcome.pushResult().getMessages()).contains("not built or graded");
        assertThat(programmingSubmissionRepository.existsByParticipationIdAndCommitHash(outcome.participationId(), outcome.commit())).isFalse();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void aStudentWithAnExtensionCanTriggerABuildAfterTheWindow() throws Exception {
        setWindowWithExtension(ZonedDateTime.now().minusHours(1), ZonedDateTime.now().plusHours(1), ZonedDateTime.now().plusHours(3));
        var participation = localVCLocalCITestService.createParticipation(programmingExercise, student1Login);

        request.postWithoutResponseBody("/api/programming/programming-submissions/" + participation.getId() + "/trigger-build", null, HttpStatus.OK);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void pushOutsideTheWindowStaysInTheAccessLogWithItsCommitHash() throws Exception {
        setBuildTimeWindow(ZonedDateTime.now().plusHours(1), ZonedDateTime.now().plusHours(2));

        PushOutcome outcome = commitAndPushAs(student1Login);

        assertThat(outcome.pushResult().getMessages()).contains("not built or graded");
        // the entry is finished after the hook answered, so wait for it
        await().untilAsserted(() -> assertThat(vcsAccessLogRepository.findNewestUserEntryByParticipationId(outcome.participationId())).isPresent().get()
                .satisfies(accessLog -> assertThat(accessLog.getCommitHash()).isEqualTo(outcome.commit())));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void pushBeforeAWindowWithOnlyAStartMentionsTheStart() throws Exception {
        setBuildTimeWindow(ZonedDateTime.now().plusHours(1), null);

        PushOutcome outcome = commitAndPushAs(student1Login);

        assertThat(outcome.pushResult().getMessages()).contains("builds start on");
        assertThat(programmingSubmissionRepository.existsByParticipationIdAndCommitHash(outcome.participationId(), outcome.commit())).isFalse();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void pushInsideTheWindowIsBuilt() throws Exception {
        setBuildTimeWindow(ZonedDateTime.now().minusHours(1), ZonedDateTime.now().plusHours(1));

        PushOutcome outcome = commitAndPushAs(student1Login);

        assertThat(outcome.pushResult().getMessages()).doesNotContain("not built or graded");
        await().until(() -> programmingSubmissionRepository.existsByParticipationIdAndCommitHash(outcome.participationId(), outcome.commit()));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void pushWithoutAWindowIsBuilt() throws Exception {
        PushOutcome outcome = commitAndPushAs(student1Login);

        await().until(() -> programmingSubmissionRepository.existsByParticipationIdAndCommitHash(outcome.participationId(), outcome.commit()));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void aStudentCannotTriggerABuildOutsideTheWindow() throws Exception {
        var participation = localVCLocalCITestService.createParticipation(programmingExercise, student1Login);
        setBuildTimeWindow(ZonedDateTime.now().plusHours(1), ZonedDateTime.now().plusHours(2));

        request.postWithoutResponseBody("/api/programming/programming-submissions/" + participation.getId() + "/trigger-build", null, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void aStudentCannotTriggerAFailedBuildOutsideTheWindow() throws Exception {
        // the retry builds the latest commit, which may have been pushed outside the window
        var participation = localVCLocalCITestService.createParticipation(programmingExercise, student1Login);
        setBuildTimeWindow(ZonedDateTime.now().minusHours(2), ZonedDateTime.now().minusHours(1));

        request.postWithoutLocation("/api/programming/participations/" + participation.getId() + "/trigger-failed-build", null, HttpStatus.BAD_REQUEST, null);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void aStudentCanTriggerABuildInsideTheWindow() throws Exception {
        var participation = localVCLocalCITestService.createParticipation(programmingExercise, student1Login);
        setBuildTimeWindow(ZonedDateTime.now().minusHours(1), ZonedDateTime.now().plusHours(1));

        request.postWithoutResponseBody("/api/programming/programming-submissions/" + participation.getId() + "/trigger-build", null, HttpStatus.OK);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void instructorPushOutsideTheWindowIsStillBuilt() throws Exception {
        setBuildTimeWindow(ZonedDateTime.now().plusHours(1), ZonedDateTime.now().plusHours(2));

        PushOutcome outcome = commitAndPushAs(instructor1Login);

        assertThat(outcome.pushResult().getMessages()).doesNotContain("not built or graded");
        await().until(() -> programmingSubmissionRepository.existsByParticipationIdAndCommitHash(outcome.participationId(), outcome.commit()));
    }
}
