package de.tum.cit.aet.artemis.exam.service;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.exam.config.ExamEnabled;
import de.tum.cit.aet.artemis.exam.domain.StudentExam;
import de.tum.cit.aet.artemis.exam.dto.submit.ModelingExamSubmissionDTO;
import de.tum.cit.aet.artemis.exam.dto.submit.QuizExamSubmissionDTO;
import de.tum.cit.aet.artemis.exam.dto.submit.SubmitExamExerciseDTO;
import de.tum.cit.aet.artemis.exam.dto.submit.SubmitExamParticipationDTO;
import de.tum.cit.aet.artemis.exam.dto.submit.SubmitExamSubmissionDTO;
import de.tum.cit.aet.artemis.exam.dto.submit.SubmitStudentExamDTO;
import de.tum.cit.aet.artemis.exam.dto.submit.TextExamSubmissionDTO;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.Submission;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.modeling.domain.ModelingSubmission;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;
import de.tum.cit.aet.artemis.quiz.dto.submission.QuizSubmissionFromLiveClientDTO;
import de.tum.cit.aet.artemis.quiz.repository.QuizExerciseRepository;
import de.tum.cit.aet.artemis.quiz.service.QuizSubmissionService;
import de.tum.cit.aet.artemis.text.domain.TextSubmission;

/**
 * Reconstructs the transient {@link StudentExam} object graph that {@link StudentExamService#submitStudentExam} needs,
 * from the slim {@link SubmitStudentExamDTO} the client posts on hand-in.
 * <p>
 * The reconstruction intentionally keeps the exam-conduction save machinery
 * ({@code StudentExamService.saveSubmissions}/{@code saveSubmission}) byte-for-byte unchanged: it only changes how the
 * graph is built at the controller boundary. The DB-loaded exercise instances are reused (so the {@code instanceof}
 * type switch keeps working), and onto each we hang transient participations (id + participant = current user, so
 * {@code isOwnedBy(currentUser)} passes for both individual and team exercises) carrying transient submissions rebuilt
 * per type with their client ids preserved (so the DB id-match in {@code saveSubmission} still resolves them).
 */
@Conditional(ExamEnabled.class)
@Lazy
@Component
public class StudentExamSubmitMapper {

    private static final Logger log = LoggerFactory.getLogger(StudentExamSubmitMapper.class);

    private final QuizExerciseRepository quizExerciseRepository;

    private final QuizSubmissionService quizSubmissionService;

    public StudentExamSubmitMapper(QuizExerciseRepository quizExerciseRepository, QuizSubmissionService quizSubmissionService) {
        this.quizExerciseRepository = quizExerciseRepository;
        this.quizSubmissionService = quizSubmissionService;
    }

    /**
     * Attaches the client-supplied last-second submission changes onto the DB-loaded exercises of the given student
     * exam, producing the transient graph the submit machinery consumes.
     * <p>
     * Every exercise's {@code studentParticipations} collection is (re)initialized to a concrete set — this is
     * required because the student exam was loaded without its participations, so leaving the lazy collection
     * untouched would trigger a {@link org.hibernate.LazyInitializationException} when the (non-transactional) submit
     * code reads {@code exercise.getStudentParticipations().size()}. Exercises with no matching entry in the DTO get an
     * empty set and are skipped downstream.
     *
     * @param existingStudentExam  the student exam loaded from the database (with its exercises)
     * @param submitStudentExamDTO the slim request body
     * @param currentUser          the authenticated user, used as the participant of every reconstructed participation
     */
    public void attachSubmissions(StudentExam existingStudentExam, SubmitStudentExamDTO submitStudentExamDTO, User currentUser) {
        Map<Long, SubmitExamExerciseDTO> exerciseDtosById = indexById(submitStudentExamDTO.exercises(), SubmitExamExerciseDTO::id);
        for (Exercise exercise : existingStudentExam.getExercises()) {
            SubmitExamExerciseDTO exerciseDTO = exerciseDtosById.get(exercise.getId());
            exercise.setStudentParticipations(buildParticipations(exercise, exerciseDTO, currentUser));
        }
    }

    private Set<StudentParticipation> buildParticipations(Exercise exercise, @Nullable SubmitExamExerciseDTO exerciseDTO, User currentUser) {
        Set<StudentParticipation> participations = new HashSet<>();
        if (exerciseDTO == null || exerciseDTO.studentParticipations() == null) {
            return participations;
        }
        for (SubmitExamParticipationDTO participationDTO : exerciseDTO.studentParticipations()) {
            StudentParticipation participation = new StudentParticipation();
            participation.setId(participationDTO.id());
            // the participant is re-derived from the authenticated user (the client no longer sends it); this passes
            // isOwnedBy(currentUser) for individual exercises directly and for team exercises the DB participation
            // still carries the real team, which is what the second (DB-side) ownership check validates.
            participation.setParticipant(currentUser);
            participation.setSubmissions(buildSubmissions(exercise, participationDTO));
            participations.add(participation);
        }
        return participations;
    }

    private Set<Submission> buildSubmissions(Exercise exercise, SubmitExamParticipationDTO participationDTO) {
        Set<Submission> submissions = new HashSet<>();
        if (participationDTO.submissions() == null) {
            return submissions;
        }
        for (SubmitExamSubmissionDTO submissionDTO : participationDTO.submissions()) {
            Submission submission = buildSubmission(exercise, submissionDTO);
            if (submission != null) {
                submissions.add(submission);
            }
        }
        return submissions;
    }

    @Nullable
    private Submission buildSubmission(Exercise exercise, SubmitExamSubmissionDTO submissionDTO) {
        return switch (submissionDTO) {
            case TextExamSubmissionDTO textDTO -> {
                TextSubmission textSubmission = new TextSubmission();
                textSubmission.setId(textDTO.id());
                textSubmission.setText(textDTO.text());
                yield textSubmission;
            }
            case ModelingExamSubmissionDTO modelingDTO -> {
                ModelingSubmission modelingSubmission = new ModelingSubmission();
                modelingSubmission.setId(modelingDTO.id());
                modelingSubmission.setModel(modelingDTO.model());
                modelingSubmission.setExplanationText(modelingDTO.explanationText());
                yield modelingSubmission;
            }
            case QuizExamSubmissionDTO quizDTO -> buildQuizSubmission(exercise, quizDTO);
            // programming and file-upload submissions are never saved via the exam hand-in; accepted and ignored.
            default -> null;
        };
    }

    @Nullable
    private Submission buildQuizSubmission(Exercise exercise, QuizExamSubmissionDTO quizDTO) {
        if (!(exercise instanceof QuizExercise)) {
            // defensive: a quiz submission for a non-quiz exercise would be dropped by the type switch anyway.
            log.warn("Ignoring quiz submission {} for non-quiz exercise {}", quizDTO.id(), exercise.getId());
            return null;
        }
        // The quiz exercise must be loaded WITH its questions (and their nested options/items/spots) so
        // buildSubmissionFromLiveClientDTO can re-resolve the client-supplied answer ids; without it every answer is
        // dropped. This mirrors the quiz live/exam auto-save path (#12832).
        QuizExercise quizExerciseWithQuestions = quizExerciseRepository.findByIdWithQuestionsElseThrow(exercise.getId());
        return quizSubmissionService.buildSubmissionFromLiveClientDTO(new QuizSubmissionFromLiveClientDTO(quizDTO.id(), quizDTO.submittedAnswers()), quizExerciseWithQuestions);
    }

    private static <T> Map<Long, T> indexById(@Nullable List<T> items, Function<T, Long> idExtractor) {
        if (items == null) {
            return Map.of();
        }
        // keep the last entry on the (client-invalid) case of duplicate ids rather than throwing.
        return items.stream().filter(item -> idExtractor.apply(item) != null).collect(Collectors.toMap(idExtractor, Function.identity(), (first, second) -> second));
    }
}
