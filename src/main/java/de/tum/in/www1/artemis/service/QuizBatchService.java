package de.tum.in.www1.artemis.service;

import java.time.ZonedDateTime;
import java.util.*;

import javax.annotation.Nullable;

import org.apache.commons.lang.RandomStringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.annotation.JsonIgnore;

import de.tum.in.www1.artemis.config.Constants;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.enumeration.QuizMode;
import de.tum.in.www1.artemis.domain.quiz.*;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.service.scheduled.quiz.QuizScheduleService;

@Service
public class QuizBatchService {

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

    public void loadBatchesIfMissing(QuizExercise quizExercise) {
        if (quizExercise.getQuizBatches() == null) {
            quizExercise.setQuizBatches(quizBatchRepository.findAllByQuizExercise(quizExercise));
        }
    }

    /**
     * Get or create the batch for synchronized quiz exercises. If it was created it will not have been saved to the database yet.
     * Only valid to call for synchronized quiz exercises
     *
     * @return the single batch of the exercise
     */
    @JsonIgnore
    public QuizBatch getOrCreateSynchronizedQuizBatch(QuizExercise quizExercise) {
        if (quizExercise.getQuizMode() != QuizMode.SYNCHRONIZED) {
            throw new IllegalStateException();
        }

        loadBatchesIfMissing(quizExercise);

        var batch = quizExercise.getQuizBatches().stream().findAny();
        if (batch.isPresent()) {
            return batch.get();
        }

        var quizBatch = new QuizBatch();
        quizBatch.setQuizExercise(quizExercise);
        quizExercise.setQuizBatches(Set.of(quizBatch));
        return quizBatch;
    }

    public Optional<QuizBatch> joinBatch(QuizExercise quizExercise, User user, @Nullable String password, @Nullable Long batchId) {
        Optional<QuizBatch> quizBatch = switch (quizExercise.getQuizMode()) {
            case SYNCHRONIZED -> Optional.empty();
            case BATCHED -> quizBatchRepository.findByQuizExerciseAndPassword(quizExercise, password);
            case INDIVIDUAL -> Optional.of(createIndividualBatch(quizExercise, user));
        };

        quizBatch.ifPresent(batch -> quizScheduleService.joinQuizBatch(quizExercise, batch, user));
        return quizBatch;
    }

    public String createBatchPassword(QuizExercise quizExercise) {
        loadBatchesIfMissing(quizExercise);
        for (int i = 0; i < 1000; i++) {
            var password = RandomStringUtils.randomNumeric(8);
            if (quizExercise.getQuizBatches().stream().noneMatch(batch -> password.equals(batch.getPassword()))) {
                return password;
            }
        }
        throw new RuntimeException("failed to generate a new batch password");
    }

    /**
     * create and save a batch a batched run
     * @param quizExercise
     * @param user
     * @return
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
     * @param quizExercise
     * @param user
     * @return
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
     * @param quizExercise
     * @param targetTime
     * @return
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

    public Optional<QuizBatch> getQuizBatchForStudent(QuizExercise quizExercise, User user) {
        var batch = quizScheduleService.getQuizBatchForStudent(quizExercise, user);
        if (batch.isEmpty() && quizExercise.getQuizMode() == QuizMode.SYNCHRONIZED) {
            return Optional.of(getOrCreateSynchronizedQuizBatch(quizExercise));
        }
        return batch.flatMap(quizBatchRepository::findById);
    }
}
