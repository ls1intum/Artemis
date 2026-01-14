package de.tum.cit.aet.artemis.exercise.service;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.http.converter.json.MappingJacksonValue;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.assessment.repository.ResultRepository;
import de.tum.cit.aet.artemis.core.config.Constants;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.repository.SubmissionRepository;
import de.tum.cit.aet.artemis.quiz.domain.QuizBatch;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;
import de.tum.cit.aet.artemis.quiz.domain.QuizSubmission;
import de.tum.cit.aet.artemis.quiz.dto.participation.StudentQuizParticipationWithQuestionsDTO;
import de.tum.cit.aet.artemis.quiz.dto.participation.StudentQuizParticipationWithSolutionsDTO;
import de.tum.cit.aet.artemis.quiz.dto.participation.StudentQuizParticipationWithoutQuestionsDTO;
import de.tum.cit.aet.artemis.quiz.repository.QuizExerciseRepository;
import de.tum.cit.aet.artemis.quiz.repository.SubmittedAnswerRepository;
import de.tum.cit.aet.artemis.quiz.service.QuizBatchService;
import de.tum.cit.aet.artemis.quiz.service.QuizSubmissionService;

/**
 * Service Implementation for managing the participation of students in quiz exercises.
 */
@Profile(Constants.PROFILE_CORE)
@Lazy
@Service
public class QuizParticipationService {

    private static final Logger log = LoggerFactory.getLogger(QuizParticipationService.class);

    private final QuizBatchService quizBatchService;

    private final ParticipationService participationService;

    private final QuizSubmissionService quizSubmissionService;

    private final ResultRepository resultRepository;

    private final SubmittedAnswerRepository submittedAnswerRepository;

    private final SubmissionRepository submissionRepository;

    private final QuizExerciseRepository quizExerciseRepository;

    public QuizParticipationService(QuizBatchService quizBatchService, ParticipationService participationService, QuizSubmissionService quizSubmissionService,
            ResultRepository resultRepository, SubmittedAnswerRepository submittedAnswerRepository, SubmissionRepository submissionRepository,
            QuizExerciseRepository quizExerciseRepository) {
        this.quizBatchService = quizBatchService;
        this.participationService = participationService;
        this.quizSubmissionService = quizSubmissionService;
        this.resultRepository = resultRepository;
        this.submittedAnswerRepository = submittedAnswerRepository;
        this.submissionRepository = submissionRepository;
        this.quizExerciseRepository = quizExerciseRepository;
    }

    /**
     * Handles the request of a student to participate in a quiz exercise.
     *
     * @param quizExercise the quiz exercise the user wants to participate in
     * @param user         the user that wants to participate
     * @return a DTO containing the participation and possibly the quiz questions
     */
    @Nullable
    // TODO: use a proper DTO (or interface here for the return type and avoid MappingJacksonValue)
    public MappingJacksonValue participationForQuizExercise(QuizExercise quizExercise, User user) {
        // 1st case the quiz has already ended
        if (quizExercise.isQuizEnded()) {
            return handleQuizEnded(quizExercise, user);
        }
        quizExercise.setQuizBatches(null); // not available here
        var quizBatch = quizBatchService.getQuizBatchForStudentByLogin(quizExercise, user.getLogin());

        if (quizBatch.isPresent() && quizBatch.get().isStarted()) {
            return handleQuizBatchStarted(quizExercise, user, quizBatch);
        }
        else {
            return handleQuizNotStarted(quizExercise, user, quizBatch);
        }

    }

    private MappingJacksonValue handleQuizNotStarted(QuizExercise quizExercise, User user, Optional<QuizBatch> quizBatch) {
        // Quiz hasn't started yet => no Result, only quizExercise without questions
        quizExercise.filterSensitiveInformation();
        quizExercise.setQuizBatches(quizBatch.stream().collect(Collectors.toSet()));
        if (quizExercise.getAllowedNumberOfAttempts() != null) {
            var attempts = submissionRepository.countByExerciseIdAndStudentLogin(quizExercise.getId(), user.getLogin());
            quizExercise.setRemainingNumberOfAttempts(quizExercise.getAllowedNumberOfAttempts() - attempts);
        }
        StudentParticipation participation = new StudentParticipation().exercise(quizExercise);
        return new MappingJacksonValue(participation);
    }

    private MappingJacksonValue handleQuizBatchStarted(QuizExercise quizExercise, User user, Optional<QuizBatch> quizBatch) {
        // Quiz is active => construct Participation from
        // filtered quizExercise and submission from HashMap
        quizExercise = quizExerciseRepository.findByIdWithQuestionsElseThrow(quizExercise.getId());
        quizExercise.setQuizBatches(quizBatch.stream().collect(Collectors.toSet()));
        quizExercise.filterForStudentsDuringQuiz();
        StudentParticipation participation = participationForQuizWithSubmissionAndResult(quizExercise, user.getLogin(), quizBatch.get());

        // TODO: Duplicate
        Object responseDTO = null;
        if (participation != null) {
            var submissions = submissionRepository.findAllWithResultsByParticipationIdOrderBySubmissionDateAsc(participation.getId());
            participation.setSubmissions(new HashSet<>(submissions));
            if (quizExercise.isQuizEnded()) {
                responseDTO = StudentQuizParticipationWithSolutionsDTO.of(participation);
            }
            else if (quizBatch.get().isStarted()) {
                responseDTO = StudentQuizParticipationWithQuestionsDTO.of(participation);
            }
            else {
                responseDTO = StudentQuizParticipationWithoutQuestionsDTO.of(participation);
            }
        }

        return responseDTO != null ? new MappingJacksonValue(responseDTO) : null;
    }

    private MappingJacksonValue handleQuizEnded(QuizExercise quizExercise, User user) {
        // quiz has ended => get participation from database and add full quizExercise
        quizExercise = quizExerciseRepository.findByIdWithQuestionsElseThrow(quizExercise.getId());
        StudentParticipation participation = participationForQuizWithSubmissionAndResult(quizExercise, user.getLogin(), null);
        if (participation == null) {
            return null;
        }

        return new MappingJacksonValue(participation);
    }

    /**
     * Get a participation for the given quiz and username.
     * If the quiz hasn't ended, participation is constructed from cached submission.
     * If the quiz has ended, we first look in the database for the participation and construct one if none was found
     *
     * @param quizExercise the quiz exercise to attach to the participation
     * @param username     the username of the user that the participation belongs to
     * @param quizBatch    the quiz batch of quiz exercise which user participated in
     * @return the found or created participation with a result
     */
    private StudentParticipation participationForQuizWithSubmissionAndResult(QuizExercise quizExercise, String username, QuizBatch quizBatch) {
        // try getting participation from database
        Optional<StudentParticipation> optionalParticipation = participationService.findOneByExerciseAndStudentLoginAnyState(quizExercise, username);

        if (quizExercise.isQuizEnded() || quizSubmissionService.hasUserSubmitted(quizBatch, username)) {

            if (optionalParticipation.isEmpty()) {
                log.error("Participation in quiz {} not found for user {}", quizExercise.getTitle(), username);
                // TODO properly handle this case
                return null;
            }
            StudentParticipation participation = optionalParticipation.get();
            // add exercise
            participation.setExercise(quizExercise);

            // add the appropriate submission and result
            Result result = resultRepository.findFirstByParticipationIdAndRatedWithSubmissionOrderByCompletionDateDesc(participation.getId(), true).orElse(null);
            if (result != null) {
                // find the submitted answers (they are NOT loaded eagerly anymore)
                var quizSubmission = (QuizSubmission) result.getSubmission();
                quizSubmission.setResults(List.of(result));
                var submittedAnswers = submittedAnswerRepository.findBySubmission(quizSubmission);
                quizSubmission.setSubmittedAnswers(submittedAnswers);
                participation.setSubmissions(Set.of(quizSubmission));
            }
            return participation;
        }

        return optionalParticipation.orElse(null);
    }

}
