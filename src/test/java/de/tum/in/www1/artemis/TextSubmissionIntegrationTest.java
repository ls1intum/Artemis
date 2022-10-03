package de.tum.in.www1.artemis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.config.Constants;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.AssessmentType;
import de.tum.in.www1.artemis.domain.enumeration.ExerciseMode;
import de.tum.in.www1.artemis.domain.enumeration.InitializationState;
import de.tum.in.www1.artemis.domain.enumeration.Language;
import de.tum.in.www1.artemis.domain.metis.Post;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.domain.plagiarism.PlagiarismCase;
import de.tum.in.www1.artemis.domain.plagiarism.PlagiarismComparison;
import de.tum.in.www1.artemis.domain.plagiarism.PlagiarismSubmission;
import de.tum.in.www1.artemis.domain.plagiarism.text.TextSubmissionElement;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.repository.metis.PostRepository;
import de.tum.in.www1.artemis.repository.plagiarism.PlagiarismCaseRepository;
import de.tum.in.www1.artemis.repository.plagiarism.PlagiarismComparisonRepository;
import de.tum.in.www1.artemis.util.ModelFactory;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

class TextSubmissionIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    private ExerciseRepository exerciseRepo;

    @Autowired
    private TextSubmissionRepository submissionRepository;

    @Autowired
    private SubmissionVersionRepository submissionVersionRepository;

    @Autowired
    private StudentParticipationRepository participationRepository;

    @Autowired
    private PlagiarismComparisonRepository plagiarismComparisonRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TeamRepository teamRepository;

    @Autowired
    private PlagiarismCaseRepository plagiarismCaseRepository;

    @Autowired
    private PostRepository postRepository;

    private TextExercise finishedTextExercise;

    private TextExercise releasedTextExercise;

    private TextSubmission textSubmission;

    private TextSubmission lateTextSubmission;

    private TextSubmission notSubmittedTextSubmission;

    private StudentParticipation lateParticipation;

    @BeforeEach
    void initTestCase() {
        User student = database.addUsers(2, 1, 0, 1).get(0);
        Course course1 = database.addCourseWithOneReleasedTextExercise();
        Course course2 = database.addCourseWithOneFinishedTextExercise();
        releasedTextExercise = database.findTextExerciseWithTitle(course1.getExercises(), "Text");
        finishedTextExercise = database.findTextExerciseWithTitle(course2.getExercises(), "Finished");
        lateParticipation = database.createAndSaveParticipationForExercise(finishedTextExercise, student.getLogin());
        lateParticipation.setInitializationDate(ZonedDateTime.now().minusDays(2));
        participationRepository.save(lateParticipation);
        database.createAndSaveParticipationForExercise(releasedTextExercise, student.getLogin());

        textSubmission = ModelFactory.generateTextSubmission("example text", Language.ENGLISH, true);
        lateTextSubmission = ModelFactory.generateLateTextSubmission("example text 2", Language.ENGLISH);
        notSubmittedTextSubmission = ModelFactory.generateTextSubmission("example text 2", Language.ENGLISH, false);

        // Add users that are not in exercise/course
        userRepository.save(ModelFactory.generateActivatedUser("tutor2"));
        userRepository.save(ModelFactory.generateActivatedUser("student3"));
    }

    @AfterEach
    void tearDown() {
        database.resetDatabase();
    }

    @Test
    @WithMockUser(username = "student3")
    void testRepositoryMethods() {
        assertThrows(EntityNotFoundException.class, () -> submissionRepository.findByIdWithParticipationExerciseResultAssessorElseThrow(Long.MAX_VALUE));
        assertThrows(EntityNotFoundException.class, () -> submissionRepository.findByIdWithEagerResultsAndFeedbackAndTextBlocksElseThrow(Long.MAX_VALUE));
        assertThrows(BadRequestAlertException.class, () -> submissionRepository.getTextSubmissionWithResultAndTextBlocksAndFeedbackByResultIdElseThrow(Long.MAX_VALUE));
        assertThrows(BadRequestAlertException.class, () -> submissionRepository.findByIdWithEagerParticipationExerciseResultAssessorElseThrow(Long.MAX_VALUE));
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void getTextSubmissionWithResult() throws Exception {
        textSubmission = database.saveTextSubmission(finishedTextExercise, textSubmission, "student1");
        database.addResultToSubmission(textSubmission, AssessmentType.MANUAL);

        TextSubmission textSubmission = request.get("/api/text-submissions/" + this.textSubmission.getId(), HttpStatus.OK, TextSubmission.class);

        assertThat(textSubmission).as("text submission without assessment was found").isNotNull();
        assertThat(textSubmission.getId()).as("correct text submission was found").isEqualTo(this.textSubmission.getId());
        assertThat(textSubmission.getText()).as("text of text submission is correct").isEqualTo(this.textSubmission.getText());
        assertThat(textSubmission.getResults()).as("results are not loaded properly").isNotEmpty();
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void getTextSubmissionWithResult_involved_allowed() throws Exception {
        textSubmission = database.saveTextSubmission(finishedTextExercise, textSubmission, "student1");
        PlagiarismComparison<TextSubmissionElement> plagiarismComparison = new PlagiarismComparison<>();
        PlagiarismSubmission<TextSubmissionElement> submissionA = new PlagiarismSubmission<>();
        submissionA.setStudentLogin("student1");
        submissionA.setSubmissionId(this.textSubmission.getId());
        plagiarismComparison.setSubmissionA(submissionA);
        PlagiarismCase plagiarismCase = new PlagiarismCase();
        plagiarismCase = plagiarismCaseRepository.save(plagiarismCase);
        Post post = new Post();
        post.setAuthor(userRepository.getUserByLoginElseThrow("instructor1"));
        post.setTitle("Title Plagiarism Case Post");
        post.setContent("Content Plagiarism Case Post");
        post.setVisibleForStudents(true);
        post.setPlagiarismCase(plagiarismCase);
        postRepository.save(post);
        submissionA.setPlagiarismCase(plagiarismCase);
        plagiarismComparisonRepository.save(plagiarismComparison);

        var submission = request.get("/api/text-submissions/" + this.textSubmission.getId(), HttpStatus.OK, TextSubmission.class);

        assertThat(submission.getParticipation()).as("Should anonymize participation").isNull();
        assertThat(submission.getResults()).as("Should anonymize results").isEmpty();
        assertThat(submission.getSubmissionDate()).as("Should anonymize submission date").isNull();
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void getTextSubmissionWithResult_notInvolved_notAllowed() throws Exception {
        textSubmission = database.saveTextSubmission(finishedTextExercise, textSubmission, "student1");
        request.get("/api/text-submissions/" + this.textSubmission.getId(), HttpStatus.FORBIDDEN, TextSubmission.class);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void getAllTextSubmissions_studentHiddenForTutor() throws Exception {
        textSubmission = database.saveTextSubmissionWithResultAndAssessor(finishedTextExercise, textSubmission, "student1", "tutor1");

        List<TextSubmission> textSubmissions = request.getList("/api/exercises/" + finishedTextExercise.getId() + "/text-submissions?assessedByTutor=true", HttpStatus.OK,
                TextSubmission.class);

        assertThat(textSubmissions).as("one text submission was found").hasSize(1);
        assertThat(textSubmissions.get(0).getId()).as("correct text submission was found").isEqualTo(textSubmission.getId());
        assertThat(((StudentParticipation) textSubmissions.get(0).getParticipation()).getStudent()).as("student of participation is hidden").isEmpty();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void getAllTextSubmissions_studentVisibleForInstructor() throws Exception {
        textSubmission = database.saveTextSubmission(finishedTextExercise, textSubmission, "student1");

        List<TextSubmission> textSubmissions = request.getList("/api/exercises/" + finishedTextExercise.getId() + "/text-submissions", HttpStatus.OK, TextSubmission.class);

        assertThat(textSubmissions).as("one text submission was found").hasSize(1);
        assertThat(textSubmissions.get(0).getId()).as("correct text submission was found").isEqualTo(textSubmission.getId());
        assertThat(((StudentParticipation) textSubmissions.get(0).getParticipation()).getStudent()).as("student of participation is hidden").isNotEmpty();
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void getAllTextSubmissions_assessedByTutorForStudent() throws Exception {
        textSubmission = database.saveTextSubmission(finishedTextExercise, textSubmission, "student1");
        request.getList("/api/exercises/" + finishedTextExercise.getId() + "/text-submissions?assessedByTutor=true", HttpStatus.FORBIDDEN, TextSubmission.class);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void getAllTextSubmissions_notAssessedByTutorForTutor() throws Exception {
        textSubmission = database.saveTextSubmission(finishedTextExercise, textSubmission, "student1");
        request.getList("/api/exercises/" + finishedTextExercise.getId() + "/text-submissions", HttpStatus.FORBIDDEN, TextSubmission.class);
    }

    @Test
    @WithMockUser(username = "tutor2", roles = "TA")
    void getAllTextSubmission_notTutorInExercise() throws Exception {
        textSubmission = database.saveTextSubmission(finishedTextExercise, textSubmission, "student1");
        request.getList("/api/exercises/" + finishedTextExercise.getId() + "/text-submissions?assessedByTutor=true", HttpStatus.FORBIDDEN, TextSubmission.class);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void getTextSubmissionWithoutAssessment_studentHidden() throws Exception {
        textSubmission = database.saveTextSubmission(finishedTextExercise, textSubmission, "student1");

        TextSubmission textSubmissionWithoutAssessment = request.get("/api/exercises/" + finishedTextExercise.getId() + "/text-submission-without-assessment", HttpStatus.OK,
                TextSubmission.class);

        assertThat(textSubmissionWithoutAssessment).as("text submission without assessment was found").isNotNull();
        assertThat(textSubmissionWithoutAssessment.getId()).as("correct text submission was found").isEqualTo(textSubmission.getId());
        assertThat(((StudentParticipation) textSubmissionWithoutAssessment.getParticipation()).getStudent()).as("student of participation is hidden").isEmpty();
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void getTextSubmissionWithoutAssessment_lockSubmission() throws Exception {
        User user = database.getUserByLogin("tutor1");
        textSubmission = database.saveTextSubmission(finishedTextExercise, textSubmission, "student1");

        TextSubmission storedSubmission = request.get("/api/exercises/" + finishedTextExercise.getId() + "/text-submission-without-assessment?lock=true", HttpStatus.OK,
                TextSubmission.class);

        // set dates to UTC and round to milliseconds for comparison
        textSubmission.setSubmissionDate(ZonedDateTime.ofInstant(textSubmission.getSubmissionDate().truncatedTo(ChronoUnit.MILLIS).toInstant(), ZoneId.of("UTC")));
        storedSubmission.setSubmissionDate(ZonedDateTime.ofInstant(storedSubmission.getSubmissionDate().truncatedTo(ChronoUnit.MILLIS).toInstant(), ZoneId.of("UTC")));
        assertThat(storedSubmission).as("submission was found").isEqualTo(textSubmission);
        assertThat(storedSubmission.getLatestResult()).as("result is set").isNotNull();
        assertThat(storedSubmission.getLatestResult().getAssessor()).as("assessor is tutor1").isEqualTo(user);
        checkDetailsHidden(storedSubmission, false);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void getTextSubmissionWithoutAssessment_selectInTime() throws Exception {

        textSubmission = database.saveTextSubmission(finishedTextExercise, textSubmission, "student1");
        lateTextSubmission = database.saveTextSubmission(finishedTextExercise, lateTextSubmission, "student2");

        assertThat(textSubmission.getSubmissionDate()).as("first submission is in-time").isBefore(finishedTextExercise.getDueDate());
        assertThat(lateTextSubmission.getSubmissionDate()).as("second submission is late").isAfter(finishedTextExercise.getDueDate());

        TextSubmission storedSubmission = request.get("/api/exercises/" + finishedTextExercise.getId() + "/text-submission-without-assessment", HttpStatus.OK,
                TextSubmission.class);

        assertThat(storedSubmission).as("text submission without assessment was found").isNotNull();
        assertThat(storedSubmission.getId()).as("in-time text submission was found").isEqualTo(textSubmission.getId());
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void getTextSubmissionWithoutAssessment_noSubmittedSubmission_notFound() throws Exception {
        TextSubmission submission = ModelFactory.generateTextSubmission("text", Language.ENGLISH, false);
        database.saveTextSubmission(finishedTextExercise, submission, "student1");

        request.get("/api/exercises/" + finishedTextExercise.getId() + "/text-submission-without-assessment", HttpStatus.NOT_FOUND, TextSubmission.class);
    }

    @Test
    @WithMockUser(username = "tutor2", roles = "TA")
    void getTextSubmissionWithoutAssessment_notTutorInExercise() throws Exception {
        textSubmission = database.saveTextSubmission(finishedTextExercise, textSubmission, "student1");
        request.get("/api/exercises/" + finishedTextExercise.getId() + "/text-submission-without-assessment", HttpStatus.FORBIDDEN, TextSubmission.class);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void getTextSubmissionWithoutAssessment_dueDateNotOver() throws Exception {
        textSubmission = database.saveTextSubmission(releasedTextExercise, textSubmission, "student1");

        request.get("/api/exercises/" + releasedTextExercise.getId() + "/text-submission-without-assessment", HttpStatus.FORBIDDEN, TextSubmission.class);
    }

    @Test
    @WithMockUser(username = "student1")
    void getTextSubmissionWithoutAssessment_asStudent_forbidden() throws Exception {
        textSubmission = database.saveTextSubmission(finishedTextExercise, textSubmission, "student1");

        request.get("/api/exercises/" + finishedTextExercise.getId() + "/text-submission-without-assessment", HttpStatus.FORBIDDEN, TextSubmission.class);
    }

    @Test
    @WithMockUser(username = "student1")
    void getResultsForCurrentStudent_assessorHiddenForStudent() throws Exception {
        textSubmission = ModelFactory.generateTextSubmission("Some text", Language.ENGLISH, true);
        database.saveTextSubmissionWithResultAndAssessor(finishedTextExercise, textSubmission, "student1", "tutor1");

        Exercise returnedExercise = request.get("/api/exercises/" + finishedTextExercise.getId() + "/details", HttpStatus.OK, Exercise.class);

        assertThat(returnedExercise.getStudentParticipations().iterator().next().getResults().iterator().next().getAssessor()).as("assessor is null").isNull();
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void getDataForTextEditorWithResult() throws Exception {
        TextSubmission textSubmission = database.saveTextSubmissionWithResultAndAssessor(finishedTextExercise, this.textSubmission, "student1", "tutor1");
        Long participationId = textSubmission.getParticipation().getId();

        StudentParticipation participation = request.get("/api/text-editor/" + participationId, HttpStatus.OK, StudentParticipation.class);

        assertThat(participation.getResults()).isNotNull();
        assertThat(participation.getResults()).hasSize(1);

        assertThat(participation.getSubmissions()).isNotNull();
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void submitExercise_afterDueDate_forbidden() throws Exception {
        request.put("/api/exercises/" + finishedTextExercise.getId() + "/text-submissions", textSubmission, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void submitExercise_beforeDueDate_isTeamMode() throws Exception {
        releasedTextExercise.setMode(ExerciseMode.TEAM);
        exerciseRepo.save(releasedTextExercise);
        Team team = new Team();
        team.setName("Team");
        team.setShortName("team");
        team.setExercise(releasedTextExercise);
        team.addStudents(userRepository.findOneByLogin("student1").orElseThrow());
        team.addStudents(userRepository.findOneByLogin("student2").orElseThrow());
        teamRepository.save(releasedTextExercise, team);

        StudentParticipation participation = database.addTeamParticipationForExercise(releasedTextExercise, team.getId());
        releasedTextExercise.setStudentParticipations(Set.of(participation));

        TextSubmission submission = request.putWithResponseBody("/api/exercises/" + releasedTextExercise.getId() + "/text-submissions", textSubmission, TextSubmission.class,
                HttpStatus.OK);

        database.changeUser("student1");
        Optional<SubmissionVersion> version = submissionVersionRepository.findLatestVersion(submission.getId());
        assertThat(version).as("submission version was created").isNotEmpty();
        assertThat(version.get().getAuthor().getLogin()).as("submission version has correct author").isEqualTo("student1");
        assertThat(version.get().getContent()).as("submission version has correct content").isEqualTo(submission.getText());

        database.changeUser("student2");
        submission.setText(submission.getText() + " Extra contribution.");
        request.put("/api/exercises/" + releasedTextExercise.getId() + "/text-submissions", submission, HttpStatus.OK);

        // create new submission to simulate other teams working at the same time
        request.putWithResponseBody("/api/exercises/" + releasedTextExercise.getId() + "/text-submissions", textSubmission, TextSubmission.class, HttpStatus.OK);

        database.changeUser("student2");
        version = submissionVersionRepository.findLatestVersion(submission.getId());
        assertThat(version).as("submission version was created").isNotEmpty();
        assertThat(version.get().getAuthor().getLogin()).as("submission version has correct author").isEqualTo("student2");
        assertThat(version.get().getContent()).as("submission version has correct content").isEqualTo(submission.getText());

        submission.setText(submission.getText() + " Even more.");
        request.put("/api/exercises/" + releasedTextExercise.getId() + "/text-submissions", submission, HttpStatus.OK);
        database.changeUser("student2");
        Optional<SubmissionVersion> newVersion = submissionVersionRepository.findLatestVersion(submission.getId());
        assertThat(newVersion.orElseThrow().getId()).as("submission version was not created").isEqualTo(version.get().getId());

        exerciseRepo.save(releasedTextExercise.participations(Set.of()));
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void submitExercise_beforeDueDate_allowed() throws Exception {
        TextSubmission submission = request.putWithResponseBody("/api/exercises/" + releasedTextExercise.getId() + "/text-submissions", textSubmission, TextSubmission.class,
                HttpStatus.OK);

        assertThat(submission.getSubmissionDate()).isEqualToIgnoringNanos(ZonedDateTime.now());
        assertThat(submission.getParticipation().getInitializationState()).isEqualTo(InitializationState.FINISHED);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void submitExercise_tooLarge() throws Exception {
        char[] chars = new char[(int) (Constants.MAX_SUBMISSION_TEXT_LENGTH + 1)];
        Arrays.fill(chars, 'a');
        textSubmission.setText(new String(chars));
        request.put("/api/exercises/" + releasedTextExercise.getId() + "/text-submissions", textSubmission, HttpStatus.PAYLOAD_TOO_LARGE);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void submitExercise_beforeDueDateWithTwoSubmissions_allowed() throws Exception {
        final var submitPath = "/api/exercises/" + releasedTextExercise.getId() + "/text-submissions";
        final var newSubmissionText = "Some other test text";
        textSubmission = request.putWithResponseBody(submitPath, textSubmission, TextSubmission.class, HttpStatus.OK);
        textSubmission.setText(newSubmissionText);
        request.put(submitPath, textSubmission, HttpStatus.OK);

        final var submissionInDb = submissionRepository.findById(textSubmission.getId());
        assertThat(submissionInDb).isPresent();
        assertThat(submissionInDb.get().getText()).isEqualTo(newSubmissionText);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void submitExercise_afterDueDateWithParticipationStartAfterDueDate_allowed() throws Exception {
        lateParticipation.setInitializationDate(ZonedDateTime.now());
        participationRepository.save(lateParticipation);

        request.put("/api/exercises/" + releasedTextExercise.getId() + "/text-submissions", textSubmission, HttpStatus.OK);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void saveExercise_beforeDueDate() throws Exception {
        TextSubmission storedSubmission = request.putWithResponseBody("/api/exercises/" + releasedTextExercise.getId() + "/text-submissions", notSubmittedTextSubmission,
                TextSubmission.class, HttpStatus.OK);
        assertThat(storedSubmission.isSubmitted()).isTrue();
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void saveExercise_afterDueDateWithParticipationStartAfterDueDate() throws Exception {
        database.updateExerciseDueDate(releasedTextExercise.getId(), ZonedDateTime.now().minusHours(1));
        lateParticipation.setInitializationDate(ZonedDateTime.now());
        participationRepository.save(lateParticipation);

        TextSubmission storedSubmission = request.putWithResponseBody("/api/exercises/" + releasedTextExercise.getId() + "/text-submissions", notSubmittedTextSubmission,
                TextSubmission.class, HttpStatus.OK);
        assertThat(storedSubmission.isSubmitted()).isFalse();

    }

    @Test
    @WithMockUser(username = "student3", roles = "USER")
    void submitExercise_notStudentInCourse() throws Exception {
        request.post("/api/exercises/" + releasedTextExercise.getId() + "/text-submissions", textSubmission, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void submitExercise_submissionIsAlreadyCreated_badRequest() throws Exception {
        textSubmission = submissionRepository.save(textSubmission);
        request.post("/api/exercises/" + releasedTextExercise.getId() + "/text-submissions", textSubmission, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void submitExercise_noExercise_badRequest() throws Exception {
        var fakeExerciseId = releasedTextExercise.getId() + 100L;
        request.post("/api/exercises/" + fakeExerciseId + "/text-submissions", textSubmission, HttpStatus.NOT_FOUND);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void deleteTextSubmissionWithTextBlocks() throws Exception {
        textSubmission.setText("Lorem Ipsum dolor sit amet");
        textSubmission = database.saveTextSubmission(releasedTextExercise, textSubmission, "student1");
        final var blocks = Set.of(ModelFactory.generateTextBlock(0, 11), ModelFactory.generateTextBlock(12, 21), ModelFactory.generateTextBlock(22, 26));
        database.addAndSaveTextBlocksToTextSubmission(blocks, textSubmission);

        request.delete("/api/submissions/" + textSubmission.getId(), HttpStatus.OK);
    }

    private void checkDetailsHidden(TextSubmission submission, boolean isStudent) {
        assertThat(submission.getParticipation().getResults()).as("results are hidden in participation").isNullOrEmpty();
        if (isStudent) {
            assertThat(submission.getLatestResult()).as("result is hidden").isNull();
        }
        else {
            assertThat(((StudentParticipation) submission.getParticipation()).getStudent()).as("student of participation is hidden").isEmpty();
        }
    }
}
