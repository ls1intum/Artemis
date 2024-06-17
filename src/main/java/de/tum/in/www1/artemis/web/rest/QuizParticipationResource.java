package de.tum.in.www1.artemis.web.rest;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_CORE;

import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.json.MappingJacksonValue;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.in.www1.artemis.domain.Result;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.domain.quiz.QuizExercise;
import de.tum.in.www1.artemis.domain.quiz.QuizSubmission;
import de.tum.in.www1.artemis.repository.QuizExerciseRepository;
import de.tum.in.www1.artemis.repository.QuizSubmissionRepository;
import de.tum.in.www1.artemis.repository.ResultRepository;
import de.tum.in.www1.artemis.repository.SubmittedAnswerRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.security.annotations.enforceRoleInExercise.EnforceAtLeastStudentInExercise;
import de.tum.in.www1.artemis.service.ParticipationService;
import de.tum.in.www1.artemis.service.quiz.QuizBatchService;
import de.tum.in.www1.artemis.web.rest.errors.AccessForbiddenException;

/**
 * REST controller for managing quiz participations.
 */
@Profile(PROFILE_CORE)
@RestController
@RequestMapping("api/")
public class QuizParticipationResource {

    private static final Logger log = LoggerFactory.getLogger(QuizParticipationResource.class);

    private final QuizExerciseRepository quizExerciseRepository;

    private final ParticipationService participationService;

    private final UserRepository userRepository;

    private final ResultRepository resultRepository;

    private final SubmittedAnswerRepository submittedAnswerRepository;

    private final QuizSubmissionRepository quizSubmissionRepository;

    private final QuizBatchService quizBatchService;

    public QuizParticipationResource(QuizExerciseRepository quizExerciseRepository, ParticipationService participationService, UserRepository userRepository,
            ResultRepository resultRepository, SubmittedAnswerRepository submittedAnswerRepository, QuizSubmissionRepository quizSubmissionRepository,
            QuizBatchService quizBatchService) {
        this.quizExerciseRepository = quizExerciseRepository;
        this.participationService = participationService;
        this.userRepository = userRepository;
        this.resultRepository = resultRepository;
        this.submittedAnswerRepository = submittedAnswerRepository;
        this.quizSubmissionRepository = quizSubmissionRepository;
        this.quizBatchService = quizBatchService;
    }

    /**
     * POST /quiz-exercises/{exerciseId}/start-participation : start the quiz exercise participation
     *
     * @param exerciseId the id of the quiz exercise
     * @return The created participation
     */
    @PostMapping("quiz-exercises/{exerciseId}/start-participation")
    @EnforceAtLeastStudentInExercise
    public ResponseEntity<MappingJacksonValue> startParticipation(@PathVariable Long exerciseId) {
        log.debug("REST request to start quiz exercise participation : {}", exerciseId);
        QuizExercise exercise = quizExerciseRepository.findByIdWithQuestionsElseThrow(exerciseId);

        if (exercise.getReleaseDate() != null && exercise.getReleaseDate().isAfter(ZonedDateTime.now())) {
            throw new AccessForbiddenException("Students cannot start an exercise before the release date");
        }

        User user = userRepository.getUserWithGroupsAndAuthorities();

        var quizBatch = quizBatchService.getQuizBatchForStudentByLogin(exercise, user.getLogin());
        exercise.setQuizBatches(quizBatch.stream().collect(Collectors.toSet()));

        StudentParticipation participation = participationService.startExercise(exercise, user, true);

        Optional<Result> optionalResult = resultRepository.findFirstByParticipationIdAndRatedOrderByCompletionDateDesc(participation.getId(), true);
        Result result;
        if (optionalResult.isPresent()) {
            var quizSubmission = (QuizSubmission) optionalResult.get().getSubmission();
            var submittedAnswers = submittedAnswerRepository.findBySubmission(quizSubmission);
            quizSubmission.setSubmittedAnswers(submittedAnswers);
            result = optionalResult.get();
        }
        else {
            result = new Result();
            result.setSubmission(quizSubmissionRepository.findWithEagerSubmittedAnswersByParticipationId(participation.getId()).orElseThrow());
        }

        participation.setResults(Set.of(result));
        participation.setExercise(exercise);

        var view = exercise.viewForStudentsInQuizExercise(quizBatch.orElse(null));
        MappingJacksonValue value = new MappingJacksonValue(participation);
        value.setSerializationView(view);
        return new ResponseEntity<>(value, HttpStatus.OK);
    }
}
