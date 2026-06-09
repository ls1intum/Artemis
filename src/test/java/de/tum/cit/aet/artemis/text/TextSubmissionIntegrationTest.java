package de.tum.cit.aet.artemis.text;

import static de.tum.cit.aet.artemis.core.util.TestResourceUtils.HalfSecond;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.within;

import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.communication.domain.Post;
import de.tum.cit.aet.artemis.communication.test_repository.PostTestRepository;
import de.tum.cit.aet.artemis.core.config.Constants;
import de.tum.cit.aet.artemis.core.domain.Language;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseMode;
import de.tum.cit.aet.artemis.exercise.domain.InitializationState;
import de.tum.cit.aet.artemis.exercise.domain.SubmissionVersion;
import de.tum.cit.aet.artemis.exercise.domain.Team;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.dto.ExerciseDetailsDTO;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationFactory;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationUtilService;
import de.tum.cit.aet.artemis.exercise.repository.SubmissionVersionRepository;
import de.tum.cit.aet.artemis.exercise.repository.TeamRepository;
import de.tum.cit.aet.artemis.exercise.test_repository.StudentParticipationTestRepository;
import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismCase;
import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismComparison;
import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismSubmission;
import de.tum.cit.aet.artemis.plagiarism.repository.PlagiarismCaseRepository;
import de.tum.cit.aet.artemis.plagiarism.repository.PlagiarismComparisonRepository;
import de.tum.cit.aet.artemis.shared.SeedData;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentBatchTest;
import de.tum.cit.aet.artemis.text.domain.TextExercise;
import de.tum.cit.aet.artemis.text.domain.TextSubmission;
import de.tum.cit.aet.artemis.text.repository.TextExerciseRepository;
import de.tum.cit.aet.artemis.text.test_repository.TextSubmissionTestRepository;
import de.tum.cit.aet.artemis.text.util.TextExerciseFactory;
import de.tum.cit.aet.artemis.text.util.TextExerciseUtilService;

class TextSubmissionIntegrationTest extends AbstractSpringIntegrationIndependentBatchTest {

    private static final String TEST_PREFIX = "textsubmissionintegration";

    @Autowired
    private TextSubmissionTestRepository testSubmissionTestRepository;

    @Autowired
    private SubmissionVersionRepository submissionVersionRepository;

    @Autowired
    private StudentParticipationTestRepository participationRepository;

    @Autowired
    private PlagiarismComparisonRepository plagiarismComparisonRepository;

    @Autowired
    private TeamRepository teamRepository;

    @Autowired
    private PlagiarismCaseRepository plagiarismCaseRepository;

    @Autowired
    private PostTestRepository postRepository;

    @Autowired
    private TextExerciseUtilService textExerciseUtilService;

    @Autowired
    private ParticipationUtilService participationUtilService;

    @Autowired
    private TextExerciseRepository textExerciseRepository;

    private TextExercise finishedTextExercise;

    private TextExercise releasedTextExercise;

    private TextSubmission textSubmission;

    private TextSubmission lateTextSubmission;

    private TextSubmission notSubmittedTextSubmission;

    private StudentParticipation lateParticipation;

    @BeforeEach
    void initTestCase() {
        // Reuse the shared seed instead of creating two courses with users and exercises per test: claim a fresh empty
        // exercise from the seed pool (each test gets its own, so it can submit/assess/mutate freely) and use the seed
        // students (1, 2), tutor and instructor, who are already enrolled in the seed course that owns the pool.
        finishedTextExercise = textExerciseRepository.findByIdElseThrow(SeedData.claimFinishedTextExerciseId());
        releasedTextExercise = textExerciseRepository.findByIdElseThrow(SeedData.claimReleasedTextExerciseId());
        lateParticipation = participationUtilService.createAndSaveParticipationForExercise(finishedTextExercise, SeedData.STUDENT_1_LOGIN);
        // Anchor the participation start to the (fixed) seed due date so it is before it (no individual-due-date extension).
        lateParticipation.setInitializationDate(finishedTextExercise.getDueDate().minusDays(1));
        participationRepository.save(lateParticipation);
        participationUtilService.createAndSaveParticipationForExercise(releasedTextExercise, SeedData.STUDENT_1_LOGIN);

        textSubmission = ParticipationFactory.generateTextSubmission("example text", Language.ENGLISH, true);
        lateTextSubmission = ParticipationFactory.generateLateTextSubmission("example text 2", Language.ENGLISH);
        notSubmittedTextSubmission = ParticipationFactory.generateTextSubmission("example text 2", Language.ENGLISH, false);

        // Outsider users that are intentionally not in the seed course/exercise (used by the negative-authorization tests).
        userUtilService.createAndSaveUser(TEST_PREFIX + "tutor2");
        userUtilService.createAndSaveUser(TEST_PREFIX + "student3");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student3")
    void testRepositoryMethods() {
        assertThatExceptionOfType(EntityNotFoundException.class)
                .isThrownBy(() -> testSubmissionTestRepository.findByIdWithParticipationExerciseResultAssessorElseThrow(Long.MAX_VALUE));

        assertThatExceptionOfType(EntityNotFoundException.class)
                .isThrownBy(() -> testSubmissionTestRepository.findByIdWithEagerResultsAndFeedbackAndTextBlocksElseThrow(Long.MAX_VALUE));

        assertThatExceptionOfType(EntityNotFoundException.class)
                .isThrownBy(() -> testSubmissionTestRepository.getTextSubmissionWithResultAndTextBlocksAndFeedbackByResultIdElseThrow(Long.MAX_VALUE));
    }

    @Test
    @WithMockUser(username = SeedData.TUTOR_LOGIN, roles = "TA")
    void getTextSubmissionWithResult() throws Exception {
        textSubmission = textExerciseUtilService.saveTextSubmission(finishedTextExercise, textSubmission, SeedData.STUDENT_1_LOGIN);
        participationUtilService.addResultToSubmission(textSubmission, AssessmentType.MANUAL);

        TextSubmission textSubmission = request.get("/api/text/text-submissions/" + this.textSubmission.getId(), HttpStatus.OK, TextSubmission.class);

        assertThat(textSubmission).as("text submission without assessment was found").isNotNull();
        assertThat(textSubmission.getId()).as("correct text submission was found").isEqualTo(this.textSubmission.getId());
        assertThat(textSubmission.getText()).as("text of text submission is correct").isEqualTo(this.textSubmission.getText());
        assertThat(textSubmission.getResults()).as("results are not loaded properly").isNotEmpty();
    }

    @Test
    @WithMockUser(username = SeedData.STUDENT_1_LOGIN, roles = "USER")
    void getTextSubmissionWithResult_involved_allowed() throws Exception {
        textSubmission = textExerciseUtilService.saveTextSubmission(finishedTextExercise, textSubmission, SeedData.STUDENT_1_LOGIN);
        PlagiarismComparison plagiarismComparison = new PlagiarismComparison();
        PlagiarismSubmission submissionA = new PlagiarismSubmission();
        submissionA.setStudentLogin(SeedData.STUDENT_1_LOGIN);
        submissionA.setSubmissionId(this.textSubmission.getId());
        plagiarismComparison.setSubmissionA(submissionA);
        PlagiarismCase plagiarismCase = new PlagiarismCase();
        plagiarismCase.setExercise(finishedTextExercise);
        plagiarismCase = plagiarismCaseRepository.save(plagiarismCase);
        Post post = new Post();
        post.setAuthor(userTestRepository.getUserByLoginElseThrow(SeedData.INSTRUCTOR_LOGIN));
        post.setTitle("Title Plagiarism Case Post");
        post.setContent("Content Plagiarism Case Post");
        post.setVisibleForStudents(true);
        post.setPlagiarismCase(plagiarismCase);
        postRepository.save(post);
        submissionA.setPlagiarismCase(plagiarismCase);
        plagiarismComparisonRepository.save(plagiarismComparison);

        var submission = request.get("/api/text/text-submissions/" + this.textSubmission.getId(), HttpStatus.OK, TextSubmission.class);

        assertThat(submission.getParticipation()).as("Should anonymize participation").isNull();
        assertThat(submission.getResults()).as("Should anonymize results").isEmpty();
        assertThat(submission.getSubmissionDate()).as("Should anonymize submission date").isNull();
    }

    @Test
    @WithMockUser(username = SeedData.STUDENT_1_LOGIN, roles = "USER")
    void getTextSubmissionWithResult_notInvolved_notAllowed() throws Exception {
        textSubmission = textExerciseUtilService.saveTextSubmission(finishedTextExercise, textSubmission, SeedData.STUDENT_1_LOGIN);
        request.get("/api/text/text-submissions/" + this.textSubmission.getId(), HttpStatus.FORBIDDEN, TextSubmission.class);
    }

    @Test
    @WithMockUser(username = SeedData.TUTOR_LOGIN, roles = "TA")
    void getAllTextSubmissions_studentHiddenForTutor() throws Exception {
        textSubmission = textExerciseUtilService.saveTextSubmissionWithResultAndAssessor(finishedTextExercise, textSubmission, SeedData.STUDENT_1_LOGIN, SeedData.TUTOR_LOGIN);

        List<TextSubmission> textSubmissions = request.getList("/api/text/exercises/" + finishedTextExercise.getId() + "/text-submissions?assessedByTutor=true", HttpStatus.OK,
                TextSubmission.class);

        assertThat(textSubmissions).as("one text submission was found").hasSize(1);
        assertThat(textSubmissions.getFirst().getId()).as("correct text submission was found").isEqualTo(textSubmission.getId());
        assertThat(((StudentParticipation) textSubmissions.getFirst().getParticipation()).getStudent()).as(TEST_PREFIX + "student of participation is hidden").isEmpty();
    }

    @Test
    @WithMockUser(username = SeedData.INSTRUCTOR_LOGIN, roles = "INSTRUCTOR")
    void getAllTextSubmissions_studentVisibleForInstructor() throws Exception {
        textSubmission = textExerciseUtilService.saveTextSubmission(finishedTextExercise, textSubmission, SeedData.STUDENT_1_LOGIN);

        List<TextSubmission> textSubmissions = request.getList("/api/text/exercises/" + finishedTextExercise.getId() + "/text-submissions", HttpStatus.OK, TextSubmission.class);

        assertThat(textSubmissions).as("one text submission was found").hasSize(1);
        assertThat(textSubmissions.getFirst().getId()).as("correct text submission was found").isEqualTo(textSubmission.getId());
        assertThat(((StudentParticipation) textSubmissions.getFirst().getParticipation()).getStudent()).as(TEST_PREFIX + "student of participation is hidden").isNotEmpty();
    }

    @Test
    @WithMockUser(username = SeedData.STUDENT_1_LOGIN, roles = "USER")
    void getAllTextSubmissions_assessedByTutorForStudent() throws Exception {
        textSubmission = textExerciseUtilService.saveTextSubmission(finishedTextExercise, textSubmission, SeedData.STUDENT_1_LOGIN);
        request.getList("/api/text/exercises/" + finishedTextExercise.getId() + "/text-submissions?assessedByTutor=true", HttpStatus.FORBIDDEN, TextSubmission.class);
    }

    @Test
    @WithMockUser(username = SeedData.TUTOR_LOGIN, roles = "TA")
    void getAllTextSubmissions_notAssessedByTutorForTutor() throws Exception {
        textSubmission = textExerciseUtilService.saveTextSubmission(finishedTextExercise, textSubmission, SeedData.STUDENT_1_LOGIN);
        request.getList("/api/text/exercises/" + finishedTextExercise.getId() + "/text-submissions", HttpStatus.FORBIDDEN, TextSubmission.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor2", roles = "TA")
    void getAllTextSubmission_notTutorInExercise() throws Exception {
        textSubmission = textExerciseUtilService.saveTextSubmission(finishedTextExercise, textSubmission, SeedData.STUDENT_1_LOGIN);
        request.getList("/api/text/exercises/" + finishedTextExercise.getId() + "/text-submissions?assessedByTutor=true", HttpStatus.FORBIDDEN, TextSubmission.class);
    }

    @Test
    @WithMockUser(username = SeedData.TUTOR_LOGIN, roles = "TA")
    void getTextSubmissionWithoutAssessment_studentHidden() throws Exception {
        textSubmission = textExerciseUtilService.saveTextSubmission(finishedTextExercise, textSubmission, SeedData.STUDENT_1_LOGIN);

        TextSubmission textSubmissionWithoutAssessment = request.get("/api/text/exercises/" + finishedTextExercise.getId() + "/text-submission-without-assessment", HttpStatus.OK,
                TextSubmission.class);

        assertThat(textSubmissionWithoutAssessment).as("text submission without assessment was found").isNotNull();
        assertThat(textSubmissionWithoutAssessment.getId()).as("correct text submission was found").isEqualTo(textSubmission.getId());
        assertThat(((StudentParticipation) textSubmissionWithoutAssessment.getParticipation()).getStudent()).as(TEST_PREFIX + "student of participation is hidden").isEmpty();
    }

    @Test
    @WithMockUser(username = SeedData.TUTOR_LOGIN, roles = "TA")
    void getTextSubmissionWithoutAssessment_lockSubmission() throws Exception {
        User user = userUtilService.getUserByLogin(SeedData.TUTOR_LOGIN);
        textSubmission = textExerciseUtilService.saveTextSubmission(finishedTextExercise, textSubmission, SeedData.STUDENT_1_LOGIN);

        TextSubmission storedSubmission = request.get("/api/text/exercises/" + finishedTextExercise.getId() + "/text-submission-without-assessment?lock=true", HttpStatus.OK,
                TextSubmission.class);

        final String[] ignoringFields = { "results", "submissionDate", "blocks", "participation" };
        assertThat(storedSubmission).as("submission was found").usingRecursiveComparison().ignoringFields(ignoringFields).isEqualTo(textSubmission);
        assertThat(storedSubmission.getSubmissionDate()).as("submission date is correct").isCloseTo(textSubmission.getSubmissionDate(), HalfSecond());
        assertThat(storedSubmission.getLatestResult()).as("result is set").isNotNull();
        assertThat(storedSubmission.getLatestResult().getAssessor()).as("assessor is tutor1").isEqualTo(user);
        checkDetailsHidden(storedSubmission, false);
    }

    @Test
    @WithMockUser(username = SeedData.TUTOR_LOGIN, roles = "TA")
    void getTextSubmissionWithoutAssessment_selectInTime() throws Exception {
        // This test compares submission dates (now-1day vs now+1day) against the due date, so anchor the claimed
        // exercise's due date to now (the seed pool uses fixed dates).
        finishedTextExercise.setDueDate(ZonedDateTime.now());
        finishedTextExercise.setAssessmentDueDate(ZonedDateTime.now().plusDays(2));
        finishedTextExercise = textExerciseRepository.save(finishedTextExercise);

        textSubmission = textExerciseUtilService.saveTextSubmission(finishedTextExercise, textSubmission, SeedData.STUDENT_1_LOGIN);
        lateTextSubmission = textExerciseUtilService.saveTextSubmission(finishedTextExercise, lateTextSubmission, SeedData.STUDENT_2_LOGIN);

        assertThat(textSubmission.getSubmissionDate()).as("first submission is in-time").isBefore(finishedTextExercise.getDueDate());
        assertThat(lateTextSubmission.getSubmissionDate()).as("second submission is late").isAfter(finishedTextExercise.getDueDate());

        TextSubmission storedSubmission = request.get("/api/text/exercises/" + finishedTextExercise.getId() + "/text-submission-without-assessment", HttpStatus.OK,
                TextSubmission.class);

        assertThat(storedSubmission).as("text submission without assessment was found").isNotNull();
        assertThat(storedSubmission.getId()).as("in-time text submission was found").isEqualTo(textSubmission.getId());
    }

    @Test
    @WithMockUser(username = SeedData.TUTOR_LOGIN, roles = "TA")
    void getTextSubmissionWithoutAssessment_noSubmittedSubmission_null() throws Exception {
        TextSubmission submission = ParticipationFactory.generateTextSubmission("text", Language.ENGLISH, false);
        textExerciseUtilService.saveTextSubmission(finishedTextExercise, submission, SeedData.STUDENT_1_LOGIN);

        var response = request.get("/api/text/exercises/" + finishedTextExercise.getId() + "/text-submission-without-assessment", HttpStatus.OK, TextSubmission.class);
        assertThat(response).isNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor2", roles = "TA")
    void getTextSubmissionWithoutAssessment_notTutorInExercise() throws Exception {
        textSubmission = textExerciseUtilService.saveTextSubmission(finishedTextExercise, textSubmission, SeedData.STUDENT_1_LOGIN);
        request.get("/api/text/exercises/" + finishedTextExercise.getId() + "/text-submission-without-assessment", HttpStatus.FORBIDDEN, TextSubmission.class);
    }

    @Test
    @WithMockUser(username = SeedData.TUTOR_LOGIN, roles = "TA")
    void getTextSubmissionWithoutAssessment_dueDateNotOver() throws Exception {
        textSubmission = textExerciseUtilService.saveTextSubmission(releasedTextExercise, textSubmission, SeedData.STUDENT_1_LOGIN);

        request.get("/api/text/exercises/" + releasedTextExercise.getId() + "/text-submission-without-assessment", HttpStatus.FORBIDDEN, TextSubmission.class);
    }

    @Test
    @WithMockUser(username = SeedData.STUDENT_1_LOGIN)
    void getTextSubmissionWithoutAssessment_asStudent_forbidden() throws Exception {
        textSubmission = textExerciseUtilService.saveTextSubmission(finishedTextExercise, textSubmission, SeedData.STUDENT_1_LOGIN);

        request.get("/api/text/exercises/" + finishedTextExercise.getId() + "/text-submission-without-assessment", HttpStatus.FORBIDDEN, TextSubmission.class);
    }

    @Test
    @WithMockUser(username = SeedData.STUDENT_1_LOGIN)
    void getResultsForCurrentStudent_assessorHiddenForStudent() throws Exception {
        textSubmission = ParticipationFactory.generateTextSubmission("Some text", Language.ENGLISH, true);
        textExerciseUtilService.saveTextSubmissionWithResultAndAssessor(finishedTextExercise, textSubmission, SeedData.STUDENT_1_LOGIN, SeedData.TUTOR_LOGIN);

        ExerciseDetailsDTO returnedExerciseDetails = request.get("/api/exercise/exercises/" + finishedTextExercise.getId() + "/details", HttpStatus.OK, ExerciseDetailsDTO.class);
        StudentParticipation studentParticipation = returnedExerciseDetails.exercise().getStudentParticipations().iterator().next();
        assertThat(participationUtilService.getResultsForParticipation(studentParticipation).iterator().next().getAssessor()).as("assessor is null").isNull();
    }

    @Test
    @WithMockUser(username = SeedData.STUDENT_1_LOGIN, roles = "USER")
    void getDataForTextEditorWithResult() throws Exception {
        TextSubmission textSubmission = textExerciseUtilService.saveTextSubmissionWithResultAndAssessor(finishedTextExercise, this.textSubmission, SeedData.STUDENT_1_LOGIN,
                SeedData.TUTOR_LOGIN);
        Long participationId = textSubmission.getParticipation().getId();

        StudentParticipation participation = request.get("/api/text/text-editor/" + participationId, HttpStatus.OK, StudentParticipation.class);

        Set<Result> results = participationUtilService.getResultsForParticipation(participation);
        assertThat(results).isNotNull();
        assertThat(results).hasSize(1);

        assertThat(participation.getSubmissions()).isNotNull();
    }

    @Test
    @WithMockUser(username = SeedData.STUDENT_1_LOGIN, roles = "USER")
    void submitExercise_afterDueDate_forbidden() throws Exception {
        request.put("/api/text/exercises/" + finishedTextExercise.getId() + "/text-submissions", textSubmission, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = SeedData.STUDENT_1_LOGIN, roles = "USER")
    void submitExercise_beforeDueDate_isTeamMode() throws Exception {
        releasedTextExercise.setMode(ExerciseMode.TEAM);
        exerciseRepository.save(releasedTextExercise);
        Team team = new Team();
        team.setName("Team");
        team.setShortName("team");
        team.setExercise(releasedTextExercise);
        team.addStudents(userTestRepository.findOneByLogin(SeedData.STUDENT_1_LOGIN).orElseThrow());
        team.addStudents(userTestRepository.findOneByLogin(SeedData.STUDENT_2_LOGIN).orElseThrow());
        teamRepository.save(releasedTextExercise, team);

        StudentParticipation participation = participationUtilService.addTeamParticipationForExercise(releasedTextExercise, team.getId());
        releasedTextExercise.setStudentParticipations(Set.of(participation));

        TextSubmission submission = request.putWithResponseBody("/api/text/exercises/" + releasedTextExercise.getId() + "/text-submissions", textSubmission, TextSubmission.class,
                HttpStatus.OK);

        userUtilService.changeUser(SeedData.STUDENT_1_LOGIN);
        Optional<SubmissionVersion> version = submissionVersionRepository.findLatestVersion(submission.getId());
        assertThat(version).as("submission version was created").isNotEmpty();
        assertThat(version.orElseThrow().getAuthor().getLogin()).as("submission version has correct author").isEqualTo(SeedData.STUDENT_1_LOGIN);
        assertThat(version.get().getContent()).as("submission version has correct content").isEqualTo(submission.getText());

        userUtilService.changeUser(SeedData.STUDENT_2_LOGIN);
        submission.setText(submission.getText() + " Extra contribution.");
        request.put("/api/text/exercises/" + releasedTextExercise.getId() + "/text-submissions", submission, HttpStatus.OK);

        // create new submission to simulate other teams working at the same time
        request.putWithResponseBody("/api/text/exercises/" + releasedTextExercise.getId() + "/text-submissions", textSubmission, TextSubmission.class, HttpStatus.OK);

        userUtilService.changeUser(SeedData.STUDENT_2_LOGIN);
        version = submissionVersionRepository.findLatestVersion(submission.getId());
        assertThat(version).as("submission version was created").isNotEmpty();
        assertThat(version.orElseThrow().getAuthor().getLogin()).as("submission version has correct author").isEqualTo(SeedData.STUDENT_2_LOGIN);
        assertThat(version.get().getContent()).as("submission version has correct content").isEqualTo(submission.getText());

        submission.setText(submission.getText() + " Even more.");
        request.put("/api/text/exercises/" + releasedTextExercise.getId() + "/text-submissions", submission, HttpStatus.OK);
        userUtilService.changeUser(SeedData.STUDENT_2_LOGIN);
        Optional<SubmissionVersion> newVersion = submissionVersionRepository.findLatestVersion(submission.getId());
        assertThat(newVersion.orElseThrow().getId()).as("submission version was not created").isEqualTo(version.get().getId());

        // Note: Cleanup of participations through orphan removal is not possible here because
        // the participations have submissions that are not cascade-deleted. The test database
        // is reset between test runs, so explicit cleanup is not required.
    }

    @Test
    @WithMockUser(username = SeedData.STUDENT_1_LOGIN, roles = "USER")
    void submitExercise_beforeDueDate_allowed() throws Exception {
        TextSubmission submission = request.putWithResponseBody("/api/text/exercises/" + releasedTextExercise.getId() + "/text-submissions", textSubmission, TextSubmission.class,
                HttpStatus.OK);

        assertThat(submission.getSubmissionDate()).isCloseTo(ZonedDateTime.now(), within(500, ChronoUnit.MILLIS));
        assertThat(submission.getParticipation().getInitializationState()).isEqualTo(InitializationState.FINISHED);
    }

    @Test
    @WithMockUser(username = SeedData.STUDENT_1_LOGIN, roles = "USER")
    void saveAndSubmitTextSubmission_tooLarge() throws Exception {
        // should be ok
        char[] chars = new char[(int) (Constants.MAX_SUBMISSION_TEXT_LENGTH)];
        Arrays.fill(chars, 'a');
        textSubmission.setText(new String(chars));
        request.postWithResponseBody("/api/text/exercises/" + releasedTextExercise.getId() + "/text-submissions", textSubmission, TextSubmission.class, HttpStatus.OK);

        // should be too large
        char[] charsTooLarge = new char[(int) (Constants.MAX_SUBMISSION_TEXT_LENGTH + 1)];
        Arrays.fill(charsTooLarge, 'a');
        textSubmission.setText(new String(charsTooLarge));
        request.put("/api/text/exercises/" + releasedTextExercise.getId() + "/text-submissions", textSubmission, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = SeedData.STUDENT_1_LOGIN, roles = "USER")
    void submitExercise_beforeDueDateWithTwoSubmissions_allowed() throws Exception {
        final var submitPath = "/api/text/exercises/" + releasedTextExercise.getId() + "/text-submissions";
        final var newSubmissionText = "Some other test text";
        textSubmission = request.putWithResponseBody(submitPath, textSubmission, TextSubmission.class, HttpStatus.OK);
        textSubmission.setText(newSubmissionText);
        request.put(submitPath, textSubmission, HttpStatus.OK);

        final var submissionInDb = testSubmissionTestRepository.findById(textSubmission.getId());
        assertThat(submissionInDb).isPresent();
        assertThat(submissionInDb.get().getText()).isEqualTo(newSubmissionText);
    }

    @Test
    @WithMockUser(username = SeedData.STUDENT_1_LOGIN, roles = "USER")
    void submitExercise_afterDueDateWithParticipationStartAfterDueDate_allowed() throws Exception {
        lateParticipation.setInitializationDate(ZonedDateTime.now());
        participationRepository.save(lateParticipation);

        request.put("/api/text/exercises/" + releasedTextExercise.getId() + "/text-submissions", textSubmission, HttpStatus.OK);
    }

    @Test
    @WithMockUser(username = SeedData.STUDENT_1_LOGIN, roles = "USER")
    void saveExercise_beforeDueDate() throws Exception {
        TextSubmission storedSubmission = request.putWithResponseBody("/api/text/exercises/" + releasedTextExercise.getId() + "/text-submissions", notSubmittedTextSubmission,
                TextSubmission.class, HttpStatus.OK);
        assertThat(storedSubmission.isSubmitted()).isTrue();
    }

    @Test
    @WithMockUser(username = SeedData.STUDENT_1_LOGIN, roles = "USER")
    void saveExercise_afterDueDateWithParticipationStartAfterDueDate() throws Exception {
        exerciseUtilService.updateExerciseDueDate(releasedTextExercise.getId(), ZonedDateTime.now().minusHours(1));
        lateParticipation.setInitializationDate(ZonedDateTime.now());
        participationRepository.save(lateParticipation);

        TextSubmission storedSubmission = request.putWithResponseBody("/api/text/exercises/" + releasedTextExercise.getId() + "/text-submissions", notSubmittedTextSubmission,
                TextSubmission.class, HttpStatus.OK);
        assertThat(storedSubmission.isSubmitted()).isFalse();

    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student3", roles = "USER")
    void submitExercise_notStudentInCourse() throws Exception {
        request.post("/api/text/exercises/" + releasedTextExercise.getId() + "/text-submissions", textSubmission, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = SeedData.STUDENT_1_LOGIN, roles = "USER")
    void submitExercise_submissionIsAlreadyCreated_badRequest() throws Exception {
        textSubmission = testSubmissionTestRepository.save(textSubmission);
        request.post("/api/text/exercises/" + releasedTextExercise.getId() + "/text-submissions", textSubmission, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = SeedData.STUDENT_1_LOGIN, roles = "USER")
    void submitExercise_noExercise_badRequest() throws Exception {
        // Use an id far outside the seed exercise pool so it is guaranteed not to exist (the pool occupies the 9.x M range).
        var fakeExerciseId = releasedTextExercise.getId() + 1_000_000L;
        request.post("/api/text/exercises/" + fakeExerciseId + "/text-submissions", textSubmission, HttpStatus.NOT_FOUND);
    }

    @Test
    @WithMockUser(username = SeedData.INSTRUCTOR_LOGIN, roles = "INSTRUCTOR")
    void deleteTextSubmissionWithTextBlocks() throws Exception {
        textSubmission.setText("Lorem Ipsum dolor sit amet");
        textSubmission = textExerciseUtilService.saveTextSubmission(releasedTextExercise, textSubmission, SeedData.STUDENT_1_LOGIN);
        final var blocks = Set.of(TextExerciseFactory.generateTextBlock(0, 11), TextExerciseFactory.generateTextBlock(12, 21), TextExerciseFactory.generateTextBlock(22, 26));
        textExerciseUtilService.addAndSaveTextBlocksToTextSubmission(blocks, textSubmission);

        request.delete("/api/exercise/submissions/" + textSubmission.getId(), HttpStatus.OK);
    }

    private void checkDetailsHidden(TextSubmission submission, boolean isStudent) {
        assertThat(participationUtilService.getResultsForParticipation(submission.getParticipation())).as("results are hidden in participation").isNullOrEmpty();
        if (isStudent) {
            assertThat(submission.getLatestResult()).as("result is hidden").isNull();
        }
        else {
            assertThat(((StudentParticipation) submission.getParticipation()).getStudent()).as(TEST_PREFIX + "student of participation is hidden").isEmpty();
        }
    }
}
