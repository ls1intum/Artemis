package de.tum.cit.aet.artemis.programming.hestia;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.assessment.domain.Feedback;
import de.tum.cit.aet.artemis.assessment.domain.FeedbackType;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.assessment.domain.Visibility;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationUtilService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseTestCase;
import de.tum.cit.aet.artemis.programming.domain.hestia.CodeHint;
import de.tum.cit.aet.artemis.programming.domain.hestia.ExerciseHint;
import de.tum.cit.aet.artemis.programming.domain.hestia.ExerciseHintActivation;
import de.tum.cit.aet.artemis.programming.domain.hestia.ProgrammingExerciseTask;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseTestCaseRepository;
import de.tum.cit.aet.artemis.programming.repository.hestia.ExerciseHintActivationRepository;
import de.tum.cit.aet.artemis.programming.repository.hestia.ExerciseHintRepository;
import de.tum.cit.aet.artemis.programming.service.hestia.ProgrammingExerciseTaskService;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseUtilService;
import de.tum.cit.aet.artemis.programming.util.ProgrammingSubmissionTestRepository;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

class ExerciseHintIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "exercisehintintegration";

    @Autowired
    private ExerciseHintRepository exerciseHintRepository;

    @Autowired
    private ProgrammingExerciseRepository exerciseRepository;

    @Autowired
    private ProgrammingExerciseTaskService programmingExerciseTaskService;

    @Autowired
    private ProgrammingSubmissionTestRepository programmingSubmissionRepository;

    @Autowired
    private ExerciseHintActivationRepository exerciseHintActivationRepository;

    @Autowired
    private ProgrammingExerciseTestCaseRepository programmingExerciseTestCaseRepository;

    @Autowired
    private ProgrammingExerciseUtilService programmingExerciseUtilService;

    @Autowired
    private ParticipationUtilService participationUtilService;

    private ProgrammingExercise exercise;

    private ProgrammingExercise exerciseLite;

    private List<ExerciseHint> hints;

    private ExerciseHint exerciseHint;

    private ProgrammingExerciseStudentParticipation studentParticipation;

    private int timeOffset = 0;

    @BeforeEach
    void initTestCase() {
        final Course course = programmingExerciseUtilService.addCourseWithOneProgrammingExerciseAndTestCases();
        final ProgrammingExercise programmingExercise = exerciseUtilService.getFirstExerciseWithType(course, ProgrammingExercise.class);

        userUtilService.addUsers(TEST_PREFIX, 2, 2, 1, 2);

        programmingExerciseTestCaseRepository
                .saveAll(programmingExerciseTestCaseRepository.findByExerciseId(programmingExercise.getId()).stream().peek(testCase -> testCase.setActive(true)).toList());
        exerciseLite = exerciseRepository.findByIdElseThrow(programmingExercise.getId());
        exercise = programmingExerciseUtilService.loadProgrammingExerciseWithEagerReferences(exerciseLite);
        programmingExerciseUtilService.addHintsToExercise(exercise);
        programmingExerciseUtilService.addTasksToProgrammingExercise(exercise);

        List<ProgrammingExerciseTask> sortedTasks = programmingExerciseTaskService.getSortedTasks(exercise);

        hints = new ArrayList<>(exerciseHintRepository.findByExerciseId(exerciseLite.getId()));
        exerciseHint = hints.getFirst();
        exerciseHint.setProgrammingExerciseTask(sortedTasks.getFirst());
        hints.get(1).setProgrammingExerciseTask(sortedTasks.get(1));
        hints.get(2).setProgrammingExerciseTask(sortedTasks.get(2));
        exerciseHintRepository.saveAll(hints);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void queryAllAvailableHintsForAnExercise() throws Exception {
        var user = userRepository.getUserWithGroupsAndAuthorities();
        studentParticipation = participationUtilService.addStudentParticipationForProgrammingExercise(exercise, user.getLogin());
        addResultWithFailedTestCases(exercise.getTestCases());
        addResultWithFailedTestCases(exercise.getTestCases());
        addResultWithFailedTestCases(exercise.getTestCases());

        var availableHints = request.getList("/api/programming-exercises/" + exercise.getId() + "/exercise-hints/available", HttpStatus.OK, ExerciseHint.class);
        assertThat(availableHints).hasSize(1);
        assertThat(availableHints.getFirst().getContent()).isNullOrEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void queryAllActivatedHintsForAnExercise() throws Exception {
        var user = userRepository.getUserWithGroupsAndAuthorities();
        var ueha = new ExerciseHintActivation();
        ueha.setExerciseHint(exerciseHint);
        ueha.setUser(user);
        ueha.setActivationDate(ZonedDateTime.now());
        ueha.setRating(4);
        exerciseHintActivationRepository.save(ueha);

        var availableHints = request.getList("/api/programming-exercises/" + exercise.getId() + "/exercise-hints/activated", HttpStatus.OK, ExerciseHint.class);
        assertThat(availableHints).hasSize(1);
        assertThat(availableHints.getFirst().getId()).isEqualTo(exerciseHint.getId());
        assertThat(availableHints.getFirst().getContent()).isEqualTo(exerciseHint.getContent());
        assertThat(availableHints.getFirst().getCurrentUserRating()).isEqualTo(4);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void activateHintForAnExercise() throws Exception {
        var user = userRepository.getUserWithGroupsAndAuthorities();
        studentParticipation = participationUtilService.addStudentParticipationForProgrammingExercise(exercise, user.getLogin());
        addResultWithFailedTestCases(exercise.getTestCases());
        addResultWithFailedTestCases(exercise.getTestCases());
        addResultWithFailedTestCases(exercise.getTestCases());

        var activatedHint = request.postWithResponseBody("/api/programming-exercises/" + exercise.getId() + "/exercise-hints/" + exerciseHint.getId() + "/activate", null,
                ExerciseHint.class, HttpStatus.OK);
        assertThat(activatedHint.getId()).isEqualTo(exerciseHint.getId());
        assertThat(activatedHint.getContent()).isEqualTo(exerciseHint.getContent());

        var uehas = exerciseHintActivationRepository.findByExerciseAndUserWithExerciseHintRelations(exercise.getId(), user.getId());
        assertThat(uehas).hasSize(1);
        assertThat(uehas.stream().findFirst().get().getExerciseHint().getId()).isEqualTo(exerciseHint.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void rateActivatedHintForAnExercise() throws Exception {
        var user = userRepository.getUserWithGroupsAndAuthorities();
        var ueha = new ExerciseHintActivation();
        ueha.setExerciseHint(exerciseHint);
        ueha.setUser(user);
        ueha.setActivationDate(ZonedDateTime.now());
        exerciseHintActivationRepository.save(ueha);

        request.postWithoutLocation("/api/programming-exercises/" + exercise.getId() + "/exercise-hints/" + exerciseHint.getId() + "/rating/" + 4, null, HttpStatus.OK, null);

        ueha = exerciseHintActivationRepository.findById(ueha.getId()).orElseThrow();
        assertThat(ueha.getRating()).isEqualTo(4);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void rateActivatedHintForAnExerciseBadRequest() throws Exception {
        var user = userRepository.getUserWithGroupsAndAuthorities();
        var ueha = new ExerciseHintActivation();
        ueha.setExerciseHint(exerciseHint);
        ueha.setUser(user);
        ueha.setActivationDate(ZonedDateTime.now());
        exerciseHintActivationRepository.save(ueha);

        request.postWithoutLocation("/api/programming-exercises/" + exercise.getId() + "/exercise-hints/" + exerciseHint.getId() + "/rating/" + 100, null, HttpStatus.BAD_REQUEST,
                null);

        ueha = exerciseHintActivationRepository.findById(ueha.getId()).orElseThrow();
        assertThat(ueha.getRating()).isNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void rateNotActivatedHintForAnExerciseForbidden() throws Exception {
        long sizeBefore = exerciseHintActivationRepository.count();
        request.postWithoutLocation("/api/programming-exercises/" + exercise.getId() + "/exercise-hints/" + exerciseHint.getId() + "/rating/" + 4, null, HttpStatus.NOT_FOUND,
                null);
        assertThat(exerciseHintActivationRepository.count()).isEqualTo(sizeBefore);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void queryAllHintsForAnExerciseAsAStudent() throws Exception {
        request.getList("/api/programming-exercises/" + exercise.getId() + "/exercise-hints", HttpStatus.FORBIDDEN, ExerciseHint.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void queryAllHintsForAnExerciseAsATutor() throws Exception {
        request.getList("/api/programming-exercises/" + exercise.getId() + "/exercise-hints", HttpStatus.OK, ExerciseHint.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void queryAllHintsForAnExerciseAsAnInstructor() throws Exception {
        request.getList("/api/programming-exercises/" + exercise.getId() + "/exercise-hints", HttpStatus.OK, ExerciseHint.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void getHintForAnExerciseAsStudentShouldReturnForbidden() throws Exception {
        request.get("/api/programming-exercises/" + exerciseHint.getExercise().getId() + "/exercise-hints/" + exerciseHint.getId(), HttpStatus.FORBIDDEN, ExerciseHint.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void getHintForAnExerciseAsTutor() throws Exception {
        request.get("/api/programming-exercises/" + exerciseHint.getExercise().getId() + "/exercise-hints/" + exerciseHint.getId(), HttpStatus.OK, ExerciseHint.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void getHintForAnExerciseAsEditor() throws Exception {
        request.get("/api/programming-exercises/" + exerciseHint.getExercise().getId() + "/exercise-hints/" + exerciseHint.getId(), HttpStatus.OK, ExerciseHint.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void getHintForAnExerciseAsAnInstructor() throws Exception {
        request.get("/api/programming-exercises/" + exerciseHint.getExercise().getId() + "/exercise-hints/" + exerciseHint.getId(), HttpStatus.OK, ExerciseHint.class);
        request.get("/api/programming-exercises/" + exerciseHint.getExercise().getId() + "/exercise-hints/" + 0L, HttpStatus.NOT_FOUND, ExerciseHint.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void createHintAsAStudentShouldReturnForbidden() throws Exception {
        ExerciseHint exerciseHint = new ExerciseHint().content("content 4").title("title 4").exercise(exerciseLite);
        request.post("/api/programming-exercises/" + exercise.getId() + "/exercise-hints", exerciseHint, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void createHintAsTutorForbidden() throws Exception {
        ExerciseHint exerciseHint = new ExerciseHint().content("content 4").title("title 4").exercise(exerciseLite);
        request.post("/api/programming-exercises/" + exerciseHint.getExercise().getId() + "/exercise-hints", exerciseHint, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void createHintAsEditor() throws Exception {
        ExerciseHint exerciseHint = new ExerciseHint().content("content 4").title("title 4").exercise(exerciseLite);
        request.post("/api/programming-exercises/" + exerciseHint.getExercise().getId() + "/exercise-hints", exerciseHint, HttpStatus.CREATED);

        Set<ExerciseHint> exerciseHints = exerciseHintRepository.findByExerciseId(exerciseLite.getId());
        assertThat(exerciseHints).hasSize(4);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createHintAsInstructor() throws Exception {
        ExerciseHint exerciseHint = new ExerciseHint().content("content 4").title("title 4").exercise(exerciseLite);

        request.post("/api/programming-exercises/" + exercise.getId() + "/exercise-hints", exerciseHint, HttpStatus.CREATED);
        Set<ExerciseHint> exerciseHints = exerciseHintRepository.findByExerciseId(exerciseLite.getId());
        assertThat(exerciseHints).hasSize(4);

        exerciseHint.setExercise(null);
        request.post("/api/programming-exercises/" + exercise.getId() + "/exercise-hints", exerciseHint, HttpStatus.CONFLICT);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createCodeHintShouldFail() throws Exception {
        CodeHint codeHint = new CodeHint();
        codeHint.setTitle("Hint 1");
        codeHint.setExercise(exercise);

        long sizeBefore = exerciseHintRepository.count();
        request.post("/api/programming-exercises/" + codeHint.getExercise().getId() + "/exercise-hints", codeHint, HttpStatus.BAD_REQUEST);
        long sizeAfter = exerciseHintRepository.count();
        assertThat(sizeAfter).isEqualTo(sizeBefore);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void updateHintAsAStudentShouldReturnForbidden() throws Exception {
        updateHintForbidden();
    }

    private void updateHintForbidden() throws Exception {
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
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void updateHintAsTutorForbidden() throws Exception {
        updateHintForbidden();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void updateHintAsEditor() throws Exception {
        String newContent = "new content value!";
        exerciseHint.setContent(newContent);
        request.put("/api/programming-exercises/" + exerciseHint.getExercise().getId() + "/exercise-hints/" + exerciseHint.getId(), exerciseHint, HttpStatus.OK);
        Optional<ExerciseHint> hintAfterSave = exerciseHintRepository.findById(exerciseHint.getId());
        assertThat(hintAfterSave).isPresent();
        assertThat(hintAfterSave.get()).isInstanceOf(ExerciseHint.class);
        assertThat((hintAfterSave.get()).getContent()).isEqualTo(newContent);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateHintAsInstructor() throws Exception {
        String newContent = "new content value!";

        exerciseHint.setContent(newContent);
        request.put("/api/programming-exercises/" + exerciseHint.getExercise().getId() + "/exercise-hints/" + exerciseHint.getId(), exerciseHint, HttpStatus.OK);

        Optional<ExerciseHint> hintAfterSave = exerciseHintRepository.findById(exerciseHint.getId());
        assertThat(hintAfterSave).isPresent();
        assertThat(hintAfterSave.get()).isInstanceOf(ExerciseHint.class);
        assertThat((hintAfterSave.get()).getContent()).isEqualTo(newContent);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateCodeHintTitle() throws Exception {
        CodeHint codeHint = new CodeHint();
        codeHint.setTitle("Hint 1");
        codeHint.setExercise(exerciseLite);
        codeHint.setProgrammingExerciseTask(programmingExerciseTaskService.getSortedTasks(exercise).getFirst());

        exerciseHintRepository.save(codeHint);

        codeHint.setTitle("New Title");

        request.put("/api/programming-exercises/" + codeHint.getExercise().getId() + "/exercise-hints/" + codeHint.getId(), codeHint, HttpStatus.OK);
        Optional<ExerciseHint> hintAfterSave = exerciseHintRepository.findById(codeHint.getId());
        assertThat(hintAfterSave).isPresent();
        assertThat(hintAfterSave.get()).isInstanceOf(CodeHint.class);
        assertThat((hintAfterSave.get()).getTitle()).isEqualTo("New Title");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void deleteHintAsInstructor() throws Exception {
        final ExerciseHint exerciseHint = new ExerciseHint().content("content 4").title("title 4").exercise(exerciseLite);
        request.delete("/api/programming-exercises/" + exerciseHint.getExercise().getId() + "/exercise-hints/" + 0L, HttpStatus.NOT_FOUND);
        request.post("/api/programming-exercises/" + exerciseHint.getExercise().getId() + "/exercise-hints", exerciseHint, HttpStatus.CREATED);
        final ExerciseHint exerciseHintAfterCreation = exerciseHintRepository.findByExerciseId(exerciseLite.getId()).stream().findAny().orElseThrow();
        request.delete("/api/programming-exercises/" + exerciseHint.getExercise().getId() + "/exercise-hints/" + exerciseHintAfterCreation.getId(), HttpStatus.NO_CONTENT);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void deleteCodeHintAsInstructor() throws Exception {
        CodeHint codeHint = new CodeHint();
        codeHint.setTitle("Hint 1");
        codeHint.setExercise(exercise);
        exerciseHintRepository.save(codeHint);

        request.delete("/api/programming-exercises/" + codeHint.getExercise().getId() + "/exercise-hints/" + codeHint.getId(), HttpStatus.NO_CONTENT);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetHintTitleAsInstructor() throws Exception {
        // Only user and role matter, so we can re-use the logic
        testGetHintTitle();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testGetHintTitleAsTeachingAssistant() throws Exception {
        // Only user and role matter, so we can re-use the logic
        testGetHintTitle();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "user1", roles = "USER")
    void testGetHintTitleAsUser() throws Exception {
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
    @WithMockUser(username = TEST_PREFIX + "user1", roles = "USER")
    void testGetHintTitleForNonExistingHint() throws Exception {
        request.get("/api/programming-exercises/" + exercise.getId() + "/exercise-hints/12312312321/title", HttpStatus.NOT_FOUND, String.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createHintWithInvalidExerciseIds() throws Exception {
        Course course = programmingExerciseUtilService.addCourseWithOneProgrammingExercise();
        var unrelatedExercise = course.getExercises().stream().findFirst().orElseThrow();

        ExerciseHint exerciseHint = new ExerciseHint();
        exerciseHint.setTitle("Test Title");
        exerciseHint.setExercise(exerciseLite);

        request.post("/api/programming-exercises/" + unrelatedExercise.getId() + "/exercise-hints", exerciseHint, HttpStatus.CONFLICT);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateHintWithInvalidExerciseIds() throws Exception {
        Course course = programmingExerciseUtilService.addCourseWithOneProgrammingExercise();
        var unrelatedExercise = course.getExercises().stream().findFirst().orElseThrow();

        exerciseHint.setTitle("New Title");

        request.put("/api/programming-exercises/" + unrelatedExercise.getId() + "/exercise-hints/" + exerciseHint.getId(), exerciseHint, HttpStatus.CONFLICT);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void getHintTitleWithInvalidExerciseIds() throws Exception {
        Course course = programmingExerciseUtilService.addCourseWithOneProgrammingExercise();
        var unrelatedExercise = course.getExercises().stream().findFirst().orElseThrow();

        request.get("/api/programming-exercises/" + unrelatedExercise.getId() + "/exercise-hints/" + exerciseHint.getId(), HttpStatus.CONFLICT, String.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void getExerciseHintWithInvalidExerciseIds() throws Exception {
        Course course = programmingExerciseUtilService.addCourseWithOneProgrammingExercise();
        var unrelatedExercise = course.getExercises().stream().findFirst().orElseThrow();

        request.get("/api/programming-exercises/" + unrelatedExercise.getId() + "/exercise-hints/" + exerciseHint.getId(), HttpStatus.CONFLICT, String.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void deleteHintWithInvalidExerciseIds() throws Exception {
        Course course = programmingExerciseUtilService.addCourseWithOneProgrammingExercise();
        var unrelatedExercise = course.getExercises().stream().findFirst().orElseThrow();

        request.delete("/api/programming-exercises/" + unrelatedExercise.getId() + "/exercise-hints/" + exerciseHint.getId(), HttpStatus.CONFLICT);
    }

    private void addResultWithFailedTestCases(Collection<ProgrammingExerciseTestCase> failedTestCases) {
        var successfulTestCases = new ArrayList<>(exercise.getTestCases());
        successfulTestCases.removeAll(failedTestCases);
        addResultWithSuccessfulTestCases(successfulTestCases);
    }

    private void addResultWithSuccessfulTestCases(Collection<ProgrammingExerciseTestCase> successfulTestCases) {
        var submission = programmingExerciseUtilService.createProgrammingSubmission(studentParticipation, false);
        Result result = new Result().participation(submission.getParticipation()).assessmentType(AssessmentType.AUTOMATIC).score(0D).rated(true)
                .completionDate(ZonedDateTime.now().plusSeconds(timeOffset++));
        result = resultRepository.save(result);
        result.setSubmission(submission);
        submission.addResult(result);
        programmingSubmissionRepository.save(submission);

        for (ProgrammingExerciseTestCase testCase : exercise.getTestCases()) {
            var feedback = new Feedback();
            feedback.setPositive(successfulTestCases.contains(testCase));
            feedback.setTestCase(testCase);
            feedback.setVisibility(Visibility.ALWAYS);
            feedback.setType(FeedbackType.AUTOMATIC);
            participationUtilService.addFeedbackToResult(feedback, result);
        }
    }
}
