package de.tum.cit.aet.artemis.programming.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import org.eclipse.jgit.api.errors.CanceledException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import de.tum.cit.aet.artemis.account.test_repository.UserTestRepository;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.assessment.test_repository.ResultTestRepository;
import de.tum.cit.aet.artemis.exercise.domain.Submission;
import de.tum.cit.aet.artemis.exercise.repository.TeamRepository;
import de.tum.cit.aet.artemis.exercise.test_repository.ParticipationTestRepository;
import de.tum.cit.aet.artemis.exercise.test_repository.SubmissionTestRepository;
import de.tum.cit.aet.artemis.localvc.service.GitService;
import de.tum.cit.aet.artemis.localvc.service.LocalVCRepositoryUri;
import de.tum.cit.aet.artemis.localvc.service.vcs.VersionControlService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingSubmission;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;
import de.tum.cit.aet.artemis.programming.domain.SolutionProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.TemplateProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.repository.SolutionProgrammingExerciseParticipationRepository;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingExerciseStudentParticipationTestRepository;
import de.tum.cit.aet.artemis.programming.test_repository.TemplateProgrammingExerciseParticipationTestRepository;

/**
 * Unit tests for finding the participation a repository or a build plan belongs to.
 * <p>
 * These lookups are what the LocalVC server and the CI result handler use to decide whose work a push or a build result
 * belongs to, and the routing is not obvious: a push to the test repository is recorded against the solution
 * participation, and the URIs arrive with the suffix git appends when it negotiates a transfer. Routing to the wrong
 * participation attributes one student's push or result to another, so each branch is pinned to the repository it is
 * supposed to read.
 */
@ExtendWith(MockitoExtension.class)
class ProgrammingExerciseParticipationServiceTest {

    private static final long EXERCISE_ID = 7L;

    private static final String REPOSITORY_URI = "https://artemis.example.com/git/ABC/abc-student.git";

    @Mock
    private SolutionProgrammingExerciseParticipationRepository solutionParticipationRepository;

    @Mock
    private TemplateProgrammingExerciseParticipationTestRepository templateParticipationRepository;

    @Mock
    private ProgrammingExerciseStudentParticipationTestRepository studentParticipationRepository;

    @Mock
    private ParticipationTestRepository participationRepository;

    @Mock
    private TeamRepository teamRepository;

    @Mock
    private GitService gitService;

    @Mock
    private VersionControlService versionControlService;

    @Mock
    private ResultTestRepository resultRepository;

    @Mock
    private SubmissionTestRepository submissionRepository;

    @Mock
    private UserTestRepository userRepository;

    private ProgrammingExerciseParticipationService participationService;

    private ProgrammingExercise exercise;

    @BeforeEach
    void setUp() {
        participationService = new ProgrammingExerciseParticipationService(solutionParticipationRepository, templateParticipationRepository, studentParticipationRepository,
                participationRepository, teamRepository, gitService, Optional.of(versionControlService), resultRepository, submissionRepository, userRepository);
        exercise = new ProgrammingExercise();
        exercise.setId(EXERCISE_ID);
    }

    @Test
    void fetchParticipationByRepository_forAPushToTheTestRepository_readsTheSolutionParticipation() {
        // The tests belong to the exercise rather than to a participation of their own, so a push to them is recorded
        // against the solution participation, which is what gets rebuilt when the tests change.
        var solutionParticipation = new SolutionProgrammingExerciseParticipation();
        when(solutionParticipationRepository.findWithEagerResultsAndSubmissionsByProgrammingExerciseIdElseThrow(EXERCISE_ID)).thenReturn(solutionParticipation);

        assertThat(participationService.fetchParticipationByRepository(RepositoryType.TESTS.toString(), REPOSITORY_URI, exercise)).isSameAs(solutionParticipation);
    }

    @Test
    void fetchParticipationByRepository_forTheSolutionRepository_readsTheSolutionParticipation() {
        var solutionParticipation = new SolutionProgrammingExerciseParticipation();
        when(solutionParticipationRepository.findWithEagerResultsAndSubmissionsByProgrammingExerciseIdElseThrow(EXERCISE_ID)).thenReturn(solutionParticipation);

        assertThat(participationService.fetchParticipationByRepository(RepositoryType.SOLUTION.toString(), REPOSITORY_URI, exercise)).isSameAs(solutionParticipation);
    }

    @Test
    void fetchParticipationByRepository_forTheTemplateRepository_readsItByItsUri() {
        var templateParticipation = new TemplateProgrammingExerciseParticipation();
        when(templateParticipationRepository.findByRepositoryUriElseThrow(REPOSITORY_URI)).thenReturn(templateParticipation);

        assertThat(participationService.fetchParticipationByRepository(RepositoryType.TEMPLATE.toString(), REPOSITORY_URI, exercise)).isSameAs(templateParticipation);
    }

    @Test
    void fetchParticipationByRepository_forAStudentRepository_readsThatStudentsParticipation() {
        var studentParticipation = new ProgrammingExerciseStudentParticipation();
        when(studentParticipationRepository.findByRepositoryUriElseThrow(REPOSITORY_URI)).thenReturn(studentParticipation);

        assertThat(participationService.fetchParticipationByRepository("ge12abc", REPOSITORY_URI, exercise)).isSameAs(studentParticipation);
    }

    @Test
    void fetchParticipationByRepository_stripsTheSuffixGitAppendsWhenItNegotiatesATransfer() {
        // The URI arrives straight from the git request, where it ends in the service being called; looking that up verbatim
        // would find no participation at all.
        var studentParticipation = new ProgrammingExerciseStudentParticipation();
        when(studentParticipationRepository.findByRepositoryUriElseThrow(REPOSITORY_URI)).thenReturn(studentParticipation);

        assertThat(participationService.fetchParticipationByRepository("ge12abc", REPOSITORY_URI + "/git-upload-pack", exercise)).isSameAs(studentParticipation);
        assertThat(participationService.fetchParticipationByRepository("ge12abc", REPOSITORY_URI + "/git-receive-pack", exercise)).isSameAs(studentParticipation);
    }

    @Test
    void fetchParticipationWithSubmissionsByRepository_routesTheSameWayButLoadsTheSubmissions() {
        var templateParticipation = new TemplateProgrammingExerciseParticipation();
        when(templateParticipationRepository.findWithSubmissionsByRepositoryUriElseThrow(REPOSITORY_URI)).thenReturn(templateParticipation);

        assertThat(participationService.fetchParticipationWithSubmissionsByRepository(RepositoryType.TEMPLATE.toString(), REPOSITORY_URI + "/git-upload-pack", exercise))
                .isSameAs(templateParticipation);
    }

    @Test
    void getCommitInfos_whenTheRepositoryCannotBeRead_reportsNoCommitsRatherThanFailing() throws Exception {
        // The commit list is shown next to a participation; failing to read it must not take down the page around it.
        var uri = new LocalVCRepositoryUri(java.net.URI.create("https://artemis.example.com"), "ABC", "abc-student");
        when(gitService.getCommitInfos(uri)).thenThrow(new CanceledException("the repository is locked"));

        assertThat(participationService.getCommitInfos(uri)).isEmpty();
    }

    private static ProgrammingExerciseStudentParticipation participationInitializedAt(long id, ZonedDateTime initializationDate) {
        var participation = new ProgrammingExerciseStudentParticipation();
        participation.setId(id);
        participation.setInitializationDate(initializationDate);
        return participation;
    }

    @Test
    void getParticipationWithResults_forATemplateBuildPlan_readsTheTemplateParticipation() {
        var templateParticipation = new TemplateProgrammingExerciseParticipation();
        when(templateParticipationRepository.findByBuildPlanIdWithResults("ABC-BASE")).thenReturn(Optional.of(templateParticipation));

        assertThat(participationService.getParticipationWithResults("ABC-BASE")).isSameAs(templateParticipation);
    }

    @Test
    void getParticipationWithResults_forASolutionBuildPlan_readsTheSolutionParticipation() {
        var solutionParticipation = new SolutionProgrammingExerciseParticipation();
        when(solutionParticipationRepository.findByBuildPlanIdWithResults("ABC-SOLUTION")).thenReturn(Optional.of(solutionParticipation));

        assertThat(participationService.getParticipationWithResults("ABC-SOLUTION")).isSameAs(solutionParticipation);
    }

    @Test
    void getParticipationWithResults_forABuildPlanNobodyOwns_returnsNothing() {
        when(studentParticipationRepository.findWithResultsAndExerciseAndTeamStudentsByBuildPlanId("ABC-GE12ABC")).thenReturn(List.of());

        assertThat(participationService.getParticipationWithResults("ABC-GE12ABC")).isNull();
    }

    @Test
    void getParticipationWithResults_whenAStudentHasSeveralParticipationsForOnePlan_takesTheMostRecentOne() {
        // A repeated participation reuses the build plan key, and the result of a build belongs to the participation that
        // is currently in use, not to the one that was abandoned.
        var older = participationInitializedAt(1L, ZonedDateTime.now().minusDays(10));
        var newest = participationInitializedAt(2L, ZonedDateTime.now().minusDays(1));
        var middle = participationInitializedAt(3L, ZonedDateTime.now().minusDays(5));
        when(studentParticipationRepository.findWithResultsAndExerciseAndTeamStudentsByBuildPlanId("ABC-GE12ABC")).thenReturn(List.of(older, newest, middle));

        assertThat(participationService.getParticipationWithResults("ABC-GE12ABC")).isSameAs(newest);
    }

    @Test
    void findStudentParticipationWithLatestSubmission_forAParticipationWithoutASubmission_returnsItWithNoSubmissions() {
        var participation = participationInitializedAt(1L, ZonedDateTime.now());
        when(studentParticipationRepository.findByIdElseThrow(1L)).thenReturn(participation);
        when(submissionRepository.findLatestSubmissionByParticipationId(1L)).thenReturn(Optional.empty());

        var result = participationService.findStudentParticipationWithLatestSubmissionResultAndFeedbacksElseThrow(1L);

        assertThat(result.getSubmissions()).isEmpty();
    }

    @Test
    void findStudentParticipationWithLatestSubmission_attachesTheLatestResultToTheLatestSubmission() {
        var participation = participationInitializedAt(1L, ZonedDateTime.now());
        Submission submission = new ProgrammingSubmission();
        submission.setId(50L);
        Result result = new Result();
        result.setId(90L);
        when(studentParticipationRepository.findByIdElseThrow(1L)).thenReturn(participation);
        when(submissionRepository.findLatestSubmissionByParticipationId(1L)).thenReturn(Optional.of(submission));
        when(resultRepository.findLatestResultWithFeedbacksBySubmissionId(eqId(50L), any())).thenReturn(Optional.of(result));

        var loaded = participationService.findStudentParticipationWithLatestSubmissionResultAndFeedbacksElseThrow(1L);

        assertThat(loaded.getSubmissions()).containsExactly(submission);
        assertThat(submission.getResults()).containsExactly(result);
    }

    @Test
    void findStudentParticipationWithLatestSubmission_forASubmissionThatHasNoResultYet_leavesTheResultsEmpty() {
        // A submission whose build is still running has no result, and a null there would break every caller that iterates it.
        var participation = participationInitializedAt(1L, ZonedDateTime.now());
        Submission submission = new ProgrammingSubmission();
        submission.setId(50L);
        when(studentParticipationRepository.findByIdElseThrow(1L)).thenReturn(participation);
        when(submissionRepository.findLatestSubmissionByParticipationId(1L)).thenReturn(Optional.of(submission));
        when(resultRepository.findLatestResultWithFeedbacksBySubmissionId(eqId(50L), any())).thenReturn(Optional.empty());

        var loaded = participationService.findStudentParticipationWithLatestSubmissionResultAndFeedbacksElseThrow(1L);

        assertThat(loaded.getSubmissions()).containsExactly(submission);
        assertThat(submission.getResults()).isEmpty();
    }

    @Test
    void retrieveSolutionParticipation_readsTheSolutionParticipationOfTheExercise() {
        var solutionParticipation = new SolutionProgrammingExerciseParticipation();
        when(solutionParticipationRepository.findByProgrammingExerciseIdElseThrow(EXERCISE_ID)).thenReturn(solutionParticipation);

        assertThat(participationService.retrieveSolutionParticipation(exercise)).isSameAs(solutionParticipation);
        verify(solutionParticipationRepository).findByProgrammingExerciseIdElseThrow(EXERCISE_ID);
    }

    /**
     * The submission id is a primitive on this query, so it needs a matcher of the same primitive type.
     */
    private static long eqId(long id) {
        return org.mockito.ArgumentMatchers.eq(id);
    }

    // --- initial participations ------------------------------------------------------------------------------------

    private ProgrammingExercise newExercise() {
        var course = new de.tum.cit.aet.artemis.course.domain.Course();
        course.setShortName("course1");
        var newExercise = new ProgrammingExercise();
        newExercise.setCourse(course);
        newExercise.setShortName("exercise1");
        newExercise.generateAndSetProjectKey();
        return newExercise;
    }

    @Test
    void setupInitialSolutionParticipation_pointsTheParticipationAtTheSolutionRepositoryOfTheNewExercise() throws Exception {
        var newExercise = newExercise();
        when(versionControlService.getCloneRepositoryUri(newExercise.getProjectKey(), newExercise.generateRepositoryName(RepositoryType.SOLUTION)))
                .thenReturn(new LocalVCRepositoryUri("https://artemis.example.com/git/COURSE1EXERCISE1/course1exercise1-solution.git"));

        participationService.setupInitialSolutionParticipation(newExercise);

        var solutionParticipation = newExercise.getSolutionParticipation();
        // A participation pointed at the wrong repository builds somebody else's code and reports the result as this exercise's.
        assertThat(solutionParticipation.getRepositoryUri()).endsWith("course1exercise1-solution.git");
        assertThat(solutionParticipation.getBuildPlanId()).isEqualTo(newExercise.generateBuildPlanId(de.tum.cit.aet.artemis.programming.domain.build.BuildPlanType.SOLUTION));
        assertThat(solutionParticipation.getInitializationState()).isEqualTo(de.tum.cit.aet.artemis.exercise.domain.InitializationState.INITIALIZED);
        assertThat(solutionParticipation.getProgrammingExercise()).isSameAs(newExercise);
        verify(solutionParticipationRepository).save(solutionParticipation);
    }

    @Test
    void setupInitialTemplateParticipation_pointsTheParticipationAtTheTemplateRepositoryOfTheNewExercise() throws Exception {
        var newExercise = newExercise();
        when(versionControlService.getCloneRepositoryUri(newExercise.getProjectKey(), newExercise.generateRepositoryName(RepositoryType.TEMPLATE)))
                .thenReturn(new LocalVCRepositoryUri("https://artemis.example.com/git/COURSE1EXERCISE1/course1exercise1-exercise.git"));

        participationService.setupInitialTemplateParticipation(newExercise);

        var templateParticipation = newExercise.getTemplateParticipation();
        assertThat(templateParticipation.getRepositoryUri()).endsWith("course1exercise1-exercise.git");
        assertThat(templateParticipation.getBuildPlanId()).isEqualTo(newExercise.generateBuildPlanId(de.tum.cit.aet.artemis.programming.domain.build.BuildPlanType.TEMPLATE));
        assertThat(templateParticipation.getInitializationState()).isEqualTo(de.tum.cit.aet.artemis.exercise.domain.InitializationState.INITIALIZED);
        assertThat(templateParticipation.getProgrammingExercise()).isSameAs(newExercise);
        verify(templateParticipationRepository).save(templateParticipation);
    }

    // --- resetRepository -------------------------------------------------------------------------------------------

    /**
     * A checked out repository on disk with one file in it, plus the git metadata a reset has to leave alone.
     */
    private de.tum.cit.aet.artemis.programming.domain.Repository checkoutWith(java.nio.file.Path path, String fileName, String content) throws Exception {
        java.nio.file.Files.createDirectories(path);
        org.eclipse.jgit.api.Git.init().setDirectory(path.toFile()).setInitialBranch("main").call().close();
        org.apache.commons.io.FileUtils.write(path.resolve(fileName).toFile(), content, java.nio.charset.StandardCharsets.UTF_8);
        org.apache.commons.io.FileUtils.write(path.resolve(".gitignore").toFile(), "build/", java.nio.charset.StandardCharsets.UTF_8);
        var repository = new de.tum.cit.aet.artemis.programming.domain.Repository(path.resolve(".git").toString(),
                new LocalVCRepositoryUri(java.net.URI.create("https://artemis.example.com"), "ABC", "abc-" + path.getFileName()));
        org.springframework.test.util.ReflectionTestUtils.setField(repository, "localPath", path);
        return repository;
    }

    private void withCheckouts(de.tum.cit.aet.artemis.programming.domain.Repository target, de.tum.cit.aet.artemis.programming.domain.Repository source) throws Exception {
        when(gitService.getOrCheckoutRepository(target.getRemoteRepositoryUri(), true, true)).thenReturn(target);
        when(gitService.getOrCheckoutRepository(source.getRemoteRepositoryUri(), true, true)).thenReturn(source);
    }

    @Test
    void resetRepository_replacesTheStudentsWorkWithTheTemplateButLeavesTheGitFolderIntact(@org.junit.jupiter.api.io.TempDir java.nio.file.Path baseDir) throws Exception {
        var target = checkoutWith(baseDir.resolve("student"), "Main.java", "the student's attempt");
        var source = checkoutWith(baseDir.resolve("template"), "Template.java", "the template");
        withCheckouts(target, source);

        participationService.resetRepository(target.getRemoteRepositoryUri(), source.getRemoteRepositoryUri());

        assertThat(baseDir.resolve("student/Template.java")).content(java.nio.charset.StandardCharsets.UTF_8).isEqualTo("the template");
        assertThat(baseDir.resolve("student/Main.java")).as("the student's own files are gone").doesNotExist();
        // Deleting the git folder would destroy the repository rather than reset it, taking the whole history with it.
        assertThat(baseDir.resolve("student/.git")).as("the repository itself survives the reset").isDirectory();
        verify(gitService).stageAllChanges(target);
        verify(gitService).commitAndPush(org.mockito.ArgumentMatchers.eq(target), any(), org.mockito.ArgumentMatchers.eq(true), org.mockito.ArgumentMatchers.isNull());
    }

    @Test
    void resetRepository_creditsTheUserWhoResetItSoTheCommitIsNotAnonymous(@org.junit.jupiter.api.io.TempDir java.nio.file.Path baseDir) throws Exception {
        var target = checkoutWith(baseDir.resolve("student"), "Main.java", "the student's attempt");
        var source = checkoutWith(baseDir.resolve("template"), "Template.java", "the template");
        withCheckouts(target, source);
        var user = new de.tum.cit.aet.artemis.account.domain.User();
        user.setFirstName("Anna");
        user.setLastName("Studentin");
        user.setEmail("anna.studentin@example.com");
        when(userRepository.findOneByLogin("ge12abc")).thenReturn(Optional.of(user));
        withLoggedInUser("ge12abc");

        try {
            participationService.resetRepository(target.getRemoteRepositoryUri(), source.getRemoteRepositoryUri());
        }
        finally {
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
        }

        var message = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(gitService).commitAndPush(org.mockito.ArgumentMatchers.eq(target), message.capture(), org.mockito.ArgumentMatchers.eq(true), org.mockito.ArgumentMatchers.isNull());
        assertThat(message.getValue()).startsWith("Reset Exercise").contains("Co-authored-by: Anna Studentin <anna.studentin@example.com>");
    }

    @Test
    void resetRepository_withoutALoggedInUser_commitsWithoutACoAuthor(@org.junit.jupiter.api.io.TempDir java.nio.file.Path baseDir) throws Exception {
        // Scheduled resets run without a user; the trailer has to be left off rather than filled with a placeholder.
        var target = checkoutWith(baseDir.resolve("student"), "Main.java", "the student's attempt");
        var source = checkoutWith(baseDir.resolve("template"), "Template.java", "the template");
        withCheckouts(target, source);
        org.springframework.security.core.context.SecurityContextHolder.clearContext();

        participationService.resetRepository(target.getRemoteRepositoryUri(), source.getRemoteRepositoryUri());

        var message = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(gitService).commitAndPush(org.mockito.ArgumentMatchers.eq(target), message.capture(), org.mockito.ArgumentMatchers.eq(true), org.mockito.ArgumentMatchers.isNull());
        assertThat(message.getValue()).isEqualTo("Reset Exercise").doesNotContain("Co-authored-by");
    }

    private static void withLoggedInUser(String login) {
        var context = org.springframework.security.core.context.SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(login, null, java.util.List.of()));
        org.springframework.security.core.context.SecurityContextHolder.setContext(context);
    }
}
