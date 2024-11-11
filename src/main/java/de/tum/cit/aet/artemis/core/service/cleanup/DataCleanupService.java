package de.tum.cit.aet.artemis.core.service.cleanup;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.assessment.repository.cleanup.FeedbackCleanupRepository;
import de.tum.cit.aet.artemis.assessment.repository.cleanup.LongFeedbackTextCleanupRepository;
import de.tum.cit.aet.artemis.assessment.repository.cleanup.ParticipantScoreCleanupRepository;
import de.tum.cit.aet.artemis.assessment.repository.cleanup.PlagiarismComparisonCleanupRepository;
import de.tum.cit.aet.artemis.assessment.repository.cleanup.RatingCleanupRepository;
import de.tum.cit.aet.artemis.assessment.repository.cleanup.ResultCleanupRepository;
import de.tum.cit.aet.artemis.assessment.repository.cleanup.StudentScoreCleanupRepository;
import de.tum.cit.aet.artemis.assessment.repository.cleanup.TeamScoreCleanupRepository;
import de.tum.cit.aet.artemis.assessment.repository.cleanup.TextBlockCleanupRepository;
import de.tum.cit.aet.artemis.core.domain.CleanupJobExecution;
import de.tum.cit.aet.artemis.core.domain.CleanupJobType;
import de.tum.cit.aet.artemis.core.dto.CleanupServiceExecutionRecordDTO;
import de.tum.cit.aet.artemis.core.repository.cleanup.CleanupJobExecutionRepository;

@Profile(PROFILE_CORE)
@Service
public class DataCleanupService {

    private static final Logger log = LoggerFactory.getLogger(DataCleanupService.class);

    private final CleanupJobExecutionRepository cleanupJobExecutionRepository;

    private final PlagiarismComparisonCleanupRepository plagiarismComparisonCleanupRepository;

    private final ResultCleanupRepository resultCleanupRepository;

    private final RatingCleanupRepository ratingCleanupRepository;

    private final FeedbackCleanupRepository feedbackCleanupRepository;

    private final TextBlockCleanupRepository textBlockCleanupRepository;

    private final LongFeedbackTextCleanupRepository longFeedbackTextCleanupRepository;

    private final StudentScoreCleanupRepository studentScoreCleanupRepository;

    private final TeamScoreCleanupRepository teamScoreCleanupRepository;

    private final ParticipantScoreCleanupRepository participantScoreCleanupRepository;

    public DataCleanupService(CleanupJobExecutionRepository cleanupJobExecutionRepository, PlagiarismComparisonCleanupRepository plagiarismComparisonCleanupRepository,
            ResultCleanupRepository resultCleanupRepository, RatingCleanupRepository ratingCleanupRepository, FeedbackCleanupRepository feedbackCleanupRepository,
            TextBlockCleanupRepository textBlockCleanupRepository, LongFeedbackTextCleanupRepository longFeedbackTextCleanupRepository,
            StudentScoreCleanupRepository studentScoreCleanupRepository, TeamScoreCleanupRepository teamScoreCleanupRepository,
            ParticipantScoreCleanupRepository participantScoreCleanupRepository) {
        this.resultCleanupRepository = resultCleanupRepository;
        this.ratingCleanupRepository = ratingCleanupRepository;
        this.feedbackCleanupRepository = feedbackCleanupRepository;
        this.textBlockCleanupRepository = textBlockCleanupRepository;
        this.longFeedbackTextCleanupRepository = longFeedbackTextCleanupRepository;
        this.studentScoreCleanupRepository = studentScoreCleanupRepository;
        this.teamScoreCleanupRepository = teamScoreCleanupRepository;
        this.cleanupJobExecutionRepository = cleanupJobExecutionRepository;
        this.plagiarismComparisonCleanupRepository = plagiarismComparisonCleanupRepository;
        this.participantScoreCleanupRepository = participantScoreCleanupRepository;
    }

    // TODO: offer the possibility to delete old submission versions

    /**
     * Deletes orphaned entities that are no longer associated with valid results or participations.
     * This includes feedback, text blocks, and scores that reference null results, participations, or submissions.
     *
     * @return a {@link CleanupServiceExecutionRecordDTO} representing the execution record of the cleanup job
     */
    public CleanupServiceExecutionRecordDTO deleteOrphans() {
        int deletedLongFeedbackTexts = longFeedbackTextCleanupRepository.deleteLongFeedbackTextForOrphanedFeedback();
        log.info("Deleted {} orphaned long feedback texts", deletedLongFeedbackTexts);

        int deletedTextBlocks = textBlockCleanupRepository.deleteTextBlockForEmptyFeedback();
        log.info("Deleted {} text blocks for empty feedback", deletedTextBlocks);

        int deletedOrphanFeedback = feedbackCleanupRepository.deleteOrphanFeedback();
        log.info("Deleted {} orphaned feedback entries", deletedOrphanFeedback);

        int deletedOrphanStudentScores = studentScoreCleanupRepository.deleteOrphanStudentScore();
        log.info("Deleted {} orphaned student scores", deletedOrphanStudentScores);

        int deletedOrphanTeamScores = teamScoreCleanupRepository.deleteOrphanTeamScore();
        log.info("Deleted {} orphaned team scores", deletedOrphanTeamScores);

        int deletedLongFeedbackTextsForOrphanResult = longFeedbackTextCleanupRepository.deleteLongFeedbackTextForOrphanResult();
        log.info("Deleted {} long feedback texts for orphan results", deletedLongFeedbackTextsForOrphanResult);

        int deletedTextBlocksForOrphanResults = textBlockCleanupRepository.deleteTextBlockForOrphanResults();
        log.info("Deleted {} text blocks for orphan results", deletedTextBlocksForOrphanResults);

        int deletedFeedbackForOrphanResults = feedbackCleanupRepository.deleteFeedbackForOrphanResults();
        log.info("Deleted {} feedback entries for orphan results", deletedFeedbackForOrphanResults);

        int deletedOrphanRatings = ratingCleanupRepository.deleteOrphanRating();
        log.info("Deleted {} orphan ratings", deletedOrphanRatings);

        int deletedResultsWithoutParticipation = resultCleanupRepository.deleteResultWithoutParticipationAndSubmission();
        log.info("Deleted {} results without participation and submission", deletedResultsWithoutParticipation);

        return CleanupServiceExecutionRecordDTO.of(createCleanupJobExecution(CleanupJobType.ORPHANS, null, null));
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
        var pcIds = plagiarismComparisonCleanupRepository.findPlagiarismComparisonIdWithStatusNoneThatBelongToCourseWithDates(deleteFrom, deleteTo);
        log.info("Deleting {} plagiarism comparisons with status 'None' between {} and {}", pcIds.size(), deleteFrom, deleteTo);

        // NOTE: we first need to delete related data to avoid foreign key constraints
        // Delete all plagiarism elements that are part of the plagiarism submissions
        int deletedPlagiarismElements = plagiarismComparisonCleanupRepository.deletePlagiarismSubmissionElementsByComparisonIdsIn(pcIds);
        log.info("Deleted {} plagiarism elements that are part of the plagiarism submissions", deletedPlagiarismElements);

        // NOTE: we need to set submissionA and submissionB to null first to avoid foreign key constraints
        int updatedPlagiarismComparisons = plagiarismComparisonCleanupRepository.setPlagiarismSubmissionsToNullInComparisonsWithIds(pcIds);
        log.info("Updated {} plagiarism comparisons to set plagiarism submissions to null", updatedPlagiarismComparisons);

        // Delete all plagiarism submissions that reference plagiarism comparisons
        int deletedPlagiarismSubmissions = plagiarismComparisonCleanupRepository.deletePlagiarismSubmissionsByComparisonIdsIn(pcIds);
        log.info("Deleted {} plagiarism submissions that reference plagiarism comparisons", deletedPlagiarismSubmissions);

        // Delete all plagiarism comparison matches that reference plagiarism comparisons
        int deletedPlagiarismComparisonMatches = plagiarismComparisonCleanupRepository.deletePlagiarismComparisonMatchesByComparisonIdsIn(pcIds);
        log.info("Deleted {} plagiarism comparison matches that reference plagiarism comparisons", deletedPlagiarismComparisonMatches);

        int deletedPCs = plagiarismComparisonCleanupRepository.deleteByIdsIn(pcIds);
        log.info("Deleted {} plagiarism comparisons with status 'None'", deletedPCs);
        return CleanupServiceExecutionRecordDTO.of(createCleanupJobExecution(CleanupJobType.PLAGIARISM_COMPARISONS, deleteFrom, deleteTo));
    }

    /**
     * Deletes non-rated results, excluding the latest non-rated result for each participation(to be able to compute Competencies Scores), within the specified date range, along
     * with associated long feedback texts,
     * text blocks, feedback items, and participant scores.
     *
     * @param deleteFrom The start of the date range for deleting non-rated results.
     * @param deleteTo   The end of the date range for deleting non-rated results.
     * @return a {@link CleanupServiceExecutionRecordDTO} representing the execution record of the cleanup job
     */
    public CleanupServiceExecutionRecordDTO deleteNonLatestNonRatedResultsFeedback(ZonedDateTime deleteFrom, ZonedDateTime deleteTo) {
        int deletedLongFeedbackTexts = longFeedbackTextCleanupRepository.deleteLongFeedbackTextForNonRatedResultsWhereCourseDateBetween(deleteFrom, deleteTo);
        log.info("Deleted {} long feedback texts for non-rated results between {} and {}", deletedLongFeedbackTexts, deleteFrom, deleteTo);

        int deletedTextBlocks = textBlockCleanupRepository.deleteTextBlockForNonRatedResultsWhereCourseDateBetween(deleteFrom, deleteTo);
        log.info("Deleted {} text blocks for non-rated results between {} and {}", deletedTextBlocks, deleteFrom, deleteTo);

        int deletedFeedback = feedbackCleanupRepository.deleteOldNonRatedFeedbackWhereCourseDateBetween(deleteFrom, deleteTo);
        log.info("Deleted {} feedback entries for non-rated results between {} and {}", deletedFeedback, deleteFrom, deleteTo);

        // TODO: old results and participant scores should not be deleted automatically: if at all this could be offered as an option for the admin
        // int deletedParticipantScoresForLatest = participantScoreCleanupRepository.deleteParticipantScoresForLatestNonRatedResultsWhereCourseDateBetween(deleteFrom, deleteTo);
        // log.info("Deleted {} participant scores for latest non-rated results between {} and {}", deletedParticipantScoresForLatest, deleteFrom, deleteTo);
        //
        // int deletedParticipantScores = participantScoreCleanupRepository.deleteParticipantScoresForNonRatedResultsWhereCourseDateBetween(deleteFrom, deleteTo);
        // log.info("Deleted {} participant scores for non-rated results between {} and {}", deletedParticipantScores, deleteFrom, deleteTo);

        // int deletedNonLatestNonRatedResults = resultCleanupRepository.deleteNonLatestNonRatedResultsWhereCourseDateBetween(deleteFrom, deleteTo);
        // log.info("Deleted {} non-latest non-rated results between {} and {}", deletedNonLatestNonRatedResults, deleteFrom, deleteTo);

        return CleanupServiceExecutionRecordDTO.of(createCleanupJobExecution(CleanupJobType.NON_RATED_RESULTS, deleteFrom, deleteTo));
    }

    /**
     * Deletes rated results, excluding the latest rated result for each participation, for courses conducted within the specified date range.
     * Also deletes associated long feedback texts, text blocks, feedback items, and participant scores.
     *
     * @param deleteFrom The start of the date range for deleting rated results.
     * @param deleteTo   The end of the date range for deleting rated results.
     * @return a {@link CleanupServiceExecutionRecordDTO} representing the execution record of the cleanup job
     */
    public CleanupServiceExecutionRecordDTO deleteNonLatestRatedResultsFeedback(ZonedDateTime deleteFrom, ZonedDateTime deleteTo) {
        int deletedLongFeedbackTexts = longFeedbackTextCleanupRepository.deleteLongFeedbackTextForRatedResultsWhereCourseDateBetween(deleteFrom, deleteTo);
        log.info("Deleted {} long feedback texts for rated results between {} and {}", deletedLongFeedbackTexts, deleteFrom, deleteTo);

        int deletedTextBlocks = textBlockCleanupRepository.deleteTextBlockForRatedResultsWhereCourseDateBetween(deleteFrom, deleteTo);
        log.info("Deleted {} text blocks for rated results between {} and {}", deletedTextBlocks, deleteFrom, deleteTo);

        int deletedFeedback = feedbackCleanupRepository.deleteOldFeedbackThatAreNotLatestRatedResultsWhereCourseDateBetween(deleteFrom, deleteTo);
        log.info("Deleted {} feedback entries for rated results between {} and {}", deletedFeedback, deleteFrom, deleteTo);

        // TODO: old results and participant scores should not be deleted automatically: if at all this could be offered as an option for the admin
        // int deletedParticipantScoresForNonLatest = participantScoreCleanupRepository.deleteParticipantScoresForNonLatestLastResultsWhereCourseDateBetween(deleteFrom, deleteTo);
        // log.info("Deleted {} participant scores for non-latest rated results between {} and {}", deletedParticipantScoresForNonLatest, deleteFrom, deleteTo);
        //
        // int deletedParticipantScoresForNonLatestLast = participantScoreCleanupRepository.deleteParticipantScoresForNonLatestLastRatedResultsWhereCourseDateBetween(deleteFrom,
        // deleteTo);
        // log.info("Deleted {} participant scores for non-latest last rated results between {} and {}", deletedParticipantScoresForNonLatestLast, deleteFrom, deleteTo);

        // int deletedNonLatestRatedResults = resultCleanupRepository.deleteNonLatestRatedResultsWhereCourseDateBetween(deleteFrom, deleteTo);
        // log.info("Deleted {} non-latest rated results between {} and {}", deletedNonLatestRatedResults, deleteFrom, deleteTo);

        return CleanupServiceExecutionRecordDTO.of(createCleanupJobExecution(CleanupJobType.RATED_RESULTS, deleteFrom, deleteTo));
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
        return cleanupJobExecutionRepository.save(entry);
    }
}
