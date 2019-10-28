package de.tum.in.www1.artemis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

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
    RequestUtilService request;

    @Autowired
    DatabaseUtilService database;

    private TextExercise textExercise;

    private TextSubmission textSubmission;

    @BeforeEach
    public void initTestCase() {
        database.addUsers(2, 2, 0);
        database.addCourseWithOneTextExerciseDueDateReached();
        textExercise = (TextExercise) exerciseRepo.findAll().get(0);
        textSubmission = ModelFactory.generateTextSubmission("example text", Language.ENGLISH, true);
    }

    @AfterEach
    public void tearDown() {
        database.resetDatabase();
    }

    @Test
    @WithMockUser(value = "student1")
    public void submitTextSubmission() throws Exception {
        database.addParticipationForExercise(textExercise, "student1");
        TextSubmission submission = ModelFactory.generateTextSubmission("d", Language.ENGLISH, false);
        TextSubmission returnedSubmission = request.postWithResponseBody("/api/exercises/" + textExercise.getId() + "/text-submissions", submission, TextSubmission.class,
                HttpStatus.OK);
        assertThat(returnedSubmission).as("submission is not null").isNotNull();
        assertThat(returnedSubmission).as("correct text submission was found").isEqualToComparingOnlyGivenFields(submission, "text", "language", "submitted");
        checkDetailsHidden(returnedSubmission, true);
    }

    @Test
    @WithMockUser(value = "student1")
    public void submitTextSubmission_withSubmissionId() throws Exception {
        database.addTextSubmission(textExercise, textSubmission, "student1");
        request.postWithResponseBody("/api/exercises/" + textExercise.getId() + "/text-submissions", textSubmission, TextSubmission.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(value = "student1")
    public void submitTextSubmission_exerciseIdIncorrect() throws Exception {
        database.addTextSubmission(textExercise, textSubmission, "student1");
        request.postWithResponseBody("/api/exercises/" + (textExercise.getId() + 1) + "/text-submissions", textSubmission, TextSubmission.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(value = "student1")
    public void submitTextSubmission_courseIdIncorrect() throws Exception {
        var courseId = textExercise.getCourse().getId();
        textExercise.getCourse().setId(courseId + 1);
        database.addTextSubmission(textExercise, textSubmission, "student1");
        request.postWithResponseBody("/api/exercises/" + textExercise.getId() + "/text-submissions", textSubmission, TextSubmission.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(value = "student2")
    public void submitTextSubmission_notStudentInCourse() throws Exception {
        database.addTextSubmission(textExercise, textSubmission, "student1");
        request.postWithResponseBody("/api/exercises/" + textExercise.getId() + "/text-submissions", textSubmission, TextSubmission.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(value = "student1")
    public void updateTextSubmission() throws Exception {
        var participation = database.addParticipationForExercise(textExercise, "student1");
        textSubmission.setText("dddd");
        textSubmission.setLanguage(Language.GERMAN);
        TextSubmission returnedSubmission = request.putWithResponseBody("/api/exercises/" + textExercise.getId() + "/text-submissions", textSubmission, TextSubmission.class,
                HttpStatus.OK);
        assertThat(returnedSubmission).as("submission is not null").isNotNull();
        assertThat(returnedSubmission).as("correct text submission was found").isEqualToComparingOnlyGivenFields(textSubmission, "text", "language", "submitted");
        assertThat(returnedSubmission.getParticipation()).isEqualTo(participation);
        checkDetailsHidden(returnedSubmission, true);
    }

    @Test
    @WithMockUser(value = "tutor1", roles = "TA")
    public void getAllTextSubmissions_studentHidden() throws Exception {
        textSubmission = database.addTextSubmission(textExercise, textSubmission, "student1");

        List<TextSubmission> textSubmissions = request.getList("/api/exercises/" + textExercise.getId() + "/text-submissions", HttpStatus.OK, TextSubmission.class);

        assertThat(textSubmissions.size()).as("one text submission was found").isEqualTo(1);
        checkSubmission(textSubmissions.get(0), textSubmission);

        checkDetailsHidden(textSubmissions.get(0), false);
    }

    @Test
    @WithMockUser(value = "tutor1", roles = "TA")
    public void getAllTextSubmissions_assessedByTutor() throws Exception {
        textSubmission = database.addTextSubmissionWithResultAndAssessor(textExercise, textSubmission, "student1", "tutor1");
        TextSubmission textSubmission2 = ModelFactory.generateTextSubmission("d", Language.ENGLISH, true);
        database.addTextSubmissionWithResultAndAssessor(textExercise, textSubmission2, "tutor2", "student2");
        database.updateExerciseDueDate(textExercise.getId(), ZonedDateTime.now().minusHours(1));

        LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("assessedByTutor", "true");

        List<TextSubmission> textSubmissions = request.getList("/api/exercises/" + textExercise.getId() + "/text-submissions", HttpStatus.OK, TextSubmission.class, params);

        assertThat(textSubmissions.size()).as("one text submission was found").isEqualTo(1);
        checkSubmission(textSubmissions.get(0), textSubmission);

        checkDetailsHidden(textSubmissions.get(0), false);
    }

    @Test
    @WithMockUser(value = "tutor1", roles = "TA")
    public void getAllTextSubmissions_submittedOnly() throws Exception {
        textSubmission = database.addTextSubmission(textExercise, textSubmission, "student1");
        TextSubmission textSubmission2 = ModelFactory.generateTextSubmission("d", Language.ENGLISH, false);
        database.addTextSubmission(textExercise, textSubmission2, "student2");
        database.updateExerciseDueDate(textExercise.getId(), ZonedDateTime.now().minusHours(1));

        LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("submittedOnly", "true");

        List<TextSubmission> textSubmissions = request.getList("/api/exercises/" + textExercise.getId() + "/text-submissions", HttpStatus.OK, TextSubmission.class, params);

        assertThat(textSubmissions.size()).as("one text submission was found").isEqualTo(1);
        checkSubmission(textSubmissions.get(0), textSubmission);

        checkDetailsHidden(textSubmissions.get(0), false);
    }

    @Test
    @WithMockUser(value = "tutor1", roles = "TA")
    public void getTextSubmissionWithoutAssessment_studentHidden() throws Exception {
        textSubmission = database.addTextSubmission(textExercise, textSubmission, "student1");

        TextSubmission textSubmissionWithoutAssessment = request.get("/api/exercises/" + textExercise.getId() + "/text-submission-without-assessment", HttpStatus.OK,
                TextSubmission.class);

        assertThat(textSubmissionWithoutAssessment).as("text submission without assessment was found").isNotNull();
        checkSubmission(textSubmissionWithoutAssessment, textSubmission);
        checkDetailsHidden(textSubmissionWithoutAssessment, false);
    }

    @Test
    @WithMockUser(username = "student1")
    public void getResultsForCurrentStudent_assessorHiddenForStudent() throws Exception {
        textSubmission = ModelFactory.generateTextSubmission("Some text", Language.ENGLISH, true);
        database.addTextSubmissionWithResultAndAssessor(textExercise, textSubmission, "student1", "tutor1");

        Exercise returnedExercise = request.get("/api/exercises/" + textExercise.getId() + "/results", HttpStatus.OK, Exercise.class);

        assertThat(returnedExercise.getStudentParticipations().iterator().next().getResults().iterator().next().getAssessor()).as("assessor is null").isNull();
    }

    @Test
    @WithMockUser(value = "student1", roles = "USER")
    public void getDataForTextEditorWithResult() throws Exception {
        TextSubmission textSubmission = database.addTextSubmissionWithResultAndAssessor(textExercise, this.textSubmission, "student1", "tutor1");
        Long participationId = textSubmission.getParticipation().getId();

        StudentParticipation participation = request.get("/api/text-editor/" + participationId, HttpStatus.OK, StudentParticipation.class);

        assertThat(participation.getResults(), is(notNullValue()));
        assertThat(participation.getResults(), hasSize(1));

        assertThat(participation.getSubmissions(), is(notNullValue()));
        checkSubmission((TextSubmission) participation.getSubmissions().iterator().next(), textSubmission);
    }

    private void checkDetailsHidden(TextSubmission submission, boolean isStudent) {
        assertThat(submission.getParticipation().getResults()).isNullOrEmpty();
        if (isStudent) {
            assertThat(submission.getResult()).isNull();
        }
        else {
            assertThat(((StudentParticipation) submission.getParticipation()).getStudent()).as("student of participation is hidden").isNull();
        }
    }

    private void checkSubmission(TextSubmission actualSubmission, TextSubmission expectedSubmission) {
        assertThat(actualSubmission).as("correct text submission was found").isEqualToComparingOnlyGivenFields(expectedSubmission, "id", "text", "language", "submitted");
    }
}
