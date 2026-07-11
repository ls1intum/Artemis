package de.tum.cit.aet.artemis.exam.service;

import java.util.HashMap;
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
import de.tum.cit.aet.artemis.exercise.domain.InitializationState;
import de.tum.cit.aet.artemis.exercise.domain.Submission;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.modeling.domain.ModelingExercise;
import de.tum.cit.aet.artemis.modeling.domain.ModelingSubmission;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;
import de.tum.cit.aet.artemis.quiz.domain.QuizSubmission;
import de.tum.cit.aet.artemis.quiz.dto.submission.QuizSubmissionFromLiveClientDTO;
import de.tum.cit.aet.artemis.quiz.repository.QuizExerciseRepository;
import de.tum.cit.aet.artemis.quiz.service.QuizSubmissionService;
import de.tum.cit.aet.artemis.text.domain.TextExercise;
import de.tum.cit.aet.artemis.text.domain.TextSubmission;

/**
 * Reconstructs the transient {@link StudentExam} object graph that {@link StudentExamService#submitStudentExam} needs,
 * from the slim {@link SubmitStudentExamDTO} the client posts on hand-in.
 * <p>
 * The reconstruction intentionally keeps the exam-conduction save machinery
 * ({@code StudentExamService.saveSubmissions}/{@code saveSubmission}) byte-for-byte unchanged: it only changes how the
 * graph is built at the controller boundary. The DB-loaded exercise instances are reused (so the {@code instanceof}
 * type switch keeps working), and onto each we hang transient participations (id + participant = current user, so the
 * swallowed {@code isOwnedBy(currentUser)} check in {@code saveSubmission} passes) carrying transient submissions rebuilt
 * per type with their client ids preserved (so the DB id-match in {@code saveSubmission} still resolves them).
 * <p>
 * <b>Exam team mode is unsupported downstream.</b> Re-deriving the participant from the authenticated user makes
 * {@code isOwnedBy(currentUser)} pass regardless of exercise mode, but this is <i>not</i> a claim that team exams submit
 * end-to-end: the exam submit/summary queries filter on {@code p.student.id} and {@code StudentParticipationRepository}
 * documents that "in an exam there is only one submission for file upload, text, modeling and quiz and there is no team
 * support" (see {@code findGradesByExamIdAndStudentId}). This mapper deliberately adds no team-rejection logic (that
 * would be a behavior change); it merely reconstructs whatever the client posts, and exam team participations remain as
 * unsupported here as they are everywhere else in the exam flow.
 * <p>
 * <b>Failure containment.</b> The reconstruction runs at the controller boundary <i>before</i>
 * {@code submitStudentExam} marks the exam submitted, so any exception it throws would abort the whole hand-in with a
 * 5xx and leave the exam un-submitted. To preserve the legacy semantics (the exam is ALWAYS marked submitted, and a
 * broken exercise degrades to a logged drop of only that exercise's answers) the per-exercise reconstruction is wrapped
 * in a try/catch that mirrors the legacy per-exercise swallow: on failure the exercise is given an empty participation
 * set (its last-second changes are dropped) and the hand-in proceeds. One poisoned exercise can therefore never lose
 * another exercise's answers, nor turn the hand-in into an error.
 * <p>
 * <b>Duplicate / ambiguity resolution (single documented rule).</b> The only client-invalid duplicate that is silently
 * resolved is the exercise lookup map, which keeps the last entry on a duplicate exercise id (see {@link #indexById}).
 * Any other ambiguity — an exercise carrying anything other than exactly one participation, or a participation carrying
 * anything other than exactly one submission — is <i>not</i> resolved by collapsing into a {@code Set}; it attaches
 * nothing for that exercise, reproducing the server's {@code size() != 1 -> skip} outcome on the wire {@code List}
 * before it is ever folded into an entity {@code Set}. This is enforced here (rather than left to the downstream
 * {@code Set}) because folding same-id duplicates into a {@code HashSet} would collapse them and defeat the skip.
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
     * code reads {@code exercise.getStudentParticipations().size()}. Exercises with no matching entry in the DTO (or
     * whose reconstruction fails) get an empty set and are skipped downstream.
     *
     * @param existingStudentExam  the student exam loaded from the database (with its exercises)
     * @param submitStudentExamDTO the slim request body
     * @param currentUser          the authenticated user, used as the participant of every reconstructed participation
     */
    public void attachSubmissions(StudentExam existingStudentExam, SubmitStudentExamDTO submitStudentExamDTO, User currentUser) {
        Map<Long, SubmitExamExerciseDTO> exerciseDtosById = indexById(submitStudentExamDTO.exercises(), SubmitExamExerciseDTO::id);
        // One question-loaded quiz exercise per distinct quiz id per request; the exact-one participation/submission
        // rules already bound this to a single load per quiz, but memoizing keeps that guarantee explicit and cheap.
        Map<Long, QuizExercise> quizExerciseCache = new HashMap<>();
        boolean testRun = existingStudentExam.isTestRun();
        for (Exercise exercise : existingStudentExam.getExercises()) {
            if (exercise == null) {
                continue;
            }
            try {
                SubmitExamExerciseDTO exerciseDTO = exerciseDtosById.get(exercise.getId());
                exercise.setStudentParticipations(buildParticipations(exercise, exerciseDTO, currentUser, quizExerciseCache, testRun));
            }
            catch (Exception e) {
                // Mirror the legacy per-exercise swallow: never let one broken exercise abort the hand-in or lose
                // another exercise's answers. The exam is still marked submitted downstream; only this exercise's
                // last-second changes are dropped.
                log.error("Failed to reconstruct the submitted answers for exercise {}; dropping its last-second changes so the hand-in still succeeds", exercise.getId(), e);
                exercise.setStudentParticipations(new HashSet<>());
            }
        }
    }

    private Set<StudentParticipation> buildParticipations(Exercise exercise, @Nullable SubmitExamExerciseDTO exerciseDTO, User currentUser,
            Map<Long, QuizExercise> quizExerciseCache, boolean testRun) {
        Set<StudentParticipation> participations = new HashSet<>();
        // Enforce the exact-one-participation semantics on the wire List (see class javadoc): anything other than a
        // single participation attaches nothing, matching the server's studentParticipations.size() != 1 -> skip.
        if (exerciseDTO == null || exerciseDTO.studentParticipations() == null || exerciseDTO.studentParticipations().size() != 1) {
            return participations;
        }
        SubmitExamParticipationDTO participationDTO = exerciseDTO.studentParticipations().getFirst();
        // Enforce the exact-one-submission semantics on the wire List, matching the server's submissions.size() != 1 -> skip.
        if (participationDTO == null || participationDTO.submissions() == null || participationDTO.submissions().size() != 1) {
            return participations;
        }
        StudentParticipation participation = new StudentParticipation();
        participation.setId(participationDTO.id());
        // the participant is re-derived from the authenticated user (the client no longer sends it); this passes the
        // swallowed isOwnedBy(currentUser) check for individual exercises, and the second (DB-side) ownership check
        // validates the persisted participation independently. Exam team mode is unsupported downstream regardless (see
        // the class javadoc and StudentParticipationRepository); this mapper adds no team-specific handling either way.
        participation.setParticipant(currentUser);
        // Re-attach the exercise back-reference the legacy full-entity graph always carried. Without it the test-run /
        // test-exam quiz evaluation (ExamQuizService.evaluateQuizParticipationsForTestRunAndTestExam) filters this
        // participation out via `participation.getExercise() instanceof QuizExercise` and never persists the quiz
        // participation/result.
        participation.setExercise(exercise);
        // Preserve the two participation metadata fields the legacy conduction-loaded graph round-tripped and that the
        // downstream save must not clobber: the test-run flag (ExamQuizService persists this reconstructed participation,
        // and the summary's test-run query filters on `p.testRun = TRUE`, so a lost flag hides the quiz participation) and
        // the initialization state (a null would break consumers that read getInitializationState() and the participation
        // is INITIALIZED while it is being worked on).
        participation.setTestRun(testRun);
        participation.setInitializationState(InitializationState.INITIALIZED);
        participation.setSubmissions(buildSubmissions(exercise, participationDTO.submissions().getFirst(), quizExerciseCache));
        participations.add(participation);
        return participations;
    }

    private Set<Submission> buildSubmissions(Exercise exercise, @Nullable SubmitExamSubmissionDTO submissionDTO, Map<Long, QuizExercise> quizExerciseCache) {
        Set<Submission> submissions = new HashSet<>();
        // null-tolerate an individual null element in the client array (skip it) rather than throwing.
        if (submissionDTO == null) {
            return submissions;
        }
        Submission submission = buildSubmission(exercise, submissionDTO, quizExerciseCache);
        if (submission != null) {
            submissions.add(submission);
        }
        return submissions;
    }

    @Nullable
    private Submission buildSubmission(Exercise exercise, SubmitExamSubmissionDTO submissionDTO, Map<Long, QuizExercise> quizExerciseCache) {
        return switch (submissionDTO) {
            case TextExamSubmissionDTO textDTO -> {
                if (!(exercise instanceof TextExercise)) {
                    // matches the legacy ClassCastException-swallow: a text submission for a non-text exercise is dropped.
                    log.warn("Ignoring text submission {} for non-text exercise {}", textDTO.id(), exercise.getId());
                    yield null;
                }
                TextSubmission textSubmission = new TextSubmission();
                textSubmission.setId(textDTO.id());
                textSubmission.setText(textDTO.text());
                // Preserve the client-detected language: saveSubmissionTextExercise persists this submission via a JPA
                // merge that overwrites every mapped column from the entity, so a dropped language would be written as
                // null on every hand-in text edit (regression vs the legacy full-entity body). See TextExamSubmissionDTO.
                textSubmission.setLanguage(textDTO.language());
                yield textSubmission;
            }
            case ModelingExamSubmissionDTO modelingDTO -> {
                if (!(exercise instanceof ModelingExercise)) {
                    // matches the legacy ClassCastException-swallow: a modeling submission for a non-modeling exercise is dropped.
                    log.warn("Ignoring modeling submission {} for non-modeling exercise {}", modelingDTO.id(), exercise.getId());
                    yield null;
                }
                ModelingSubmission modelingSubmission = new ModelingSubmission();
                modelingSubmission.setId(modelingDTO.id());
                modelingSubmission.setModel(modelingDTO.model());
                modelingSubmission.setExplanationText(modelingDTO.explanationText());
                yield modelingSubmission;
            }
            case QuizExamSubmissionDTO quizDTO -> buildQuizSubmission(exercise, quizDTO, quizExerciseCache);
            // programming and file-upload submissions are never saved via the exam hand-in; accepted and ignored.
            default -> null;
        };
    }

    @Nullable
    private Submission buildQuizSubmission(Exercise exercise, QuizExamSubmissionDTO quizDTO, Map<Long, QuizExercise> quizExerciseCache) {
        if (!(exercise instanceof QuizExercise)) {
            // matches the legacy ClassCastException-swallow: a quiz submission for a non-quiz exercise is dropped.
            log.warn("Ignoring quiz submission {} for non-quiz exercise {}", quizDTO.id(), exercise.getId());
            return null;
        }
        // Efficiency: when there are no submitted answers there is nothing to re-resolve, so we skip the (expensive)
        // questions-with-nested-options load entirely and hand back an empty submission carrying just the id. This is
        // byte-for-byte what buildSubmissionFromLiveClientDTO returns for null/empty answers (QuizSubmission already
        // initializes submittedAnswers to an empty set, so the downstream save iterates over nothing).
        if (quizDTO.submittedAnswers() == null || quizDTO.submittedAnswers().isEmpty()) {
            QuizSubmission quizSubmission = new QuizSubmission();
            quizSubmission.setId(quizDTO.id());
            return quizSubmission;
        }
        // The quiz exercise must be loaded WITH its questions (and their nested options/items/spots) so
        // buildSubmissionFromLiveClientDTO can re-resolve the client-supplied answer ids; without it every answer is
        // dropped. This mirrors the quiz live/exam auto-save path (#12832). Memoized so at most one load happens per
        // distinct quiz exercise per request.
        QuizExercise quizExerciseWithQuestions = quizExerciseCache.computeIfAbsent(exercise.getId(), quizExerciseRepository::findByIdWithQuestionsElseThrow);
        return quizSubmissionService.buildSubmissionFromLiveClientDTO(new QuizSubmissionFromLiveClientDTO(quizDTO.id(), quizDTO.submittedAnswers()), quizExerciseWithQuestions);
    }

    private static <T> Map<Long, T> indexById(@Nullable List<T> items, Function<T, Long> idExtractor) {
        if (items == null) {
            return Map.of();
        }
        // The single documented duplicate-resolution rule (see class javadoc): on the client-invalid case of duplicate
        // ids, keep the last entry rather than throwing. Null elements and null ids are skipped.
        return items.stream().filter(item -> item != null && idExtractor.apply(item) != null)
                .collect(Collectors.toMap(idExtractor, Function.identity(), (first, second) -> second));
    }
}
