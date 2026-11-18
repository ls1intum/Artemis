package de.tum.cit.aet.artemis.exam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.core.domain.Language;
import de.tum.cit.aet.artemis.exam.domain.StudentExam;
import de.tum.cit.aet.artemis.exam.dto.submit.ModelingExamSubmissionDTO;
import de.tum.cit.aet.artemis.exam.dto.submit.QuizExamSubmissionDTO;
import de.tum.cit.aet.artemis.exam.dto.submit.SubmitExamExerciseDTO;
import de.tum.cit.aet.artemis.exam.dto.submit.SubmitExamParticipationDTO;
import de.tum.cit.aet.artemis.exam.dto.submit.SubmitStudentExamDTO;
import de.tum.cit.aet.artemis.exam.dto.submit.TextExamSubmissionDTO;
import de.tum.cit.aet.artemis.exam.service.StudentExamSubmitMapper;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseMode;
import de.tum.cit.aet.artemis.exercise.domain.InitializationState;
import de.tum.cit.aet.artemis.exercise.domain.Submission;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.modeling.domain.ModelingExercise;
import de.tum.cit.aet.artemis.modeling.domain.ModelingSubmission;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;
import de.tum.cit.aet.artemis.quiz.domain.QuizSubmission;
import de.tum.cit.aet.artemis.quiz.dto.submission.QuizSubmissionFromLiveClientDTO;
import de.tum.cit.aet.artemis.quiz.dto.submittedanswer.EntityIdRefDTO;
import de.tum.cit.aet.artemis.quiz.dto.submittedanswer.MultipleChoiceSubmittedAnswerFromLiveClientDTO;
import de.tum.cit.aet.artemis.quiz.dto.submittedanswer.SubmittedAnswerFromLiveClientDTO;
import de.tum.cit.aet.artemis.quiz.service.QuizSubmissionService;
import de.tum.cit.aet.artemis.quiz.test_repository.QuizExerciseTestRepository;
import de.tum.cit.aet.artemis.text.domain.TextExercise;
import de.tum.cit.aet.artemis.text.domain.TextSubmission;

/**
 * Unit tests for {@link StudentExamSubmitMapper}, the boundary that turns the slim submit DTO into the transient graph
 * the (unchanged) submit machinery consumes. These deterministically pin the silent-answer-loss traps that live in the
 * reconstruction: the participant must be re-derived so the swallowed {@code isOwnedBy} check passes, and every
 * submission's client id must survive so the swallowed DB id-match resolves it. They do <i>not</i> assert that exam team
 * mode works end-to-end — it is unsupported downstream (see the mapper's class javadoc and
 * {@code StudentParticipationRepository}); they only assert that the reconstruction sets the participant such that
 * {@code isOwnedBy} passes regardless of exercise mode.
 */
@ExtendWith(MockitoExtension.class)
class StudentExamSubmitMapperTest {

    @Mock
    private QuizExerciseTestRepository quizExerciseRepository;

    @Mock
    private QuizSubmissionService quizSubmissionService;

    @InjectMocks
    private StudentExamSubmitMapper mapper;

    private static final Long CURRENT_USER_ID = 7L;

    private static final String CURRENT_USER_LOGIN = "student1";

    private User currentUser() {
        User user = new User();
        user.setId(CURRENT_USER_ID);
        user.setLogin(CURRENT_USER_LOGIN);
        return user;
    }

    private StudentExam studentExamWith(Exercise... exercises) {
        StudentExam studentExam = new StudentExam();
        studentExam.setId(1L);
        studentExam.setExercises(List.of(exercises));
        return studentExam;
    }

    private StudentExam testRunStudentExamWith(Exercise... exercises) {
        StudentExam studentExam = studentExamWith(exercises);
        studentExam.setTestRun(true);
        return studentExam;
    }

    private static StudentParticipation onlyParticipation(Exercise exercise) {
        assertThat(exercise.getStudentParticipations()).hasSize(1);
        return exercise.getStudentParticipations().iterator().next();
    }

    private static Submission onlySubmission(StudentParticipation participation) {
        assertThat(participation.getSubmissions()).hasSize(1);
        return participation.getSubmissions().iterator().next();
    }

    @Test
    void shouldReconstructIndividualTextSubmissionOwnedByCurrentUser() {
        TextExercise exercise = new TextExercise();
        exercise.setId(10L);
        exercise.setMode(ExerciseMode.INDIVIDUAL);
        StudentExam studentExam = studentExamWith(exercise);

        var dto = new SubmitStudentExamDTO(1L,
                List.of(new SubmitExamExerciseDTO(10L, List.of(new SubmitExamParticipationDTO(1000L, List.of(new TextExamSubmissionDTO(100L, "answer", Language.GERMAN)))))));

        mapper.attachSubmissions(studentExam, dto, currentUser());

        StudentParticipation participation = onlyParticipation(exercise);
        assertThat(participation.getId()).isEqualTo(1000L);
        // the exercise back-reference must be re-attached: the test-run/test-exam quiz evaluation filters participations
        // by `participation.getExercise() instanceof QuizExercise`, so a null exercise would silently drop the evaluation
        assertThat(participation.getExercise()).isSameAs(exercise);
        // the initialization state must be re-attached so the persisted participation is not clobbered to null
        assertThat(participation.getInitializationState()).isEqualTo(InitializationState.INITIALIZED);
        // a non-test-run student exam yields non-test-run participations
        assertThat(participation.isTestRun()).isFalse();
        // the swallowed isOwnedBy(currentUser) in saveSubmission must pass, otherwise the answer is silently dropped
        assertThat(participation.isOwnedBy(currentUser())).isTrue();
        Submission submission = onlySubmission(participation);
        assertThat(submission).isInstanceOf(TextSubmission.class);
        // the client id must survive so existingParticipationInDatabase.getSubmissions().contains(...) id-matches
        assertThat(submission.getId()).isEqualTo(100L);
        assertThat(((TextSubmission) submission).getText()).isEqualTo("answer");
        // the client-detected language must survive: the downstream merge overwrites the column, so a dropped language
        // would be persisted as null (regression). See TextExamSubmissionDTO.
        assertThat(((TextSubmission) submission).getLanguage()).isEqualTo(Language.GERMAN);
        assertThat(submission.getLatestResult()).as("no result injected -> passes the result-injection guard").isNull();
    }

    @Test
    void shouldFlagReconstructedParticipationsOfATestRunAsTestRun() {
        // Regression guard: a test-run participation must be reconstructed with testRun=true, otherwise the evaluate step
        // persists it as a non-test-run participation and the summary's `WHERE p.testRun = TRUE` query hides the quiz.
        TextExercise exercise = new TextExercise();
        exercise.setId(12L);
        exercise.setMode(ExerciseMode.INDIVIDUAL);
        StudentExam testRunStudentExam = testRunStudentExamWith(exercise);

        var dto = new SubmitStudentExamDTO(1L,
                List.of(new SubmitExamExerciseDTO(12L, List.of(new SubmitExamParticipationDTO(1002L, List.of(new TextExamSubmissionDTO(102L, "answer", null)))))));

        mapper.attachSubmissions(testRunStudentExam, dto, currentUser());

        StudentParticipation participation = onlyParticipation(exercise);
        assertThat(participation.isTestRun()).isTrue();
        assertThat(participation.getInitializationState()).isEqualTo(InitializationState.INITIALIZED);
    }

    @Test
    void shouldSetParticipantSoIsOwnedByPassesRegardlessOfExerciseMode() {
        // NOT a claim that team exams submit end-to-end (they are unsupported downstream — the exam submit/summary
        // queries filter p.student.id and StudentParticipationRepository documents there is no exam team support). This
        // only pins the reconstruction contract: re-deriving the participant from the authenticated user makes the
        // swallowed isOwnedBy(currentUser) check in saveSubmission pass independent of exercise mode, so the answer is
        // not silently dropped there. A TEAM-mode exercise is used purely to show the re-derivation does not depend on
        // the mode.
        TextExercise exercise = new TextExercise();
        exercise.setId(11L);
        exercise.setMode(ExerciseMode.TEAM);
        StudentExam studentExam = studentExamWith(exercise);

        var dto = new SubmitStudentExamDTO(1L,
                List.of(new SubmitExamExerciseDTO(11L, List.of(new SubmitExamParticipationDTO(1001L, List.of(new TextExamSubmissionDTO(101L, "answer", null)))))));

        mapper.attachSubmissions(studentExam, dto, currentUser());

        StudentParticipation participation = onlyParticipation(exercise);
        assertThat(participation.isOwnedBy(currentUser())).isTrue();
        assertThat(onlySubmission(participation).getId()).isEqualTo(101L);
    }

    @Test
    void shouldReconstructModelingSubmissionPreservingContent() {
        ModelingExercise exercise = new ModelingExercise();
        exercise.setId(12L);
        StudentExam studentExam = studentExamWith(exercise);

        var dto = new SubmitStudentExamDTO(1L,
                List.of(new SubmitExamExerciseDTO(12L, List.of(new SubmitExamParticipationDTO(1002L, List.of(new ModelingExamSubmissionDTO(102L, "{model}", "explanation")))))));

        mapper.attachSubmissions(studentExam, dto, currentUser());

        Submission submission = onlySubmission(onlyParticipation(exercise));
        assertThat(submission).isInstanceOf(ModelingSubmission.class);
        assertThat(submission.getId()).isEqualTo(102L);
        assertThat(((ModelingSubmission) submission).getModel()).isEqualTo("{model}");
        assertThat(((ModelingSubmission) submission).getExplanationText()).isEqualTo("explanation");
    }

    @Test
    void shouldDelegateQuizReconstructionToLiveClientMapperWithQuestionLoadedExercise() {
        QuizExercise exercise = new QuizExercise();
        exercise.setId(13L);
        StudentExam studentExam = studentExamWith(exercise);

        QuizExercise questionLoadedExercise = new QuizExercise();
        questionLoadedExercise.setId(13L);
        when(quizExerciseRepository.findByIdWithQuestionsElseThrow(13L)).thenReturn(questionLoadedExercise);
        QuizSubmission rebuiltSubmission = new QuizSubmission();
        rebuiltSubmission.setId(103L);
        var answerCaptor = ArgumentCaptor.forClass(QuizSubmissionFromLiveClientDTO.class);
        when(quizSubmissionService.buildSubmissionFromLiveClientDTO(answerCaptor.capture(), eq(questionLoadedExercise))).thenReturn(rebuiltSubmission);

        Set<SubmittedAnswerFromLiveClientDTO> answers = Set.of(new MultipleChoiceSubmittedAnswerFromLiveClientDTO(new EntityIdRefDTO(20L), Set.of(new EntityIdRefDTO(201L))));
        var dto = new SubmitStudentExamDTO(1L,
                List.of(new SubmitExamExerciseDTO(13L, List.of(new SubmitExamParticipationDTO(1003L, List.of(new QuizExamSubmissionDTO(103L, answers)))))));

        mapper.attachSubmissions(studentExam, dto, currentUser());

        // the exercise must be loaded WITH questions and the id + answers forwarded verbatim to the reused #12832 mapper
        assertThat(answerCaptor.getValue().id()).isEqualTo(103L);
        assertThat(answerCaptor.getValue().submittedAnswers()).isEqualTo(answers);
        assertThat(onlySubmission(onlyParticipation(exercise))).isSameAs(rebuiltSubmission);
    }

    @Test
    void shouldInitializeParticipationsToEmptySetForExercisesAbsentFromTheDto() {
        // The student exam is loaded without participations; every exercise must end up with a concrete (never lazy)
        // collection, or the non-transactional submit code throws LazyInitializationException when it reads size().
        TextExercise submittedExercise = new TextExercise();
        submittedExercise.setId(10L);
        TextExercise untouchedExercise = new TextExercise();
        untouchedExercise.setId(99L);
        StudentExam studentExam = studentExamWith(submittedExercise, untouchedExercise);

        var dto = new SubmitStudentExamDTO(1L,
                List.of(new SubmitExamExerciseDTO(10L, List.of(new SubmitExamParticipationDTO(1000L, List.of(new TextExamSubmissionDTO(100L, "answer", null)))))));

        mapper.attachSubmissions(studentExam, dto, currentUser());

        assertThat(submittedExercise.getStudentParticipations()).hasSize(1);
        assertThat(untouchedExercise.getStudentParticipations()).isNotNull().isEmpty();
        // no exercise here is a quiz, so the quiz collaborators must never be touched
        verifyNoInteractions(quizExerciseRepository, quizSubmissionService);
    }

    @Test
    void shouldDropTextSubmissionSentForANonTextExercise() {
        // FIX 2: the text branch must validate the exercise type like the quiz branch, matching the legacy
        // ClassCastException-swallow. A text submission bound to a quiz exercise is dropped, not cast-and-crashed.
        QuizExercise exercise = new QuizExercise();
        exercise.setId(30L);
        StudentExam studentExam = studentExamWith(exercise);

        var dto = new SubmitStudentExamDTO(1L,
                List.of(new SubmitExamExerciseDTO(30L, List.of(new SubmitExamParticipationDTO(3000L, List.of(new TextExamSubmissionDTO(300L, "mismatched", null)))))));

        mapper.attachSubmissions(studentExam, dto, currentUser());

        // the participation is still initialized, but the type-mismatched submission is dropped
        assertThat(onlyParticipation(exercise).getSubmissions()).isEmpty();
        // a text submission never touches the quiz collaborators
        verifyNoInteractions(quizExerciseRepository, quizSubmissionService);
    }

    @Test
    void shouldDropModelingSubmissionSentForANonModelingExercise() {
        // FIX 2: same for the modeling branch.
        TextExercise exercise = new TextExercise();
        exercise.setId(31L);
        StudentExam studentExam = studentExamWith(exercise);

        var dto = new SubmitStudentExamDTO(1L,
                List.of(new SubmitExamExerciseDTO(31L, List.of(new SubmitExamParticipationDTO(3100L, List.of(new ModelingExamSubmissionDTO(310L, "{model}", "explanation")))))));

        mapper.attachSubmissions(studentExam, dto, currentUser());

        assertThat(onlyParticipation(exercise).getSubmissions()).isEmpty();
    }

    @Test
    void shouldSkipExerciseWhenMoreThanOneParticipationIsPresent() {
        // FIX 3: the ambiguity check must run on the wire List BEFORE folding into a Set. Two same-id participations
        // would collapse to one in a HashSet and wrongly proceed; on the List they are size 2 -> attach nothing.
        TextExercise exercise = new TextExercise();
        exercise.setId(40L);
        StudentExam studentExam = studentExamWith(exercise);

        var dto = new SubmitStudentExamDTO(1L,
                List.of(new SubmitExamExerciseDTO(40L, List.of(new SubmitExamParticipationDTO(4000L, List.of(new TextExamSubmissionDTO(400L, "a", null))),
                        new SubmitExamParticipationDTO(4000L, List.of(new TextExamSubmissionDTO(401L, "b", null)))))));

        mapper.attachSubmissions(studentExam, dto, currentUser());

        assertThat(exercise.getStudentParticipations()).isNotNull().isEmpty();
    }

    @Test
    void shouldSkipExerciseWhenTheParticipationCarriesMoreThanOneSubmission() {
        // FIX 3: same ambiguity rule for submissions.
        TextExercise exercise = new TextExercise();
        exercise.setId(41L);
        StudentExam studentExam = studentExamWith(exercise);

        var dto = new SubmitStudentExamDTO(1L, List.of(new SubmitExamExerciseDTO(41L,
                List.of(new SubmitExamParticipationDTO(4100L, List.of(new TextExamSubmissionDTO(410L, "a", null), new TextExamSubmissionDTO(411L, "b", null)))))));

        mapper.attachSubmissions(studentExam, dto, currentUser());

        assertThat(exercise.getStudentParticipations()).isNotNull().isEmpty();
    }

    @Test
    void shouldBuildEmptyQuizSubmissionWithoutLoadingQuestionsWhenNoAnswers() {
        // FIX 5: an empty answer set has nothing to re-resolve, so the expensive question-tree load is skipped and an
        // empty submission carrying only the id is produced (matching buildSubmissionFromLiveClientDTO for empty input).
        QuizExercise exercise = new QuizExercise();
        exercise.setId(60L);
        StudentExam studentExam = studentExamWith(exercise);

        var dto = new SubmitStudentExamDTO(1L,
                List.of(new SubmitExamExerciseDTO(60L, List.of(new SubmitExamParticipationDTO(6000L, List.of(new QuizExamSubmissionDTO(600L, Set.of())))))));

        mapper.attachSubmissions(studentExam, dto, currentUser());

        Submission submission = onlySubmission(onlyParticipation(exercise));
        assertThat(submission).isInstanceOf(QuizSubmission.class);
        assertThat(submission.getId()).isEqualTo(600L);
        assertThat(((QuizSubmission) submission).getSubmittedAnswers()).isEmpty();
        // neither the question-tree load nor the live-client rebuild run for an empty answer set
        verifyNoInteractions(quizExerciseRepository, quizSubmissionService);
    }

    @Test
    void shouldDropPoisonedExerciseButReconstructHealthyOnesInTheSameCall() {
        // FIX 1: a per-exercise reconstruction failure (here: the quiz question-tree load throws) must never abort the
        // hand-in nor lose a sibling exercise's answers. The poisoned quiz degrades to empty; the healthy text survives.
        QuizExercise poisonedQuiz = new QuizExercise();
        poisonedQuiz.setId(50L);
        TextExercise healthyText = new TextExercise();
        healthyText.setId(51L);
        StudentExam studentExam = studentExamWith(poisonedQuiz, healthyText);

        when(quizExerciseRepository.findByIdWithQuestionsElseThrow(50L)).thenThrow(new RuntimeException("boom"));

        Set<SubmittedAnswerFromLiveClientDTO> answers = Set.of(new MultipleChoiceSubmittedAnswerFromLiveClientDTO(new EntityIdRefDTO(20L), Set.of(new EntityIdRefDTO(201L))));
        var dto = new SubmitStudentExamDTO(1L,
                List.of(new SubmitExamExerciseDTO(50L, List.of(new SubmitExamParticipationDTO(5000L, List.of(new QuizExamSubmissionDTO(500L, answers))))),
                        new SubmitExamExerciseDTO(51L, List.of(new SubmitExamParticipationDTO(5100L, List.of(new TextExamSubmissionDTO(510L, "healthy", null)))))));

        mapper.attachSubmissions(studentExam, dto, currentUser());

        // poisoned quiz: reconstruction threw -> empty participations (its last-second changes dropped), no crash
        assertThat(poisonedQuiz.getStudentParticipations()).isNotNull().isEmpty();
        // healthy text: reconstructed despite the sibling failure
        Submission submission = onlySubmission(onlyParticipation(healthyText));
        assertThat(submission).isInstanceOf(TextSubmission.class);
        assertThat(((TextSubmission) submission).getText()).isEqualTo("healthy");
    }
}
