package de.tum.in.www1.artemis.hestia;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.AssessmentType;
import de.tum.in.www1.artemis.domain.enumeration.FeedbackType;
import de.tum.in.www1.artemis.domain.enumeration.Visibility;
import de.tum.in.www1.artemis.domain.hestia.CodeHint;
import de.tum.in.www1.artemis.domain.hestia.ExerciseHint;
import de.tum.in.www1.artemis.domain.hestia.ExerciseHintActivation;
import de.tum.in.www1.artemis.domain.hestia.ProgrammingExerciseTask;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseStudentParticipation;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.repository.hestia.ExerciseHintActivationRepository;
import de.tum.in.www1.artemis.repository.hestia.ExerciseHintRepository;
import de.tum.in.www1.artemis.service.hestia.ProgrammingExerciseTaskService;

public class ExerciseHintIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ResultRepository resultRepository;

    @Autowired
    private ExerciseHintRepository exerciseHintRepository;

    @Autowired
    private ProgrammingExerciseRepository exerciseRepository;

    @Autowired
    private ProgrammingExerciseTaskService programmingExerciseTaskService;

    @Autowired
    private ProgrammingSubmissionRepository programmingSubmissionRepository;

    @Autowired
    private ExerciseHintActivationRepository exerciseHintActivationRepository;

    @Autowired
    private ProgrammingExerciseTestCaseRepository programmingExerciseTestCaseRepository;

    private ProgrammingExercise exercise;

    private ProgrammingExercise exerciseLite;

    private List<ExerciseHint> hints;

    private ProgrammingExerciseStudentParticipation studentParticipation;

    private int timeOffset = 0;

    @BeforeEach
    public void initTestCase() {
        database.addCourseWithOneProgrammingExerciseAndTestCases();
        database.addUsers(2, 2, 1, 2);

        programmingExerciseTestCaseRepository.saveAll(programmingExerciseTestCaseRepository.findAll().stream().peek(testCase -> testCase.setActive(true)).toList());
        exerciseLite = exerciseRepository.findAll().get(0);
        exercise = database.loadProgrammingExerciseWithEagerReferences(exerciseLite);
        database.addHintsToExercise(exercise);
        database.addTasksToProgrammingExercise(exercise);

        List<ProgrammingExerciseTask> sortedTasks = programmingExerciseTaskService.getSortedTasks(exercise);

        hints = exerciseHintRepository.findAll();
        hints.get(0).setProgrammingExerciseTask(sortedTasks.get(0));
        hints.get(1).setProgrammingExerciseTask(sortedTasks.get(1));
        hints.get(2).setProgrammingExerciseTask(sortedTasks.get(2));
        exerciseHintRepository.saveAll(hints);
    }

    @AfterEach
    public void tearDown() {
        database.resetDatabase();
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void queryAllAvailableHintsForAnExercise() throws Exception {
        var user = userRepository.getUserWithGroupsAndAuthorities();
        studentParticipation = database.addStudentParticipationForProgrammingExercise(exercise, user.getLogin());
        addResultWithFailedTestCases(exercise.getTestCases());
        addResultWithFailedTestCases(exercise.getTestCases());
        addResultWithFailedTestCases(exercise.getTestCases());

        var availableHints = request.getList("/api/programming-exercises/" + exercise.getId() + "/exercise-hints/available", HttpStatus.OK, ExerciseHint.class);
        assertThat(availableHints).hasSize(1);
        assertThat(availableHints.get(0).getContent()).isNullOrEmpty();
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void queryAllActivatedHintsForAnExercise() throws Exception {
        var user = userRepository.getUserWithGroupsAndAuthorities();
        var ueha = new ExerciseHintActivation();
        ueha.setExerciseHint(hints.get(0));
        ueha.setUser(user);
        ueha.setActivationDate(ZonedDateTime.now());
        ueha.setRating(4);
        exerciseHintActivationRepository.save(ueha);

        var availableHints = request.getList("/api/programming-exercises/" + exercise.getId() + "/exercise-hints/activated", HttpStatus.OK, ExerciseHint.class);
        assertThat(availableHints).hasSize(1);
        assertThat(availableHints.get(0).getId()).isEqualTo(hints.get(0).getId());
        assertThat(availableHints.get(0).getContent()).isEqualTo(hints.get(0).getContent());
        assertThat(availableHints.get(0).getCurrentUserRating()).isEqualTo(4);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void activateHintForAnExercise() throws Exception {
        var user = userRepository.getUserWithGroupsAndAuthorities();
        studentParticipation = database.addStudentParticipationForProgrammingExercise(exercise, user.getLogin());
        addResultWithFailedTestCases(exercise.getTestCases());
        addResultWithFailedTestCases(exercise.getTestCases());
        addResultWithFailedTestCases(exercise.getTestCases());

        var activatedHint = request.postWithResponseBody("/api/programming-exercises/" + exercise.getId() + "/exercise-hints/" + hints.get(0).getId() + "/activate", null,
                ExerciseHint.class, HttpStatus.OK);
        assertThat(activatedHint.getId()).isEqualTo(hints.get(0).getId());
        assertThat(activatedHint.getContent()).isEqualTo(hints.get(0).getContent());

        var uehas = exerciseHintActivationRepository.findAll();
        assertThat(uehas).hasSize(1);
        assertThat(uehas.get(0).getExerciseHint()).isEqualTo(hints.get(0));
        assertThat(uehas.get(0).getUser()).isEqualTo(user);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void rateActivatedHintForAnExercise() throws Exception {
        var user = userRepository.getUserWithGroupsAndAuthorities();
        var ueha = new ExerciseHintActivation();
        ueha.setExerciseHint(hints.get(0));
        ueha.setUser(user);
        ueha.setActivationDate(ZonedDateTime.now());
        exerciseHintActivationRepository.save(ueha);

        request.postWithoutLocation("/api/programming-exercises/" + exercise.getId() + "/exercise-hints/" + hints.get(0).getId() + "/rating/" + 4, null, HttpStatus.OK, null);

        ueha = exerciseHintActivationRepository.findById(ueha.getId()).orElseThrow();
        assertThat(ueha.getRating()).isEqualTo(4);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void rateActivatedHintForAnExerciseBadRequest() throws Exception {
        var user = userRepository.getUserWithGroupsAndAuthorities();
        var ueha = new ExerciseHintActivation();
        ueha.setExerciseHint(hints.get(0));
        ueha.setUser(user);
        ueha.setActivationDate(ZonedDateTime.now());
        exerciseHintActivationRepository.save(ueha);

        request.postWithoutLocation("/api/programming-exercises/" + exercise.getId() + "/exercise-hints/" + hints.get(0).getId() + "/rating/" + 100, null, HttpStatus.BAD_REQUEST,
                null);

        ueha = exerciseHintActivationRepository.findById(ueha.getId()).orElseThrow();
        assertThat(ueha.getRating()).isNull();
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void rateNotActivatedHintForAnExerciseForbidden() throws Exception {
        request.postWithoutLocation("/api/programming-exercises/" + exercise.getId() + "/exercise-hints/" + hints.get(0).getId() + "/rating/" + 4, null, HttpStatus.NOT_FOUND,
                null);
        assertThat(exerciseHintActivationRepository.findAll()).isEmpty();
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void queryAllHintsForAnExerciseAsAStudent() throws Exception {
        request.getList("/api/programming-exercises/" + exercise.getId() + "/exercise-hints", HttpStatus.FORBIDDEN, ExerciseHint.class);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void queryAllHintsForAnExerciseAsATutor() throws Exception {
        request.getList("/api/programming-exercises/" + exercise.getId() + "/exercise-hints", HttpStatus.OK, ExerciseHint.class);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void queryAllHintsForAnExerciseAsAnInstructor() throws Exception {
        request.getList("/api/programming-exercises/" + exercise.getId() + "/exercise-hints", HttpStatus.OK, ExerciseHint.class);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void getHintForAnExerciseAsStudentShouldReturnForbidden() throws Exception {
        ExerciseHint exerciseHint = exerciseHintRepository.findAll().get(0);
        request.get("/api/programming-exercises/" + exerciseHint.getExercise().getId() + "/exercise-hints/" + exerciseHint.getId(), HttpStatus.FORBIDDEN, ExerciseHint.class);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void getHintForAnExerciseAsTutor() throws Exception {
        ExerciseHint exerciseHint = exerciseHintRepository.findAll().get(0);
        request.get("/api/programming-exercises/" + exerciseHint.getExercise().getId() + "/exercise-hints/" + exerciseHint.getId(), HttpStatus.OK, ExerciseHint.class);
    }

    @Test
    @WithMockUser(username = "editor1", roles = "EDITOR")
    public void getHintForAnExerciseAsEditor() throws Exception {
        ExerciseHint exerciseHint = exerciseHintRepository.findAll().get(0);
        request.get("/api/programming-exercises/" + exerciseHint.getExercise().getId() + "/exercise-hints/" + exerciseHint.getId(), HttpStatus.OK, ExerciseHint.class);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void getHintForAnExerciseAsAnInstructor() throws Exception {
        ExerciseHint exerciseHint = exerciseHintRepository.findAll().get(0);
        request.get("/api/programming-exercises/" + exerciseHint.getExercise().getId() + "/exercise-hints/" + exerciseHint.getId(), HttpStatus.OK, ExerciseHint.class);
        request.get("/api/programming-exercises/" + exerciseHint.getExercise().getId() + "/exercise-hints/" + 0L, HttpStatus.NOT_FOUND, ExerciseHint.class);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void createHintAsAStudentShouldReturnForbidden() throws Exception {
        ExerciseHint exerciseHint = new ExerciseHint().content("content 4").title("title 4").exercise(exerciseLite);
        request.post("/api/programming-exercises/" + exercise.getId() + "/exercise-hints/", exerciseHint, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void createHintAsTutorForbidden() throws Exception {
        ExerciseHint exerciseHint = new ExerciseHint().content("content 4").title("title 4").exercise(exerciseLite);
        request.post("/api/programming-exercises/" + exerciseHint.getExercise().getId() + "/exercise-hints/", exerciseHint, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = "editor1", roles = "EDITOR")
    public void createHintAsEditor() throws Exception {
        ExerciseHint exerciseHint = new ExerciseHint().content("content 4").title("title 4").exercise(exerciseLite);
        request.post("/api/programming-exercises/" + exerciseHint.getExercise().getId() + "/exercise-hints/", exerciseHint, HttpStatus.CREATED);

        List<ExerciseHint> exerciseHints = exerciseHintRepository.findAll();
        assertThat(exerciseHints).hasSize(4);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void createHintAsInstructor() throws Exception {
        ExerciseHint exerciseHint = new ExerciseHint().content("content 4").title("title 4").exercise(exerciseLite);

        request.post("/api/programming-exercises/" + exercise.getId() + "/exercise-hints/", exerciseHint, HttpStatus.CREATED);
        List<ExerciseHint> exerciseHints = exerciseHintRepository.findAll();
        assertThat(exerciseHints).hasSize(4);

        exerciseHint.setExercise(null);
        request.post("/api/programming-exercises/" + exercise.getId() + "/exercise-hints/", exerciseHint, HttpStatus.CONFLICT);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void createCodeHintShouldFail() throws Exception {
        CodeHint codeHint = new CodeHint();
        codeHint.setTitle("Hint 1");
        codeHint.setExercise(exercise);

        int sizeBefore = exerciseHintRepository.findAll().size();
        request.post("/api/programming-exercises/" + codeHint.getExercise().getId() + "/exercise-hints/", codeHint, HttpStatus.BAD_REQUEST);
        int sizeAfter = exerciseHintRepository.findAll().size();
        assertThat(sizeAfter).isEqualTo(sizeBefore);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void updateHintAsAStudentShouldReturnForbidden() throws Exception {
        updateHintForbidden();
    }

    private void updateHintForbidden() throws Exception {
        ExerciseHint exerciseHint = exerciseHintRepository.findAll().get(0);
        String newContent = "new content value!";
        String contentBefore = exerciseHint.getContent();
        exerciseHint.setContent(newContent);
        request.put("/api/programming-exercises/" + exerciseHint.getExercise().getId() + "/exercise-hints/" + exerciseHint.getId(), exerciseHint, HttpStatus.FORBIDDEN);

        Optional<ExerciseHint> hintAfterSave = exerciseHintRepository.findById(exerciseHint.getId());
        assertThat(hintAfterSave).isPresent();
        assertThat(hintAfterSave.get()).isInstanceOf(ExerciseHint.class);
        assertThat((hintAfterSave.get()).getContent()).isEqualTo(contentBefore);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void updateHintAsTutorForbidden() throws Exception {
        updateHintForbidden();
    }

    @Test
    @WithMockUser(username = "editor1", roles = "EDITOR")
    public void updateHintAsEditor() throws Exception {
        ExerciseHint exerciseHint = exerciseHintRepository.findAll().get(0);
        String newContent = "new content value!";
        exerciseHint.setContent(newContent);
        request.put("/api/programming-exercises/" + exerciseHint.getExercise().getId() + "/exercise-hints/" + exerciseHint.getId(), exerciseHint, HttpStatus.OK);
        Optional<ExerciseHint> hintAfterSave = exerciseHintRepository.findById(exerciseHint.getId());
        assertThat(hintAfterSave).isPresent();
        assertThat(hintAfterSave.get()).isInstanceOf(ExerciseHint.class);
        assertThat((hintAfterSave.get()).getContent()).isEqualTo(newContent);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void updateHintAsInstructor() throws Exception {
        ExerciseHint exerciseHint = exerciseHintRepository.findAll().get(0);
        String newContent = "new content value!";

        exerciseHint.setContent(newContent);
        request.put("/api/programming-exercises/" + exerciseHint.getExercise().getId() + "/exercise-hints/" + exerciseHint.getId(), exerciseHint, HttpStatus.OK);

        Optional<ExerciseHint> hintAfterSave = exerciseHintRepository.findById(exerciseHint.getId());
        assertThat(hintAfterSave).isPresent();
        assertThat(hintAfterSave.get()).isInstanceOf(ExerciseHint.class);
        assertThat((hintAfterSave.get()).getContent()).isEqualTo(newContent);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void updateCodeHintTitle() throws Exception {
        CodeHint codeHint = new CodeHint();
        codeHint.setTitle("Hint 1");
        codeHint.setExercise(exerciseLite);
        exerciseHintRepository.save(codeHint);

        codeHint.setTitle("New Title");

        request.put("/api/programming-exercises/" + codeHint.getExercise().getId() + "/exercise-hints/" + codeHint.getId(), codeHint, HttpStatus.OK);
        Optional<ExerciseHint> hintAfterSave = exerciseHintRepository.findById(codeHint.getId());
        assertThat(hintAfterSave).isPresent();
        assertThat(hintAfterSave.get()).isInstanceOf(CodeHint.class);
        assertThat((hintAfterSave.get()).getTitle()).isEqualTo("New Title");
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void deleteHintAsInstructor() throws Exception {
        ExerciseHint exerciseHint = new ExerciseHint().content("content 4").title("title 4").exercise(exerciseLite);
        request.delete("/api/programming-exercises/" + exerciseHint.getExercise().getId() + "/exercise-hints/" + 0L, HttpStatus.NOT_FOUND);
        request.post("/api/programming-exercises/" + exerciseHint.getExercise().getId() + "/exercise-hints", exerciseHint, HttpStatus.CREATED);
        List<ExerciseHint> exerciseHints = exerciseHintRepository.findAll();
        request.delete("/api/programming-exercises/" + exerciseHint.getExercise().getId() + "/exercise-hints/" + exerciseHints.get(0).getId(), HttpStatus.NO_CONTENT);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void deleteCodeHintAsInstructor() throws Exception {
        CodeHint codeHint = new CodeHint();
        codeHint.setTitle("Hint 1");
        codeHint.setExercise(exercise);
        exerciseHintRepository.save(codeHint);

        request.delete("/api/programming-exercises/" + codeHint.getExercise().getId() + "/exercise-hints/" + codeHint.getId(), HttpStatus.NO_CONTENT);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testGetHintTitleAsInstructor() throws Exception {
        // Only user and role matter, so we can re-use the logic
        testGetHintTitle();
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void testGetHintTitleAsTeachingAssistant() throws Exception {
        // Only user and role matter, so we can re-use the logic
        testGetHintTitle();
    }

    @Test
    @WithMockUser(username = "user1", roles = "USER")
    public void testGetHintTitleAsUser() throws Exception {
        // Only user and role matter, so we can re-use the logic
        testGetHintTitle();
    }

    private void testGetHintTitle() throws Exception {
        final var hint = new ExerciseHint().title("Test Hint").exercise(exerciseLite);
        exerciseHintRepository.save(hint);

        final var title = request.get("/api/programming-exercises/" + hint.getExercise().getId() + "/exercise-hints/" + hint.getId() + "/title", HttpStatus.OK, String.class);
        assertThat(title).isEqualTo(hint.getTitle());
    }

    @Test
    @WithMockUser(username = "user1", roles = "USER")
    public void testGetHintTitleForNonExistingHint() throws Exception {
        request.get("/api/programming-exercises/" + exercise.getId() + "/exercise-hints/12312312321/title", HttpStatus.NOT_FOUND, String.class);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void createHintWithInvalidExerciseIds() throws Exception {
        Course course = database.addCourseWithOneProgrammingExercise();
        var unrelatedExercise = course.getExercises().stream().findFirst().orElseThrow();

        ExerciseHint exerciseHint = new ExerciseHint();
        exerciseHint.setTitle("Test Title");
        exerciseHint.setExercise(exerciseLite);

        request.post("/api/programming-exercises/" + unrelatedExercise.getId() + "/exercise-hints", exerciseHint, HttpStatus.CONFLICT);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void updateHintWithInvalidExerciseIds() throws Exception {
        Course course = database.addCourseWithOneProgrammingExercise();
        var unrelatedExercise = course.getExercises().stream().findFirst().orElseThrow();

        ExerciseHint exerciseHint = exerciseHintRepository.findAll().get(0);
        exerciseHint.setTitle("New Title");

        request.put("/api/programming-exercises/" + unrelatedExercise.getId() + "/exercise-hints/" + exerciseHint.getId(), exerciseHint, HttpStatus.CONFLICT);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void getHintTitleWithInvalidExerciseIds() throws Exception {
        Course course = database.addCourseWithOneProgrammingExercise();
        var unrelatedExercise = course.getExercises().stream().findFirst().orElseThrow();

        ExerciseHint exerciseHint = exerciseHintRepository.findAll().get(0);

        request.get("/api/programming-exercises/" + unrelatedExercise.getId() + "/exercise-hints/" + exerciseHint.getId(), HttpStatus.CONFLICT, String.class);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void getExerciseHintWithInvalidExerciseIds() throws Exception {
        Course course = database.addCourseWithOneProgrammingExercise();
        var unrelatedExercise = course.getExercises().stream().findFirst().orElseThrow();

        ExerciseHint exerciseHint = exerciseHintRepository.findAll().get(0);

        request.get("/api/programming-exercises/" + unrelatedExercise.getId() + "/exercise-hints/" + exerciseHint.getId(), HttpStatus.CONFLICT, String.class);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void deleteHintWithInvalidExerciseIds() throws Exception {
        Course course = database.addCourseWithOneProgrammingExercise();
        var unrelatedExercise = course.getExercises().stream().findFirst().orElseThrow();

        ExerciseHint exerciseHint = exerciseHintRepository.findAll().get(0);

        request.delete("/api/programming-exercises/" + unrelatedExercise.getId() + "/exercise-hints/" + exerciseHint.getId(), HttpStatus.CONFLICT);
    }

    private void addResultWithFailedTestCases(Collection<ProgrammingExerciseTestCase> failedTestCases) {
        var successfulTestCases = new ArrayList<>(exercise.getTestCases());
        successfulTestCases.removeAll(failedTestCases);
        addResultWithSuccessfulTestCases(successfulTestCases);
    }

    private void addResultWithSuccessfulTestCases(Collection<ProgrammingExerciseTestCase> successfulTestCases) {
        var submission = database.createProgrammingSubmission(studentParticipation, false);
        Result result = new Result().participation(submission.getParticipation()).assessmentType(AssessmentType.AUTOMATIC).score(0D).rated(true)
                .completionDate(ZonedDateTime.now().plusSeconds(timeOffset++));
        result = resultRepository.save(result);
        result.setSubmission(submission);
        submission.addResult(result);
        programmingSubmissionRepository.save(submission);

        for (ProgrammingExerciseTestCase testCase : exercise.getTestCases()) {
            var feedback = new Feedback();
            feedback.setPositive(successfulTestCases.contains(testCase));
            feedback.setText(testCase.getTestName());
            feedback.setVisibility(Visibility.ALWAYS);
            feedback.setType(FeedbackType.AUTOMATIC);
            database.addFeedbackToResult(feedback, result);
        }
    }
}
