package de.tum.cit.aet.artemis.core.service.cleanup;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.List;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.assessment.repository.StudentScoreRepository;
import de.tum.cit.aet.artemis.assessment.repository.TeamScoreRepository;
import de.tum.cit.aet.artemis.assessment.repository.TextBlockRepository;
import de.tum.cit.aet.artemis.assessment.repository.cleanup.FeedbackCleanupRepository;
import de.tum.cit.aet.artemis.assessment.repository.cleanup.LongFeedbackTextCleanupRepository;
import de.tum.cit.aet.artemis.assessment.repository.cleanup.ParticipantScoreCleanupRepository;
import de.tum.cit.aet.artemis.assessment.repository.cleanup.RatingCleanupRepository;
import de.tum.cit.aet.artemis.assessment.repository.cleanup.ResultCleanupRepository;
import de.tum.cit.aet.artemis.core.domain.CleanupJobExecution;
import de.tum.cit.aet.artemis.core.domain.CleanupJobType;
import de.tum.cit.aet.artemis.core.dto.CleanupServiceExecutionRecordDTO;
import de.tum.cit.aet.artemis.core.repository.cleanup.CleanupJobExecutionRepository;
import de.tum.cit.aet.artemis.plagiarism.repository.PlagiarismComparisonRepository;

@Profile(PROFILE_CORE)
@Service
public class DataCleanupService {

    private final CleanupJobExecutionRepository cleanupJobExecutionRepository;

    private final PlagiarismComparisonRepository plagiarismComparisonRepository;

    private final ResultCleanupRepository resultCleanupRepository;

    private final RatingCleanupRepository ratingCleanupRepository;

    private final FeedbackCleanupRepository feedbackCleanupRepository;

    private final TextBlockRepository textBlockRepository;

    private final LongFeedbackTextCleanupRepository longFeedbackTextCleanupRepository;

    private final StudentScoreRepository studentScoreRepository;

    private final TeamScoreRepository teamScoreRepository;

    private final ParticipantScoreCleanupRepository participantScoreCleanupRepository;

    public DataCleanupService(CleanupJobExecutionRepository cleanupJobExecutionRepository, PlagiarismComparisonRepository plagiarismComparisonRepository,
            ResultCleanupRepository resultCleanupRepository, RatingCleanupRepository ratingCleanupRepository, FeedbackCleanupRepository feedbackCleanupRepository,
            TextBlockRepository textBlockRepository, LongFeedbackTextCleanupRepository longFeedbackTextCleanupRepository, StudentScoreRepository studentScoreRepository,
            TeamScoreRepository teamScoreRepository, ParticipantScoreCleanupRepository participantScoreCleanupRepository) {
        this.resultCleanupRepository = resultCleanupRepository;
        this.ratingCleanupRepository = ratingCleanupRepository;
        this.feedbackCleanupRepository = feedbackCleanupRepository;
        this.textBlockRepository = textBlockRepository;
        this.longFeedbackTextCleanupRepository = longFeedbackTextCleanupRepository;
        this.studentScoreRepository = studentScoreRepository;
        this.teamScoreRepository = teamScoreRepository;
        this.cleanupJobExecutionRepository = cleanupJobExecutionRepository;
        this.plagiarismComparisonRepository = plagiarismComparisonRepository;
        this.participantScoreCleanupRepository = participantScoreCleanupRepository;
    }

    /**
     * Deletes orphaned entities that are no longer associated with valid results or participations.
     * This includes feedback, text blocks, and scores that reference null results, participations, or submissions.
     *
     * @return a {@link CleanupServiceExecutionRecordDTO} representing the execution record of the cleanup job
     */
    public CleanupServiceExecutionRecordDTO deleteOrphans() {
        this.longFeedbackTextCleanupRepository.deleteLongFeedbackTextForOrphanedFeedback();
        this.textBlockRepository.deleteTextBlockForEmptyFeedback();
        this.feedbackCleanupRepository.deleteOrphanFeedback();
        this.studentScoreRepository.deleteOrphanStudentScore();
        this.teamScoreRepository.deleteOrphanTeamScore();
        this.longFeedbackTextCleanupRepository.deleteLongFeedbackTextForOrphanResult();
        this.textBlockRepository.deleteTextBlockForOrphanResults();
        this.feedbackCleanupRepository.deleteFeedbackForOrphanResults();
        this.ratingCleanupRepository.deleteOrphanRating();
        this.resultCleanupRepository.deleteResultWithoutParticipationAndSubmission();
        return CleanupServiceExecutionRecordDTO.of(this.createCleanupJobExecution(CleanupJobType.ORPHANS, null, null));
    }

    /**
     * Deletes plagiarism comparisons with a status of "None" that belong to courses within the specified date range.
     * It retrieves the IDs of the plagiarism comparisons matching the criteria, deletes them, and records the execution of the cleanup job.
     *
     * @param deleteFrom the start date for selecting plagiarism comparisons
     * @param deleteTo   the end date for selecting plagiarism comparisons
     * @return a {@link CleanupServiceExecutionRecordDTO} representing the execution record of the cleanup job
     */
    public CleanupServiceExecutionRecordDTO deletePlagiarismComparisons(ZonedDateTime deleteFrom, ZonedDateTime deleteTo) {
        var pcIds = plagiarismComparisonRepository.findPlagiarismComparisonIdWithStatusNoneThatBelongToCourseWithDates(deleteFrom, deleteTo);
        plagiarismComparisonRepository.deleteByIdIn(pcIds);
        return CleanupServiceExecutionRecordDTO.of(this.createCleanupJobExecution(CleanupJobType.PLAGIARISM_COMPARISONS, deleteFrom, deleteTo));
    }

    /**
     * Deletes non-rated results within the specified date range, along with associated long feedback texts,
     * text blocks, feedback items, and participant scores.
     *
     * @param deleteFrom The start of the date range for deleting non-rated results.
     * @param deleteTo   The end of the date range for deleting non-rated results.
     * @return a {@link CleanupServiceExecutionRecordDTO} representing the execution record of the cleanup job
     */
    public CleanupServiceExecutionRecordDTO deleteNonRatedResults(ZonedDateTime deleteFrom, ZonedDateTime deleteTo) {
        this.longFeedbackTextCleanupRepository.deleteLongFeedbackTextForNonRatedResultsWhereCourseDateBetween(deleteFrom, deleteTo);
        this.textBlockRepository.deleteTextBlockForNonRatedResultsWhereCourseDateBetween(deleteFrom, deleteTo);
        this.participantScoreCleanupRepository.deleteParticipantScoresForLatestNonRatedResultsWhereCourseDateBetween(deleteFrom, deleteTo);
        this.participantScoreCleanupRepository.deleteParticipantScoresForNonRatedResultsWhereCourseDateBetween(deleteFrom, deleteTo);
        this.feedbackCleanupRepository.deleteOldNonRatedFeedbackWhereCourseDateBetween(deleteFrom, deleteTo);
        this.resultCleanupRepository.deleteNonRatedResultsWhereCourseDateBetween(deleteFrom, deleteTo);
        return CleanupServiceExecutionRecordDTO.of(this.createCleanupJobExecution(CleanupJobType.NON_RATED_RESULTS, deleteFrom, deleteTo));
    }

    /**
     * Deletes rated results, excluding the latest rated result for each participation, for courses conducted within the specified date range.
     * Also deletes associated long feedback texts, text blocks, feedback items, and participant scores.
     *
     * @param deleteFrom The start of the date range for deleting rated results.
     * @param deleteTo   The end of the date range for deleting rated results.
     * @return a {@link CleanupServiceExecutionRecordDTO} representing the execution record of the cleanup job
     */
    public CleanupServiceExecutionRecordDTO deleteNonLatestRatedResults(ZonedDateTime deleteFrom, ZonedDateTime deleteTo) {
        this.longFeedbackTextCleanupRepository.deleteLongFeedbackTextForRatedResultsWhereCourseDateBetween(deleteFrom, deleteTo);
        this.textBlockRepository.deleteTextBlockForRatedResultsWhereCourseDateBetween(deleteFrom, deleteTo);
        this.feedbackCleanupRepository.deleteOldFeedbackThatAreNotLatestRatedResultsWhereCourseDateBetween(deleteFrom, deleteTo);
        this.participantScoreCleanupRepository.deleteParticipantScoresForNonLatestLastResultsWhereCourseDateBetween(deleteFrom, deleteTo);
        this.participantScoreCleanupRepository.deleteParticipantScoresForNonLatestLastRatedResultsWhereCourseDateBetween(deleteFrom, deleteTo);
        this.resultCleanupRepository.deleteNonLatestRatedResultsWhereCourseDateBetween(deleteFrom, deleteTo);
        return CleanupServiceExecutionRecordDTO.of(this.createCleanupJobExecution(CleanupJobType.RATED_RESULTS, deleteFrom, deleteTo));
    }

    /**
     * Retrieves the last execution record for each cleanup job type.
     * This method returns the most recent execution of each cleanup job type by querying
     * the {@link CleanupJobExecutionRepository} for the latest execution based on the
     * deletion timestamp. If no execution is found for a job type, a default
     * {@link CleanupServiceExecutionRecordDTO} with a {@code null} execution and the job
     * type's label is returned.
     *
     * @return a list of {@link CleanupServiceExecutionRecordDTO} objects representing
     *         the last execution record for each cleanup job type
     */
    public List<CleanupServiceExecutionRecordDTO> getLastExecutions() {
        return Arrays.stream(CleanupJobType.values()).map(jobType -> {
            CleanupJobExecution lastExecution = cleanupJobExecutionRepository.findTopByCleanupJobTypeOrderByDeletionTimestampDesc(jobType);
            return lastExecution != null ? CleanupServiceExecutionRecordDTO.of(lastExecution) : new CleanupServiceExecutionRecordDTO(null, jobType.label());
        }).toList();
    }

    private CleanupJobExecution createCleanupJobExecution(CleanupJobType cleanupJobType, ZonedDateTime deleteFrom, ZonedDateTime deleteTo) {
        var entry = new CleanupJobExecution();
        entry.setCleanupJobType(cleanupJobType);
        if (deleteFrom != null) {
            entry.setDeleteFrom(deleteFrom);
        }
        if (deleteTo != null) {
            entry.setDeleteTo(deleteTo);
        }
        entry.setDeletionTimestamp(ZonedDateTime.now());
        return this.cleanupJobExecutionRepository.save(entry);
    }
}
