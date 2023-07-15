package de.tum.in.www1.artemis.service;

import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.config.Constants;
import de.tum.in.www1.artemis.domain.Result;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.enumeration.QuizMode;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.domain.quiz.*;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.service.scheduled.cache.quiz.QuizScheduleService;
import de.tum.in.www1.artemis.web.rest.dto.PageableSearchDTO;
import de.tum.in.www1.artemis.web.rest.dto.SearchResultPageDTO;
import de.tum.in.www1.artemis.web.rest.util.PageUtil;

@Service
public class QuizExerciseService extends QuizService<QuizExercise> {

    private final Logger log = LoggerFactory.getLogger(QuizExerciseService.class);

    private final QuizExerciseRepository quizExerciseRepository;

    private final ResultRepository resultRepository;

    private final QuizSubmissionRepository quizSubmissionRepository;

    private final QuizScheduleService quizScheduleService;

    private final QuizStatisticService quizStatisticService;

    private final QuizBatchService quizBatchService;

    private final ExerciseSpecificationService exerciseSpecificationService;

    public QuizExerciseService(QuizExerciseRepository quizExerciseRepository, DragAndDropMappingRepository dragAndDropMappingRepository, ResultRepository resultRepository,
            ShortAnswerMappingRepository shortAnswerMappingRepository, QuizSubmissionRepository quizSubmissionRepository, QuizScheduleService quizScheduleService,
            QuizStatisticService quizStatisticService, QuizBatchService quizBatchService, ExerciseSpecificationService exerciseSpecificationService) {
        super(dragAndDropMappingRepository, shortAnswerMappingRepository);
        this.quizExerciseRepository = quizExerciseRepository;
        this.resultRepository = resultRepository;
        this.quizSubmissionRepository = quizSubmissionRepository;
        this.quizScheduleService = quizScheduleService;
        this.quizStatisticService = quizStatisticService;
        this.quizBatchService = quizBatchService;
        this.exerciseSpecificationService = exerciseSpecificationService;
    }

    /**
     * adjust existing results if an answer or and question was deleted and recalculate the scores
     *
     * @param quizExercise the changed quizExercise.
     */
    private void updateResultsOnQuizChanges(QuizExercise quizExercise) {
        // change existing results if an answer or and question was deleted
        List<Result> results = resultRepository.findByParticipationExerciseIdOrderByCompletionDateAsc(quizExercise.getId());
        log.info("Found {} results to update for quiz re-evaluate", results.size());
        List<QuizSubmission> submissions = new ArrayList<>();
        for (Result result : results) {

            Set<SubmittedAnswer> submittedAnswersToDelete = new HashSet<>();
            QuizSubmission quizSubmission = quizSubmissionRepository.findWithEagerSubmittedAnswersById(result.getSubmission().getId());
            result.setSubmission(quizSubmission);

            for (SubmittedAnswer submittedAnswer : quizSubmission.getSubmittedAnswers()) {
                // Delete all references to question and question-elements if the question was changed
                submittedAnswer.checkAndDeleteReferences(quizExercise);
                if (!quizExercise.getQuizQuestions().contains(submittedAnswer.getQuizQuestion())) {
                    submittedAnswersToDelete.add(submittedAnswer);
                }
            }
            quizSubmission.getSubmittedAnswers().removeAll(submittedAnswersToDelete);

            // recalculate existing score
            quizSubmission.calculateAndUpdateScores(quizExercise.getQuizQuestions());
            // update Successful-Flag in Result
            StudentParticipation studentParticipation = (StudentParticipation) result.getParticipation();
            studentParticipation.setExercise(quizExercise);
            result.evaluateQuizSubmission();

            submissions.add(quizSubmission);
        }
        // save the updated submissions and results
        quizSubmissionRepository.saveAll(submissions);
        resultRepository.saveAll(results);
        log.info("{} results have been updated successfully for quiz re-evaluate", results.size());
    }

    /**
     * @param quizExercise         the changed quiz exercise from the client
     * @param originalQuizExercise the original quiz exercise (with statistics)
     * @return the updated quiz exercise with the changed statistics
     */
    public QuizExercise reEvaluate(QuizExercise quizExercise, QuizExercise originalQuizExercise) {

        quizExercise.undoUnallowedChanges(originalQuizExercise);
        boolean updateOfResultsAndStatisticsNecessary = quizExercise.checkIfRecalculationIsNecessary(originalQuizExercise);

        // update QuizExercise
        quizExercise.setMaxPoints(quizExercise.getOverallQuizPoints());
        quizExercise.reconnectJSONIgnoreAttributes();

        // adjust existing results if an answer or a question was deleted and recalculate them
        updateResultsOnQuizChanges(quizExercise);

        QuizExercise savedQuizExercise = save(quizExercise);

        if (updateOfResultsAndStatisticsNecessary) {
            // make sure we have all objects available before updating the statistics to avoid lazy / proxy issues
            savedQuizExercise = quizExerciseRepository.findByIdWithQuestionsAndStatisticsElseThrow(savedQuizExercise.getId());
            quizStatisticService.recalculateStatistics(savedQuizExercise);
        }
        // fetch the quiz exercise again to make sure the latest changes are included
        return quizExerciseRepository.findByIdWithQuestionsAndStatisticsElseThrow(savedQuizExercise.getId());
    }

    /**
     * Reset a QuizExercise to its original state, delete statistics and cleanup the schedule service.
     *
     * @param exerciseId id of the exercise to reset
     */
    public void resetExercise(Long exerciseId) {
        // fetch exercise again to make sure we have an updated version
        QuizExercise quizExercise = quizExerciseRepository.findByIdWithQuestionsAndStatisticsElseThrow(exerciseId);

        // for quizzes, we need to delete the statistics, and we need to reset the quiz to its original state
        quizExercise.setIsOpenForPractice(Boolean.FALSE);
        if (!quizExercise.isExamExercise()) {
            // do not set the release date of exam exercises
            quizExercise.setReleaseDate(ZonedDateTime.now());
        }
        quizExercise.setDueDate(null);
        quizExercise.setQuizBatches(Set.of());

        resetInvalidQuestions(quizExercise);

        QuizExercise savedQuizExercise = save(quizExercise);

        // in case the quiz has not yet started or the quiz is currently running, we have to clean up
        quizScheduleService.cancelScheduledQuizStart(savedQuizExercise.getId());
        quizScheduleService.clearQuizData(savedQuizExercise.getId());

        // clean up the statistics
        quizStatisticService.recalculateStatistics(savedQuizExercise);
    }

    public void cancelScheduledQuiz(Long quizExerciseId) {
        quizScheduleService.cancelScheduledQuizStart(quizExerciseId);
        quizScheduleService.clearQuizData(quizExerciseId);
    }

    /**
     * Update a QuizExercise so that it ends at a specific date and moves the start date of the batches as required. Does not save the quiz.
     *
     * @param quizExercise The quiz to end
     * @param endDate      When the quize should end
     */
    public void endQuiz(QuizExercise quizExercise, ZonedDateTime endDate) {
        quizExercise.setDueDate(ZonedDateTime.now().truncatedTo(ChronoUnit.SECONDS));
        quizExercise.getQuizBatches().forEach(batch -> batch.setStartTime(quizBatchService.quizBatchStartDate(quizExercise, batch.getStartTime())));
    }

    /**
     * Search for all quiz exercises fitting a {@link PageableSearchDTO search query}. The result is paged,
     * meaning that there is only a predefined portion of the result returned to the user, so that the server doesn't
     * have to send hundreds/thousands of exercises if there are that many in Artemis.
     *
     * @param search         The search query defining the search term and the size of the returned page
     * @param isCourseFilter Whether to search in courses for exercises
     * @param isExamFilter   Whether to search in exams for exercises
     * @param user           The user for whom to fetch all available exercises
     * @return A wrapper object containing a list of all found exercises and the total number of pages
     */
    public SearchResultPageDTO<QuizExercise> getAllOnPageWithSize(final PageableSearchDTO<String> search, final Boolean isCourseFilter, final Boolean isExamFilter,
            final User user) {
        if (!isCourseFilter && !isExamFilter) {
            return new SearchResultPageDTO<>(Collections.emptyList(), 0);
        }
        final var pageable = PageUtil.createExercisePageRequest(search);
        final var searchTerm = search.getSearchTerm();
        Specification<QuizExercise> specification = exerciseSpecificationService.getExerciseSearchSpecification(searchTerm, isCourseFilter, isExamFilter, user, pageable);
        Page<QuizExercise> exercisePage = quizExerciseRepository.findAll(specification, pageable);
        return new SearchResultPageDTO<>(exercisePage.getContent(), exercisePage.getTotalPages());
    }

    /**
     * Reset the invalid status of questions of given quizExercise to false
     *
     * @param quizExercise The quiz exercise which questions to be reset
     */
    private void resetInvalidQuestions(QuizExercise quizExercise) {
        for (QuizQuestion question : quizExercise.getQuizQuestions()) {
            question.setInvalid(false);
        }
    }

    @Override
    public QuizExercise save(QuizExercise quizExercise) {
        quizExercise.setMaxPoints(quizExercise.getOverallQuizPoints());

        // create a quizPointStatistic if it does not yet exist
        if (quizExercise.getQuizPointStatistic() == null) {
            QuizPointStatistic quizPointStatistic = new QuizPointStatistic();
            quizExercise.setQuizPointStatistic(quizPointStatistic);
            quizPointStatistic.setQuiz(quizExercise);
        }

        // make sure the pointers in the statistics are correct
        quizExercise.recalculatePointCounters();

        QuizExercise savedQuizExercise = super.save(quizExercise);

        if (savedQuizExercise.isCourseExercise()) {
            // only schedule quizzes for course exercises, not for exam exercises
            quizScheduleService.scheduleQuizStart(savedQuizExercise.getId());
        }

        return savedQuizExercise;
    }

    @Override
    protected QuizExercise saveAndFlush(QuizExercise quizExercise) {
        if (quizExercise.getQuizBatches() != null) {
            for (QuizBatch quizBatch : quizExercise.getQuizBatches()) {
                quizBatch.setQuizExercise(quizExercise);
                if (quizExercise.getQuizMode() == QuizMode.SYNCHRONIZED) {
                    if (quizBatch.getStartTime() != null) {
                        quizExercise.setDueDate(quizBatch.getStartTime().plusSeconds(quizExercise.getDuration() + Constants.QUIZ_GRACE_PERIOD_IN_SECONDS));
                    }
                }
                else {
                    quizBatch.setStartTime(quizBatchService.quizBatchStartDate(quizExercise, quizBatch.getStartTime()));
                }
            }
        }

        // Note: save will automatically remove deleted questions from the exercise and deleted answer options from the questions
        // and delete the now orphaned entries from the database
        log.debug("Save quiz exercise to database: {}", quizExercise);
        return quizExerciseRepository.saveAndFlush(quizExercise);
    }
}
