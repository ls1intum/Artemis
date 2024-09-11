package de.tum.cit.aet.artemis.service.quiz;

import static de.tum.cit.aet.artemis.config.Constants.PROFILE_CORE;

import java.security.SecureRandom;
import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.Set;

import jakarta.annotation.Nullable;

import org.apache.commons.lang3.RandomStringUtils;
import org.hibernate.Hibernate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.config.Constants;
import de.tum.cit.aet.artemis.domain.User;
import de.tum.cit.aet.artemis.domain.enumeration.QuizMode;
import de.tum.cit.aet.artemis.domain.quiz.QuizBatch;
import de.tum.cit.aet.artemis.domain.quiz.QuizExercise;
import de.tum.cit.aet.artemis.domain.quiz.QuizSubmission;
import de.tum.cit.aet.artemis.exception.QuizJoinException;
import de.tum.cit.aet.artemis.repository.ParticipationRepository;
import de.tum.cit.aet.artemis.repository.QuizBatchRepository;
import de.tum.cit.aet.artemis.repository.QuizSubmissionRepository;
import de.tum.cit.aet.artemis.service.ParticipationService;

@Profile(PROFILE_CORE)
@Service
public class QuizBatchService {

    // copied from tech.jhipster.security.RandomUtil
    private static final SecureRandom SECURE_RANDOM;

    static {
        SECURE_RANDOM = new SecureRandom();
        SECURE_RANDOM.nextBytes(new byte[64]);
    }

    private static final Logger log = LoggerFactory.getLogger(QuizBatchService.class);

    private final QuizBatchRepository quizBatchRepository;

    private final QuizSubmissionRepository quizSubmissionRepository;

    public QuizBatchService(QuizBatchRepository quizBatchRepository, QuizSubmissionRepository quizSubmissionRepository, ParticipationRepository participationRepository,
            ParticipationService participationService) {
        this.quizBatchRepository = quizBatchRepository;
        this.quizSubmissionRepository = quizSubmissionRepository;
    }

    /**
     * Save the given quizBatch to the database
     *
     * @param quizBatch the quiz batch to save
     * @return the saved quiz batch
     */
    public QuizBatch save(QuizBatch quizBatch) {
        return quizBatchRepository.saveAndFlush(quizBatch);
    }

    /**
     * Get or create the batch for synchronized quiz exercises. If it was created it will not have been saved to the database yet.
     * Only valid to call for synchronized quiz exercises
     *
     * @param quizExercise the quiz for which to get the batch
     * @return the single batch of the exercise
     */
    public QuizBatch getOrCreateSynchronizedQuizBatch(QuizExercise quizExercise) {
        if (quizExercise.getQuizMode() != QuizMode.SYNCHRONIZED) {
            throw new IllegalStateException();
        }

        if (quizExercise.getQuizBatches() == null || !Hibernate.isInitialized(quizExercise.getQuizBatches())) {
            var quizBatch = quizBatchRepository.findFirstByQuizExercise(quizExercise);
            if (quizBatch.isPresent()) {
                return quizBatch.get();
            }
        }
        else if (!quizExercise.getQuizBatches().isEmpty()) {
            return quizExercise.getQuizBatches().iterator().next();
        }

        var quizBatch = new QuizBatch();
        quizBatch.setQuizExercise(quizExercise);
        quizExercise.setQuizBatches(Set.of(quizBatch));
        return quizBatch;
    }

    /**
     * join a student to a batch
     * does not check of the user is already part of a batch
     * does not apply to quizzes in SYNCHRONIZED mode
     *
     * @param quizExercise the quiz of the batch to join
     * @param user         the user to join
     * @param password     the password of the batch to join; unused for INDIVIDUAL mode
     * @return the batch that was joined, or empty if the batch could not be found
     */
    public QuizBatch joinBatch(QuizExercise quizExercise, User user, @Nullable String password) throws QuizJoinException {
        QuizBatch quizBatch = switch (quizExercise.getQuizMode()) {
            case SYNCHRONIZED -> throw new QuizJoinException("quizBatchJoinSynchronized", "Cannot join batch in synchronized quiz");
            case BATCHED ->
                quizBatchRepository.findByQuizExerciseAndPassword(quizExercise, password).orElseThrow(() -> new QuizJoinException("quizBatchNotFound", "Batch does not exist"));
            case INDIVIDUAL -> createIndividualBatch(quizExercise, user);
        };
        Optional<QuizBatch> existingBatch = quizBatchRepository.findByQuizExerciseAndStudentLogin(quizExercise, user.getLogin());
        if (quizBatch.isEnded()) {
            throw new QuizJoinException("quizBatchExpired", "Batch has expired");
        }
        else if (existingBatch.isPresent()) {
            throw new QuizJoinException("quizBatchAlreadyJoined", "User is already part of a batch");
        }

        QuizSubmission quizSubmission = quizSubmissionRepository.findByExerciseIdAndStudentLogin(quizExercise.getId(), user.getLogin()).orElseThrow();
        quizSubmission.setQuizBatch(quizBatch.getId());

        quizSubmissionRepository.save(quizSubmission);

        return quizBatch;
    }

    /**
     * create a password for a new batch that was not yet used by another batch
     *
     * @param quizExercise the quiz where the password should not already be in use
     * @return a new unused password
     */
    public String createBatchPassword(QuizExercise quizExercise) {
        for (int i = 0; i < 1000; i++) {
            var password = RandomStringUtils.random(8, 0, 0, false, true, null, SECURE_RANDOM);
            if (quizExercise.getQuizBatches().stream().noneMatch(batch -> password.equals(batch.getPassword()))) {
                return password;
            }
        }
        // this should never happen if there are a reasonable number of batches
        log.error("Unable to create unused batch password; {} batches exist", quizExercise.getQuizBatches().size());
        throw new IllegalStateException("failed to generate a new batch password");
    }

    /**
     * create and save a batch a batched run with a new random password
     *
     * @param quizExercise the quiz where a new batch should be created
     * @param user         the user that created the batch
     * @return the newly created batch
     */
    public QuizBatch createBatch(QuizExercise quizExercise, User user) {
        var batch = new QuizBatch();
        batch.setCreator(user.getId());
        batch.setQuizExercise(quizExercise);
        batch.setPassword(createBatchPassword(quizExercise));
        return save(batch);
    }

    /**
     * create and save a batch an individual run
     *
     * @param quizExercise the quiz where a new batch should be created
     * @param user         the user that created the batch
     * @return the newly created batch
     */
    public QuizBatch createIndividualBatch(QuizExercise quizExercise, User user) {
        var batch = new QuizBatch();
        batch.setCreator(user.getId());
        batch.setQuizExercise(quizExercise);
        batch.setStartTime(quizBatchStartDate(quizExercise, ZonedDateTime.now()));
        return save(batch);
    }

    /**
     * Returns the start time for a batch, given some target start date, that ensures that the batch does not overrun the quiz due date.
     *
     * @param quizExercise the quiz exercise to which the batch belongs
     * @param targetTime   the time when the batch should start if possible
     * @return the minimum of targetTime and the last moment a batch can be started to not overrun the quiz due date; null iff targetTime is null
     */
    public ZonedDateTime quizBatchStartDate(QuizExercise quizExercise, ZonedDateTime targetTime) {
        if (quizExercise.getDueDate() == null || targetTime == null) {
            return targetTime;
        }
        var lastStart = quizExercise.getDueDate().minusSeconds(quizExercise.getDuration() + Constants.QUIZ_GRACE_PERIOD_IN_SECONDS);
        if (lastStart.isBefore(targetTime)) {
            return lastStart;
        }
        return targetTime;
    }

    /**
     * Return the batch that a user the currently participating in for a given quiz exercise
     * Note: This method will definitely include a database read query
     *
     * @param quizExercise the quiz for that the batch should be look up for
     * @param login        the login of the user that the batch should be looked up for
     * @return the batch that the user currently takes part in or empty
     */
    public Optional<QuizBatch> getQuizBatchForStudentByLogin(QuizExercise quizExercise, String login) {
        Optional<QuizBatch> optionalQuizBatch = quizBatchRepository.findByQuizExerciseAndStudentLogin(quizExercise, login);
        if (optionalQuizBatch.isEmpty() && quizExercise.getQuizMode() == QuizMode.SYNCHRONIZED) {
            return Optional.of(getOrCreateSynchronizedQuizBatch(quizExercise));
        }
        return optionalQuizBatch;
    }
}
