package de.tum.cit.aet.artemis.core.service.cleanup;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.List;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.assessment.repository.FeedbackRepository;
import de.tum.cit.aet.artemis.assessment.repository.LongFeedbackTextRepository;
import de.tum.cit.aet.artemis.assessment.repository.ParticipantScoreRepository;
import de.tum.cit.aet.artemis.assessment.repository.RatingRepository;
import de.tum.cit.aet.artemis.assessment.repository.ResultRepository;
import de.tum.cit.aet.artemis.assessment.repository.StudentScoreRepository;
import de.tum.cit.aet.artemis.assessment.repository.TeamScoreRepository;
import de.tum.cit.aet.artemis.assessment.repository.TextBlockRepository;
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

    private final ResultRepository resultRepository;

    private final RatingRepository ratingRepository;

    private final FeedbackRepository feedbackRepository;

    private final TextBlockRepository textBlockRepository;

    private final LongFeedbackTextRepository longFeedbackTextRepository;

    private final StudentScoreRepository studentScoreRepository;

    private final TeamScoreRepository teamScoreRepository;

    private final ParticipantScoreRepository participantScoreRepository;

    public DataCleanupService(CleanupJobExecutionRepository cleanupJobExecutionRepository, PlagiarismComparisonRepository plagiarismComparisonRepository,
            ResultRepository resultRepository, RatingRepository ratingRepository, FeedbackRepository feedbackRepository, TextBlockRepository textBlockRepository,
            LongFeedbackTextRepository longFeedbackTextRepository, StudentScoreRepository studentScoreRepository, TeamScoreRepository teamScoreRepository,
            ParticipantScoreRepository participantScoreRepository) {
        this.resultRepository = resultRepository;
        this.ratingRepository = ratingRepository;
        this.feedbackRepository = feedbackRepository;
        this.textBlockRepository = textBlockRepository;
        this.longFeedbackTextRepository = longFeedbackTextRepository;
        this.studentScoreRepository = studentScoreRepository;
        this.teamScoreRepository = teamScoreRepository;
        this.cleanupJobExecutionRepository = cleanupJobExecutionRepository;
        this.plagiarismComparisonRepository = plagiarismComparisonRepository;
        this.participantScoreRepository = participantScoreRepository;
    }

    /**
     * Deletes orphaned entities that are no longer associated with valid results or participations.
     * This includes feedback, text blocks, and scores that reference null results, participations, or submissions.
     *
     * @return a {@link CleanupServiceExecutionRecordDTO} representing the execution record of the cleanup job
     */
    public CleanupServiceExecutionRecordDTO deleteOrphans() {
        this.longFeedbackTextRepository.deleteLongFeedbackTextForEmptyFeedback();
        this.textBlockRepository.deleteTextBlockForEmptyFeedback();
        this.feedbackRepository.deleteOrphanFeedback();
        this.studentScoreRepository.deleteOrphanStudentScore();
        this.teamScoreRepository.deleteOrphanTeamScore();
        this.longFeedbackTextRepository.deleteLongFeedbackTextForOrphanResult();
        this.textBlockRepository.deleteTextBlockForOrphanResults();
        this.feedbackRepository.deleteFeedbackForOrphanResults();
        this.ratingRepository.deleteOrphanRating();
        this.resultRepository.deleteResultWithoutParticipationAndSubmission();
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
        this.longFeedbackTextRepository.deleteLongFeedbackTextForNonRatedResultsWhereCourseDateBetween(deleteFrom, deleteTo);
        this.textBlockRepository.deleteTextBlockForNonRatedResultsWhereCourseDateBetween(deleteFrom, deleteTo);
        this.participantScoreRepository.deleteParticipantScoresForLatestNonRatedResultsWhereCourseDateBetween(deleteFrom, deleteTo);
        this.participantScoreRepository.deleteParticipantScoresForNonRatedResultsWhereCourseDateBetween(deleteFrom, deleteTo);
        this.feedbackRepository.deleteOldNonRatedFeedbackWhereCourseDateBetween(deleteFrom, deleteTo);
        this.resultRepository.deleteNonRatedResultsWhereCourseDateBetween(deleteFrom, deleteTo);
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
        this.longFeedbackTextRepository.deleteLongFeedbackTextForRatedResultsWhereCourseDateBetween(deleteFrom, deleteTo);
        this.textBlockRepository.deleteTextBlockForRatedResultsWhereCourseDateBetween(deleteFrom, deleteTo);
        this.feedbackRepository.deleteOldFeedbackThatAreNotLatestRatedResultsWhereCourseDateBetween(deleteFrom, deleteTo);
        this.participantScoreRepository.deleteParticipantScoresForNonLatestLastResultsWhereCourseDateBetween(deleteFrom, deleteTo);
        this.participantScoreRepository.deleteParticipantScoresForNonLatestLastRatedResultsWhereCourseDateBetween(deleteFrom, deleteTo);
        this.resultRepository.deleteNonLatestRatedResultsWhereCourseDateBetween(deleteFrom, deleteTo);
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
