package de.tum.in.www1.artemis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

import java.time.ZonedDateTime;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.util.LinkedMultiValueMap;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.Language;
import de.tum.in.www1.artemis.repository.ExerciseRepository;
import de.tum.in.www1.artemis.repository.StudentParticipationRepository;
import de.tum.in.www1.artemis.repository.TextSubmissionRepository;
import de.tum.in.www1.artemis.util.DatabaseUtilService;
import de.tum.in.www1.artemis.util.ModelFactory;
import de.tum.in.www1.artemis.util.RequestUtilService;

@ExtendWith(SpringExtension.class)
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureTestDatabase
@ActiveProfiles("artemis")
public class TextSubmissionIntegrationTest {

    @Autowired
    ExerciseRepository exerciseRepo;

    @Autowired
    TextSubmissionRepository submissionRepository;

    @Autowired
    StudentParticipationRepository participationRepository;

    @Autowired
    RequestUtilService request;

    @Autowired
    DatabaseUtilService database;

    private TextExercise textExerciseAfterDueDate;

    private TextExercise textExerciseBeforeDueDate;

    private TextSubmission textSubmission;

    private StudentParticipation afterDueDateParticipation;

    private User student;

    @BeforeEach
    public void initTestCase() {
        student = database.addUsers(2, 2, 1).get(0);
        database.addCourseWithOneTextExerciseDueDateReached();
        textExerciseBeforeDueDate = (TextExercise) database.addCourseWithOneTextExercise().getExercises().iterator().next();
        textExerciseAfterDueDate = (TextExercise) exerciseRepo.findAll().get(0);
        afterDueDateParticipation = database.addParticipationForExercise(textExerciseAfterDueDate, student.getLogin());
        afterDueDateParticipation.setInitializationDate(ZonedDateTime.now().minusDays(2));
        participationRepository.save(afterDueDateParticipation);
        database.addParticipationForExercise(textExerciseBeforeDueDate, student.getLogin());

        textSubmission = ModelFactory.generateTextSubmission("example text", Language.ENGLISH, true);
    }

    @AfterEach
    public void tearDown() {
        database.resetDatabase();
    }

    @Test
    @WithMockUser(value = "student1")
    public void submitTextSubmission() throws Exception {
        database.addParticipationForExercise(textExerciseBeforeDueDate, "student1");
        TextSubmission submission = ModelFactory.generateTextSubmission("d", Language.ENGLISH, false);
        TextSubmission returnedSubmission = request.postWithResponseBody("/api/exercises/" + textExerciseBeforeDueDate.getId() + "/text-submissions", submission,
                TextSubmission.class, HttpStatus.OK);
        assertThat(returnedSubmission).as("submission is not null").isNotNull();
        assertThat(returnedSubmission).as("correct text submission was found").isEqualToComparingOnlyGivenFields(submission, "text", "language", "submitted");
        checkDetailsHidden(returnedSubmission, true);
        var textSubmissionInDb = submissionRepository.findById(returnedSubmission.getId()).get();
        assertThat(textSubmissionInDb).isEqualToIgnoringGivenFields(returnedSubmission, "result", "participation", "submissionDate", "blocks");
    }

    @Test
    @WithMockUser(value = "student1")
    public void submitTextSubmission_withSubmissionId() throws Exception {
        database.addTextSubmission(textExerciseBeforeDueDate, textSubmission, "student1");
        request.postWithResponseBody("/api/exercises/" + textExerciseBeforeDueDate.getId() + "/text-submissions", textSubmission, TextSubmission.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(value = "student1")
    public void submitTextSubmission_exerciseIdIncorrect() throws Exception {
        database.addTextSubmission(textExerciseBeforeDueDate, textSubmission, "student1");
        request.postWithResponseBody("/api/exercises/" + (textExerciseBeforeDueDate.getId() + 1) + "/text-submissions", textSubmission, TextSubmission.class,
                HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(value = "student1")
    public void submitTextSubmission_courseIdIncorrect() throws Exception {
        var courseId = textExerciseBeforeDueDate.getCourse().getId();
        textExerciseBeforeDueDate.getCourse().setId(courseId + 1);
        database.addTextSubmission(textExerciseBeforeDueDate, textSubmission, "student1");
        request.postWithResponseBody("/api/exercises/" + textExerciseBeforeDueDate.getId() + "/text-submissions", textSubmission, TextSubmission.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(value = "student2")
    public void submitTextSubmission_notStudentInCourse() throws Exception {
        database.addTextSubmission(textExerciseBeforeDueDate, textSubmission, "student1");
        request.postWithResponseBody("/api/exercises/" + textExerciseBeforeDueDate.getId() + "/text-submissions", textSubmission, TextSubmission.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(value = "student1", roles = "USER")
    public void submitTextSubmission_afterDueDate_forbidden() throws Exception {
        request.put("/api/exercises/" + textExerciseAfterDueDate.getId() + "/text-submissions", textSubmission, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(value = "student1", roles = "USER")
    public void submitTextSubmission_beforeDueDate_allowed() throws Exception {
        request.put("/api/exercises/" + textExerciseBeforeDueDate.getId() + "/text-submissions", textSubmission, HttpStatus.OK);
    }

    @Test
    @WithMockUser(value = "student1", roles = "USER")
    public void submitTextSubmission_beforeDueDateWithTwoSubmissions_allowed() throws Exception {
        final var submitPath = "/api/exercises/" + textExerciseBeforeDueDate.getId() + "/text-submissions";
        final var newSubmissionText = "Some other test text";
        textSubmission = request.putWithResponseBody(submitPath, textSubmission, TextSubmission.class, HttpStatus.OK);
        textSubmission.setText(newSubmissionText);
        request.put(submitPath, textSubmission, HttpStatus.OK);

        final var submissionInDb = submissionRepository.findById(textSubmission.getId());
        assertThat(submissionInDb.isPresent());
        assertThat(submissionInDb.get().getText()).isEqualTo(newSubmissionText);
        var textSubmissionInDb = submissionRepository.findById(textSubmission.getId()).get();
        assertThat(textSubmissionInDb).isEqualToIgnoringGivenFields(textSubmission, "result", "participation", "submissionDate", "blocks");
    }

    @Test
    @WithMockUser(value = "student1", roles = "USER")
    public void submitTextSubmission_afterDueDateWithParticipationStartAfterDueDate_allowed() throws Exception {
        afterDueDateParticipation.setInitializationDate(ZonedDateTime.now());
        participationRepository.save(afterDueDateParticipation);

        request.put("/api/exercises/" + textExerciseBeforeDueDate.getId() + "/text-submissions", textSubmission, HttpStatus.OK);
    }

    @Test
    @WithMockUser(value = "student1")
    public void updateTextSubmission() throws Exception {
        var participation = database.addParticipationForExercise(textExerciseBeforeDueDate, "student1");
        textSubmission.setText("dddd");
        textSubmission.setLanguage(Language.GERMAN);
        TextSubmission returnedSubmission = request.putWithResponseBody("/api/exercises/" + textExerciseBeforeDueDate.getId() + "/text-submissions", textSubmission,
                TextSubmission.class, HttpStatus.OK);
        assertThat(returnedSubmission).as("submission is not null").isNotNull();
        assertThat(returnedSubmission).as("correct text submission was found").isEqualToComparingOnlyGivenFields(textSubmission, "text", "language", "submitted");
        assertThat(returnedSubmission.getParticipation()).isEqualTo(participation);
        checkDetailsHidden(returnedSubmission, true);
        var textSubmissionInDb = submissionRepository.findById(returnedSubmission.getId()).get();
        assertThat(textSubmissionInDb).isEqualToIgnoringGivenFields(returnedSubmission, "result", "participation", "submissionDate", "blocks");
    }

    @Test
    @WithMockUser(value = "tutor1", roles = "TA")
    public void getAllTextSubmissions_studentHiddenForTutor() throws Exception {
        textSubmission = database.addTextSubmission(textExerciseAfterDueDate, textSubmission, "student1");

        List<TextSubmission> textSubmissions = request.getList("/api/exercises/" + textExerciseAfterDueDate.getId() + "/text-submissions", HttpStatus.OK, TextSubmission.class);

        assertThat(textSubmissions.size()).as("one text submission was found").isEqualTo(1);
        checkSubmission(textSubmissions.get(0), textSubmission);

        checkDetailsHidden(textSubmissions.get(0), false);

        var textSubmissionInDb = submissionRepository.findById(textSubmission.getId()).get();
        assertThat(textSubmissionInDb).isEqualToIgnoringGivenFields(textSubmission, "participation", "submissionDate", "blocks");
    }

    @Test
    @WithMockUser(value = "tutor1", roles = "TA")
    public void getAllTextSubmissions_assessedByTutor() throws Exception {
        textSubmission = database.addTextSubmissionWithResultAndAssessor(textExerciseBeforeDueDate, textSubmission, "student1", "tutor1");
        TextSubmission textSubmission2 = ModelFactory.generateTextSubmission("d", Language.ENGLISH, true);
        database.addTextSubmissionWithResultAndAssessor(textExerciseBeforeDueDate, textSubmission2, "tutor2", "student2");
        database.updateExerciseDueDate(textExerciseBeforeDueDate.getId(), ZonedDateTime.now().minusHours(1));

        LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("assessedByTutor", "true");

        List<TextSubmission> textSubmissions = request.getList("/api/exercises/" + textExerciseBeforeDueDate.getId() + "/text-submissions", HttpStatus.OK, TextSubmission.class,
                params);

        assertThat(textSubmissions.size()).as("one text submission was found").isEqualTo(1);
        checkSubmission(textSubmissions.get(0), textSubmission);

        checkDetailsHidden(textSubmissions.get(0), false);
    }

    @Test
    @WithMockUser(value = "tutor1", roles = "TA")
    public void getAllTextSubmissions_submittedOnly() throws Exception {
        textSubmission = database.addTextSubmission(textExerciseBeforeDueDate, textSubmission, "student1");
        TextSubmission textSubmission2 = ModelFactory.generateTextSubmission("d", Language.ENGLISH, false);
        database.addTextSubmission(textExerciseBeforeDueDate, textSubmission2, "student2");
        database.updateExerciseDueDate(textExerciseBeforeDueDate.getId(), ZonedDateTime.now().minusHours(1));

        LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("submittedOnly", "true");

        List<TextSubmission> textSubmissions = request.getList("/api/exercises/" + textExerciseBeforeDueDate.getId() + "/text-submissions", HttpStatus.OK, TextSubmission.class,
                params);

        assertThat(textSubmissions.size()).as("one text submission was found").isEqualTo(1);
        checkSubmission(textSubmissions.get(0), textSubmission);

        checkDetailsHidden(textSubmissions.get(0), false);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void getAllTextSubmissions_studentVisibleForInstructor() throws Exception {
        textSubmission = database.addTextSubmission(textExerciseAfterDueDate, textSubmission, "student1");

        List<TextSubmission> textSubmissions = request.getList("/api/exercises/" + textExerciseAfterDueDate.getId() + "/text-submissions", HttpStatus.OK, TextSubmission.class);

        assertThat(textSubmissions.size()).as("one text submission was found").isEqualTo(1);
        assertThat(textSubmissions.get(0).getId()).as("correct text submission was found").isEqualTo(textSubmission.getId());
        assertThat(((StudentParticipation) textSubmissions.get(0).getParticipation()).getStudent()).as("student of participation is hidden").isNotNull();
    }

    @Test
    @WithMockUser(value = "tutor1", roles = "TA")
    public void getTextSubmissionWithoutAssessment_studentHidden() throws Exception {
        textSubmission = database.addTextSubmission(textExerciseAfterDueDate, textSubmission, "student1");

        TextSubmission textSubmissionWithoutAssessment = request.get("/api/exercises/" + textExerciseAfterDueDate.getId() + "/text-submission-without-assessment", HttpStatus.OK,
                TextSubmission.class);

        assertThat(textSubmissionWithoutAssessment).as("text submission without assessment was found").isNotNull();
        checkSubmission(textSubmissionWithoutAssessment, textSubmission);
        checkDetailsHidden(textSubmissionWithoutAssessment, false);
    }

    @Test
    @WithMockUser(username = "student1")
    public void getResultsForCurrentStudent_assessorHiddenForStudent() throws Exception {
        textSubmission = ModelFactory.generateTextSubmission("Some text", Language.ENGLISH, true);
        database.addTextSubmissionWithResultAndAssessor(textExerciseAfterDueDate, textSubmission, "student1", "tutor1");

        Exercise returnedExercise = request.get("/api/exercises/" + textExerciseAfterDueDate.getId() + "/results", HttpStatus.OK, Exercise.class);

        assertThat(returnedExercise.getStudentParticipations().iterator().next().getResults().iterator().next().getAssessor()).as("assessor is null").isNull();
    }

    @Test
    @WithMockUser(value = "student1", roles = "USER")
    public void getDataForTextEditorWithResult() throws Exception {
        TextSubmission textSubmission = database.addTextSubmissionWithResultAndAssessor(textExerciseAfterDueDate, this.textSubmission, "student1", "tutor1");
        Long participationId = textSubmission.getParticipation().getId();

        StudentParticipation participation = request.get("/api/text-editor/" + participationId, HttpStatus.OK, StudentParticipation.class);

        assertThat(participation.getResults(), is(notNullValue()));
        assertThat(participation.getResults(), hasSize(1));

        assertThat(participation.getSubmissions(), is(notNullValue()));
        checkSubmission((TextSubmission) participation.getSubmissions().iterator().next(), textSubmission);
    }

    @Test
    @WithMockUser(value = "tutor1", roles = "TA")
    public void getParticipationForTextExerciseWithoutAssessment_studentHidden() throws Exception {
        TextSubmission textSubmission = ModelFactory.generateTextSubmission("example text", Language.ENGLISH, true);
        database.addTextSubmission(textExerciseAfterDueDate, textSubmission, "student1");

        LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("lock", "true");

        TextSubmission submissionWithoutAssessment = request.get("/api/exercises/" + textExerciseAfterDueDate.getId() + "/text-submission-without-assessment", HttpStatus.OK,
                TextSubmission.class, params);

        assertThat(submissionWithoutAssessment).as("submission without assessment was found").isNotNull();
        checkDetailsHidden(submissionWithoutAssessment, false);
    }

    private void checkDetailsHidden(TextSubmission submission, boolean isStudent) {
        assertThat(submission.getParticipation().getResults()).as("results are hidden in participation").isNullOrEmpty();
        if (isStudent) {
            assertThat(submission.getResult()).as("result is hidden").isNull();
        }
        else {
            assertThat(((StudentParticipation) submission.getParticipation()).getStudent()).as("student of participation is hidden").isNull();
        }
    }

    private void checkSubmission(TextSubmission actualSubmission, TextSubmission expectedSubmission) {
        assertThat(actualSubmission).as("correct text submission was found").isEqualToComparingOnlyGivenFields(expectedSubmission, "id", "text", "language", "submitted");
    }
}
