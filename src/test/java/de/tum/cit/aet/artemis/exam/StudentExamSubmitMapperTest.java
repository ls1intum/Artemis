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
 * the (unchanged) submit machinery consumes. These deterministically pin the two silent-answer-loss traps that live in
 * the reconstruction: the participant must be re-derived so the swallowed {@code isOwnedBy} check passes (for individual
 * AND team exercises), and every submission's client id must survive so the swallowed DB id-match resolves it.
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
                List.of(new SubmitExamExerciseDTO(10L, List.of(new SubmitExamParticipationDTO(1000L, List.of(new TextExamSubmissionDTO(100L, "answer")))))));

        mapper.attachSubmissions(studentExam, dto, currentUser());

        StudentParticipation participation = onlyParticipation(exercise);
        assertThat(participation.getId()).isEqualTo(1000L);
        // the swallowed isOwnedBy(currentUser) in saveSubmission must pass, otherwise the answer is silently dropped
        assertThat(participation.isOwnedBy(currentUser())).isTrue();
        Submission submission = onlySubmission(participation);
        assertThat(submission).isInstanceOf(TextSubmission.class);
        // the client id must survive so existingParticipationInDatabase.getSubmissions().contains(...) id-matches
        assertThat(submission.getId()).isEqualTo(100L);
        assertThat(((TextSubmission) submission).getText()).isEqualTo("answer");
        assertThat(submission.getLatestResult()).as("no result injected -> passes the result-injection guard").isNull();
    }

    @Test
    void shouldReconstructTeamExerciseParticipationOwnedByCurrentUser() {
        // Team mode is the trap: the client participation carries no team, yet isOwnedBy(currentUser) must still pass.
        // Setting the participant to the authenticated user makes getStudent().login match, independent of exercise mode.
        TextExercise exercise = new TextExercise();
        exercise.setId(11L);
        exercise.setMode(ExerciseMode.TEAM);
        StudentExam studentExam = studentExamWith(exercise);

        var dto = new SubmitStudentExamDTO(1L,
                List.of(new SubmitExamExerciseDTO(11L, List.of(new SubmitExamParticipationDTO(1001L, List.of(new TextExamSubmissionDTO(101L, "team answer")))))));

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
                List.of(new SubmitExamExerciseDTO(10L, List.of(new SubmitExamParticipationDTO(1000L, List.of(new TextExamSubmissionDTO(100L, "answer")))))));

        mapper.attachSubmissions(studentExam, dto, currentUser());

        assertThat(submittedExercise.getStudentParticipations()).hasSize(1);
        assertThat(untouchedExercise.getStudentParticipations()).isNotNull().isEmpty();
        // no exercise here is a quiz, so the quiz collaborators must never be touched
        verifyNoInteractions(quizExerciseRepository, quizSubmissionService);
    }
}
