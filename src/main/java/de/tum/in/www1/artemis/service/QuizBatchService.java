package de.tum.in.www1.artemis.service;

import java.security.SecureRandom;
import java.time.ZonedDateTime;
import java.util.*;

import javax.annotation.Nullable;

import org.apache.commons.lang.RandomStringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.config.Constants;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.enumeration.QuizMode;
import de.tum.in.www1.artemis.domain.quiz.*;
import de.tum.in.www1.artemis.exception.QuizJoinException;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.service.scheduled.cache.quiz.QuizScheduleService;

@Service
public class QuizBatchService {

    // copied from tech.jhipster.security.RandomUtil
    private static final SecureRandom SECURE_RANDOM;

    static {
        SECURE_RANDOM = new SecureRandom();
        SECURE_RANDOM.nextBytes(new byte[64]);
    }

    private final Logger log = LoggerFactory.getLogger(QuizBatchService.class);

    private final QuizBatchRepository quizBatchRepository;

    private final QuizScheduleService quizScheduleService;

    public QuizBatchService(QuizBatchRepository quizBatchRepository, QuizScheduleService quizScheduleService) {
        this.quizBatchRepository = quizBatchRepository;
        this.quizScheduleService = quizScheduleService;
    }

    /**
     * Save the given quizBatch to the database
     *
     * @param quizBatch the quiz batch  to save
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

        if (quizExercise.getQuizBatches() == null) {
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
     * @param quizExercise the quiz of the batch to join
     * @param user the user to join
     * @param password the password of the batch to join; unused for INDIVIDUAL mode
     * @param batchId the id of the batch to join without a password; currently not implemented; unused for INDIVIDUAL mode
     * @return the batch that was joined, or empty if the batch could not be found
     */
    public QuizBatch joinBatch(QuizExercise quizExercise, User user, @Nullable String password, @Nullable Long batchId) throws QuizJoinException {
        QuizBatch quizBatch = switch (quizExercise.getQuizMode()) {
            case SYNCHRONIZED -> throw new QuizJoinException("quizBatchJoinSynchronized", "Cannot join batch in synchronized quiz");
            case BATCHED -> quizBatchRepository.findByQuizExerciseAndPassword(quizExercise, password)
                    .orElseThrow(() -> new QuizJoinException("quizBatchNotFound", "Batch does not exist"));
            case INDIVIDUAL -> createIndividualBatch(quizExercise, user);
        };

        if (quizBatch.isEnded()) {
            throw new QuizJoinException("quizBatchExpired", "Batch has expired");
        }

        quizScheduleService.joinQuizBatch(quizExercise, quizBatch, user);
        return quizBatch;
    }

    /**
     * create a password for a new batch that was not yet used by another batch
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
     * @param quizExercise the quiz where a new batch should be created
     * @param user the user that created the batch
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
     * @param quizExercise the quiz where a new batch should be created
     * @param user the user that created the batch
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
     * @param quizExercise the quiz exercise to which the batch belongs
     * @param targetTime the time when the batch should start if possible
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
     * Return the batch that a user the currently participating in for a given exercise
     * @param quizExercise the quiz for that the batch should be look up for
     * @param login the login of the user that the batch should be looked up for
     * @return the batch that the user currently takes part in or empty
     */
    public Optional<QuizBatch> getQuizBatchForStudentByLogin(QuizExercise quizExercise, String login) {
        var batch = quizScheduleService.getQuizBatchForStudentByLogin(quizExercise, login);
        if (batch.isEmpty() && quizExercise.getQuizMode() == QuizMode.SYNCHRONIZED) {
            return Optional.of(getOrCreateSynchronizedQuizBatch(quizExercise));
        }
        if (quizExercise.getQuizBatches() != null && batch.isPresent()) {
            final Long batchId = batch.get();
            return quizExercise.getQuizBatches().stream().filter(b -> Objects.equals(b.getId(), batchId)).findAny().or(() -> quizBatchRepository.findById(batchId));
        }
        return batch.flatMap(quizBatchRepository::findById);
    }
}
