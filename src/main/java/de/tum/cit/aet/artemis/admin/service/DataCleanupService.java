package de.tum.cit.aet.artemis.admin.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.dto.UserDeletionImpactDTO;
import de.tum.cit.aet.artemis.account.dto.UserDeletionResultStatus;
import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.account.service.UserActivityService;
import de.tum.cit.aet.artemis.account.service.user.deletion.PermanentUserDeletionService;
import de.tum.cit.aet.artemis.account.service.user.deletion.UserDeletionMode;
import de.tum.cit.aet.artemis.account.service.user.deletion.UserDeletionPlanService;
import de.tum.cit.aet.artemis.admin.config.DataCleanupProperties;
import de.tum.cit.aet.artemis.admin.domain.CleanupJobExecution;
import de.tum.cit.aet.artemis.admin.domain.CleanupJobType;
import de.tum.cit.aet.artemis.admin.dto.CleanupServiceExecutionRecordDTO;
import de.tum.cit.aet.artemis.admin.dto.NonLatestNonRatedResultsCleanupCountDTO;
import de.tum.cit.aet.artemis.admin.dto.NonLatestRatedResultsCleanupCountDTO;
import de.tum.cit.aet.artemis.admin.dto.NotEnrolledUsersCleanupCountDTO;
import de.tum.cit.aet.artemis.admin.dto.OldCoursesCleanupCountDTO;
import de.tum.cit.aet.artemis.admin.dto.OldFeedbackCleanupCountDTO;
import de.tum.cit.aet.artemis.admin.dto.OrphanCleanupCountDTO;
import de.tum.cit.aet.artemis.admin.dto.PlagiarismCasesCleanupCountDTO;
import de.tum.cit.aet.artemis.admin.dto.PlagiarismComparisonCleanupCountDTO;
import de.tum.cit.aet.artemis.admin.dto.SubmissionVersionsCleanupCountDTO;
import de.tum.cit.aet.artemis.admin.repository.CleanupJobExecutionRepository;
import de.tum.cit.aet.artemis.assessment.repository.cleanup.FeedbackCleanupRepository;
import de.tum.cit.aet.artemis.assessment.repository.cleanup.FeedbackMessageCleanupRepository;
import de.tum.cit.aet.artemis.assessment.repository.cleanup.LongFeedbackTextCleanupRepository;
import de.tum.cit.aet.artemis.assessment.repository.cleanup.PlagiarismComparisonCleanupRepository;
import de.tum.cit.aet.artemis.assessment.repository.cleanup.RatingCleanupRepository;
import de.tum.cit.aet.artemis.assessment.repository.cleanup.ResultCleanupRepository;
import de.tum.cit.aet.artemis.assessment.repository.cleanup.ScaFeedbackCleanupRepository;
import de.tum.cit.aet.artemis.assessment.repository.cleanup.StudentScoreCleanupRepository;
import de.tum.cit.aet.artemis.assessment.repository.cleanup.SubmissionVersionCleanupRepository;
import de.tum.cit.aet.artemis.assessment.repository.cleanup.TeamScoreCleanupRepository;
import de.tum.cit.aet.artemis.assessment.repository.cleanup.TestCaseFeedbackCleanupRepository;
import de.tum.cit.aet.artemis.assessment.repository.cleanup.TextBlockCleanupRepository;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.course.service.CourseDataRetentionService;
import de.tum.cit.aet.artemis.notification.dto.MailRecipientDTO;
import de.tum.cit.aet.artemis.notification.service.notifications.MailSendingService;
import de.tum.cit.aet.artemis.plagiarism.api.PlagiarismCaseApi;

@Profile(PROFILE_CORE)
@Lazy
@Service
public class DataCleanupService {

    private static final Logger log = LoggerFactory.getLogger(DataCleanupService.class);

    private final CleanupJobExecutionRepository cleanupJobExecutionRepository;

    private final PlagiarismComparisonCleanupRepository plagiarismComparisonCleanupRepository;

    private final ResultCleanupRepository resultCleanupRepository;

    private final RatingCleanupRepository ratingCleanupRepository;

    private final FeedbackCleanupRepository feedbackCleanupRepository;

    private final TestCaseFeedbackCleanupRepository testCaseFeedbackCleanupRepository;

    private final ScaFeedbackCleanupRepository scaFeedbackCleanupRepository;

    private final FeedbackMessageCleanupRepository feedbackMessageCleanupRepository;

    /**
     * A message row is committed before the feedback rows that reference it (there is no surrounding transaction), so
     * only messages that have been unreferenced for longer than this are collected. The count preview uses the same
     * window, otherwise it would offer to delete rows the deletion then spares.
     */
    private static final int FEEDBACK_MESSAGE_GRACE_PERIOD_DAYS = 1;

    private final TextBlockCleanupRepository textBlockCleanupRepository;

    private final LongFeedbackTextCleanupRepository longFeedbackTextCleanupRepository;

    private final StudentScoreCleanupRepository studentScoreCleanupRepository;

    private final TeamScoreCleanupRepository teamScoreCleanupRepository;

    private final SubmissionVersionCleanupRepository submissionVersionCleanupRepository;

    private final DataCleanupProperties dataCleanupProperties;

    private final CourseDataRetentionService courseDataRetentionService;

    private final PermanentUserDeletionService permanentUserDeletionService;

    private final UserDeletionPlanService userDeletionPlanService;

    private final UserRepository userRepository;

    private final UserActivityService userActivityService;

    private final MailSendingService mailSendingService;

    private final Optional<PlagiarismCaseApi> plagiarismCaseApi;

    private static final String NOT_ENROLLED_DELETION_WARNING_EMAIL_TEMPLATE = "mail/notEnrolledUserDeletionWarningEmail";

    private static final String NOT_ENROLLED_DELETION_WARNING_SUBJECT_KEY = "email.notEnrolledUserDeletionWarning.title";

    // A date safely before any real course start date, used to convert the existing "course date range" feedback cleanup
    // queries (which filter startDate > deleteFrom AND endDate < deleteTo) into an age-only ("ended before X") cleanup.
    private static final ZonedDateTime FAR_PAST = ZonedDateTime.of(1900, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);

    public DataCleanupService(CleanupJobExecutionRepository cleanupJobExecutionRepository, PlagiarismComparisonCleanupRepository plagiarismComparisonCleanupRepository,
            ResultCleanupRepository resultCleanupRepository, RatingCleanupRepository ratingCleanupRepository, FeedbackCleanupRepository feedbackCleanupRepository,
            TextBlockCleanupRepository textBlockCleanupRepository, LongFeedbackTextCleanupRepository longFeedbackTextCleanupRepository,
            StudentScoreCleanupRepository studentScoreCleanupRepository, TeamScoreCleanupRepository teamScoreCleanupRepository,
            SubmissionVersionCleanupRepository submissionVersionCleanupRepository, DataCleanupProperties dataCleanupProperties,
            CourseDataRetentionService courseDataRetentionService, PermanentUserDeletionService permanentUserDeletionService, UserDeletionPlanService userDeletionPlanService,
            UserRepository userRepository, MailSendingService mailSendingService, Optional<PlagiarismCaseApi> plagiarismCaseApi, UserActivityService userActivityService,
            TestCaseFeedbackCleanupRepository testCaseFeedbackCleanupRepository, ScaFeedbackCleanupRepository scaFeedbackCleanupRepository,
            FeedbackMessageCleanupRepository feedbackMessageCleanupRepository) {
        this.userActivityService = userActivityService;
        this.resultCleanupRepository = resultCleanupRepository;
        this.ratingCleanupRepository = ratingCleanupRepository;
        this.feedbackCleanupRepository = feedbackCleanupRepository;
        this.textBlockCleanupRepository = textBlockCleanupRepository;
        this.longFeedbackTextCleanupRepository = longFeedbackTextCleanupRepository;
        this.studentScoreCleanupRepository = studentScoreCleanupRepository;
        this.teamScoreCleanupRepository = teamScoreCleanupRepository;
        this.cleanupJobExecutionRepository = cleanupJobExecutionRepository;
        this.plagiarismComparisonCleanupRepository = plagiarismComparisonCleanupRepository;
        this.submissionVersionCleanupRepository = submissionVersionCleanupRepository;
        this.dataCleanupProperties = dataCleanupProperties;
        this.courseDataRetentionService = courseDataRetentionService;
        this.permanentUserDeletionService = permanentUserDeletionService;
        this.userDeletionPlanService = userDeletionPlanService;
        this.userRepository = userRepository;
        this.mailSendingService = mailSendingService;
        this.plagiarismCaseApi = plagiarismCaseApi;
        this.testCaseFeedbackCleanupRepository = testCaseFeedbackCleanupRepository;
        this.scaFeedbackCleanupRepository = scaFeedbackCleanupRepository;
        this.feedbackMessageCleanupRepository = feedbackMessageCleanupRepository;
    }

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

        int deletedTestCaseFeedbackForOrphanResults = testCaseFeedbackCleanupRepository.deleteTestCaseFeedbackForOrphanResults();
        log.info("Deleted {} test case feedback entries for orphan results", deletedTestCaseFeedbackForOrphanResults);

        int deletedScaFeedbackForOrphanResults = scaFeedbackCleanupRepository.deleteScaFeedbackForOrphanResults();
        log.info("Deleted {} SCA feedback entries for orphan results", deletedScaFeedbackForOrphanResults);

        // the one-day grace period protects messages created by an in-flight build-result transaction
        // whose referencing feedback rows are not committed yet (the columns carry no foreign key)
        int deletedUnreferencedFeedbackMessages = feedbackMessageCleanupRepository
                .deleteUnreferencedFeedbackMessages(ZonedDateTime.now().minusDays(FEEDBACK_MESSAGE_GRACE_PERIOD_DAYS));
        log.info("Deleted {} unreferenced feedback messages", deletedUnreferencedFeedbackMessages);

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

        int deletedTestCaseFeedback = testCaseFeedbackCleanupRepository.deleteOldNonRatedTestCaseFeedbackWhereCourseDateBetween(deleteFrom, deleteTo);
        log.info("Deleted {} test case feedback entries for non-rated results between {} and {}", deletedTestCaseFeedback, deleteFrom, deleteTo);

        int deletedScaFeedback = scaFeedbackCleanupRepository.deleteOldNonRatedScaFeedbackWhereCourseDateBetween(deleteFrom, deleteTo);
        log.info("Deleted {} SCA feedback entries for non-rated results between {} and {}", deletedScaFeedback, deleteFrom, deleteTo);

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

        int deletedTestCaseFeedback = testCaseFeedbackCleanupRepository.deleteOldTestCaseFeedbackThatAreNotLatestRatedResultsWhereCourseDateBetween(deleteFrom, deleteTo);
        log.info("Deleted {} test case feedback entries for rated results between {} and {}", deletedTestCaseFeedback, deleteFrom, deleteTo);

        int deletedScaFeedback = scaFeedbackCleanupRepository.deleteOldScaFeedbackThatAreNotLatestRatedResultsWhereCourseDateBetween(deleteFrom, deleteTo);
        log.info("Deleted {} SCA feedback entries for rated results between {} and {}", deletedScaFeedback, deleteFrom, deleteTo);

        return CleanupServiceExecutionRecordDTO.of(createCleanupJobExecution(CleanupJobType.RATED_RESULTS, deleteFrom, deleteTo));
    }

    public CleanupServiceExecutionRecordDTO deleteSubmissionVersions(ZonedDateTime deleteFrom, ZonedDateTime deleteTo) {
        int deletedSubmissionVersions = submissionVersionCleanupRepository.deleteSubmissionVersionsByCreatedDateRange(deleteFrom.toInstant(), deleteTo.toInstant());
        log.info("Deleted {} submission versions entries between {} and {}", deletedSubmissionVersions, deleteFrom, deleteTo);

        return CleanupServiceExecutionRecordDTO.of(createCleanupJobExecution(CleanupJobType.SUBMISSION_VERSIONS, deleteFrom, deleteTo));
    }

    /**
     * Counts orphaned entities that are no longer associated with valid results or participations.
     * This includes feedback, text blocks, and scores that reference null results, participations, or submissions.
     *
     * @return an {@link OrphanCleanupCountDTO} representing the counts of orphaned entities that would be deleted
     */
    public OrphanCleanupCountDTO countOrphans() {
        int orphanFeedbackCount = feedbackCleanupRepository.countOrphanFeedback();
        int orphanLongFeedbackTextCount = longFeedbackTextCleanupRepository.countLongFeedbackTextForOrphanedFeedback();
        int orphanTextBlockCount = textBlockCleanupRepository.countTextBlockForEmptyFeedback();
        int orphanStudentScoreCount = studentScoreCleanupRepository.countOrphanStudentScore();
        int orphanTeamScoreCount = teamScoreCleanupRepository.countOrphanTeamScore();
        int orphanLongFeedbackTextForOrphanResultsCount = longFeedbackTextCleanupRepository.countLongFeedbackTextForOrphanResult();
        int orphanTextBlockForOrphanResultsCount = textBlockCleanupRepository.countTextBlockForOrphanResults();
        int orphanFeedbackForOrphanResultsCount = feedbackCleanupRepository.countFeedbackForOrphanResults()
                + testCaseFeedbackCleanupRepository.countTestCaseFeedbackForOrphanResults() + scaFeedbackCleanupRepository.countScaFeedbackForOrphanResults();
        int orphanRatingCount = ratingCleanupRepository.countOrphanRating();
        int orphanResultsWithoutParticipationCount = resultCleanupRepository.countResultWithoutParticipationAndSubmission();
        int orphanFeedbackMessageCount = feedbackMessageCleanupRepository.countUnreferencedFeedbackMessages(ZonedDateTime.now().minusDays(FEEDBACK_MESSAGE_GRACE_PERIOD_DAYS));

        return new OrphanCleanupCountDTO(orphanFeedbackCount, orphanLongFeedbackTextCount, orphanTextBlockCount, orphanStudentScoreCount, orphanTeamScoreCount,
                orphanFeedbackForOrphanResultsCount, orphanLongFeedbackTextForOrphanResultsCount, orphanTextBlockForOrphanResultsCount, orphanRatingCount,
                orphanResultsWithoutParticipationCount, orphanFeedbackMessageCount);
    }

    /**
     * Counts plagiarism comparisons with a status of "None" that belong to courses within the specified date range.
     * It retrieves the IDs of the plagiarism comparisons matching the criteria and counts the related data,
     * including plagiarism elements, submissions, and matches.
     *
     * @param deleteFrom the start date for selecting plagiarism comparisons
     * @param deleteTo   the end date for selecting plagiarism comparisons
     * @return a {@link PlagiarismComparisonCleanupCountDTO} representing the counts of entities related to plagiarism comparisons
     */
    public PlagiarismComparisonCleanupCountDTO countPlagiarismComparisons(ZonedDateTime deleteFrom, ZonedDateTime deleteTo) {
        var pcIds = plagiarismComparisonCleanupRepository.findPlagiarismComparisonIdWithStatusNoneThatBelongToCourseWithDates(deleteFrom, deleteTo);
        int plagiarismComparisonCount = pcIds.size();
        int plagiarismElementsCount = plagiarismComparisonCleanupRepository.countPlagiarismSubmissionElementsByComparisonIdsIn(pcIds);
        int plagiarismSubmissionsCount = plagiarismComparisonCleanupRepository.countPlagiarismSubmissionsByComparisonIdsIn(pcIds);
        int plagiarismMatchesCount = plagiarismComparisonCleanupRepository.countPlagiarismComparisonMatchesByComparisonIdsIn(pcIds);

        return new PlagiarismComparisonCleanupCountDTO(plagiarismComparisonCount, plagiarismElementsCount, plagiarismSubmissionsCount, plagiarismMatchesCount);
    }

    /**
     * Counts non-rated results that are not the latest non-rated result for each participation, within the specified date range.
     * This includes associated long feedback texts, text blocks, and feedback items that would be affected.
     *
     * @param deleteFrom The start of the date range for counting non-rated results.
     * @param deleteTo   The end of the date range for counting non-rated results.
     * @return a {@link NonLatestNonRatedResultsCleanupCountDTO} representing the counts of entities related to non-latest non-rated results
     */
    public NonLatestNonRatedResultsCleanupCountDTO countNonLatestNonRatedResults(ZonedDateTime deleteFrom, ZonedDateTime deleteTo) {
        int longFeedbackTextCount = longFeedbackTextCleanupRepository.countLongFeedbackTextForNonRatedResultsWhereCourseDateBetween(deleteFrom, deleteTo);
        int textBlockCount = textBlockCleanupRepository.countTextBlockForNonRatedResultsWhereCourseDateBetween(deleteFrom, deleteTo);
        int feedbackCount = feedbackCleanupRepository.countOldNonRatedFeedbackWhereCourseDateBetween(deleteFrom, deleteTo)
                + testCaseFeedbackCleanupRepository.countOldNonRatedTestCaseFeedbackWhereCourseDateBetween(deleteFrom, deleteTo)
                + scaFeedbackCleanupRepository.countOldNonRatedScaFeedbackWhereCourseDateBetween(deleteFrom, deleteTo);

        return new NonLatestNonRatedResultsCleanupCountDTO(longFeedbackTextCount, textBlockCount, feedbackCount);
    }

    /**
     * Counts rated results that are not the latest rated result for each participation, for courses conducted within the specified date range.
     * This includes associated long feedback texts, text blocks, and feedback items that would be affected.
     *
     * @param deleteFrom The start of the date range for counting rated results.
     * @param deleteTo   The end of the date range for counting rated results.
     * @return a {@link NonLatestRatedResultsCleanupCountDTO} representing the counts of entities related to non-latest rated results
     */
    public NonLatestRatedResultsCleanupCountDTO countNonLatestRatedResults(ZonedDateTime deleteFrom, ZonedDateTime deleteTo) {
        int longFeedbackTextCount = longFeedbackTextCleanupRepository.countLongFeedbackTextForRatedResultsWhereCourseDateBetween(deleteFrom, deleteTo);
        int textBlockCount = textBlockCleanupRepository.countTextBlockForRatedResultsWhereCourseDateBetween(deleteFrom, deleteTo);
        int feedbackCount = feedbackCleanupRepository.countOldFeedbackThatAreNotLatestRatedResultsWhereCourseDateBetween(deleteFrom, deleteTo)
                + testCaseFeedbackCleanupRepository.countOldTestCaseFeedbackThatAreNotLatestRatedResultsWhereCourseDateBetween(deleteFrom, deleteTo)
                + scaFeedbackCleanupRepository.countOldScaFeedbackThatAreNotLatestRatedResultsWhereCourseDateBetween(deleteFrom, deleteTo);

        return new NonLatestRatedResultsCleanupCountDTO(longFeedbackTextCount, textBlockCount, feedbackCount);
    }

    public SubmissionVersionsCleanupCountDTO countSubmissionVersions(ZonedDateTime deleteFrom, ZonedDateTime deleteTo) {
        int submissionVersionsCount = this.submissionVersionCleanupRepository.countSubmissionVersionsByCreatedDateRange(deleteFrom.toInstant(), deleteTo.toInstant());
        return new SubmissionVersionsCleanupCountDTO(submissionVersionsCount);
    }

    /**
     * Phase 1 of the old-course cleanup: archives every old course due for a student-data reset and warns its instructors
     * (see {@link CourseDataRetentionService#warnAndArchiveDueCourses()}).
     *
     * @return a {@link CleanupServiceExecutionRecordDTO} representing the execution record of the cleanup job
     */
    public CleanupServiceExecutionRecordDTO warnOldCoursesReset() {
        int warned = courseDataRetentionService.warnAndArchiveDueCourses();
        log.info("Warned instructors of {} old course(s) about the upcoming student-data reset", warned);
        return CleanupServiceExecutionRecordDTO.of(createCleanupJobExecution(CleanupJobType.OLD_COURSES_RESET_WARNING, null, null));
    }

    /**
     * Counts the old courses that are due for a student-data reset warning (retention elapsed, not yet archived).
     *
     * @return an {@link OldCoursesCleanupCountDTO} with the affected course count and titles
     */
    public OldCoursesCleanupCountDTO countOldCoursesResetWarning() {
        return toOldCoursesCleanupCountDTO(courseDataRetentionService.findCoursesDueForWarning());
    }

    /**
     * Phase 2 of the old-course cleanup: resets the student data of every old course past the grace period, keeping the
     * course material intact (see {@link CourseDataRetentionService#resetDueCourses()}).
     *
     * @return a {@link CleanupServiceExecutionRecordDTO} representing the execution record of the cleanup job
     */
    public CleanupServiceExecutionRecordDTO resetOldCourses() {
        int reset = courseDataRetentionService.resetDueCourses();
        log.info("Reset the student data of {} old course(s)", reset);
        return CleanupServiceExecutionRecordDTO.of(createCleanupJobExecution(CleanupJobType.OLD_COURSES_RESET, null, null));
    }

    /**
     * Counts the old courses that are due for a student-data reset (retention plus grace elapsed, already archived).
     *
     * @return an {@link OldCoursesCleanupCountDTO} with the affected course count and titles
     */
    public OldCoursesCleanupCountDTO countOldCoursesReset() {
        return toOldCoursesCleanupCountDTO(courseDataRetentionService.findCoursesDueForReset());
    }

    /**
     * Deletes the feedback (feedback entries, long feedback texts, text blocks) of non-latest results for all courses that
     * ended before the configured cutoff. Only the feedback of non-latest results is removed; the results themselves and
     * the feedback of the latest rated and latest non-rated result per participation are kept. Runs both the rated and
     * non-rated variants of the existing course-date-range cleanup with an open (far-past) lower bound.
     *
     * @return a {@link CleanupServiceExecutionRecordDTO} representing the execution record of the cleanup job
     */
    public CleanupServiceExecutionRecordDTO deleteFeedbackOfNonLatestResultsOfOldCourses() {
        ZonedDateTime cutoff = ZonedDateTime.now().minusWeeks(dataCleanupProperties.oldFeedbackCutoffWeeks());

        // Rated: delete referencing rows (long feedback texts, text blocks) before the feedback they belong to.
        int ratedLongFeedbackTexts = longFeedbackTextCleanupRepository.deleteLongFeedbackTextForRatedResultsWhereCourseDateBetween(FAR_PAST, cutoff);
        int ratedTextBlocks = textBlockCleanupRepository.deleteTextBlockForRatedResultsWhereCourseDateBetween(FAR_PAST, cutoff);
        int ratedFeedback = feedbackCleanupRepository.deleteOldFeedbackThatAreNotLatestRatedResultsWhereCourseDateBetween(FAR_PAST, cutoff)
                + testCaseFeedbackCleanupRepository.deleteOldTestCaseFeedbackThatAreNotLatestRatedResultsWhereCourseDateBetween(FAR_PAST, cutoff)
                + scaFeedbackCleanupRepository.deleteOldScaFeedbackThatAreNotLatestRatedResultsWhereCourseDateBetween(FAR_PAST, cutoff);

        // Non-rated
        int nonRatedLongFeedbackTexts = longFeedbackTextCleanupRepository.deleteLongFeedbackTextForNonRatedResultsWhereCourseDateBetween(FAR_PAST, cutoff);
        int nonRatedTextBlocks = textBlockCleanupRepository.deleteTextBlockForNonRatedResultsWhereCourseDateBetween(FAR_PAST, cutoff);
        int nonRatedFeedback = feedbackCleanupRepository.deleteOldNonRatedFeedbackWhereCourseDateBetween(FAR_PAST, cutoff)
                + testCaseFeedbackCleanupRepository.deleteOldNonRatedTestCaseFeedbackWhereCourseDateBetween(FAR_PAST, cutoff)
                + scaFeedbackCleanupRepository.deleteOldNonRatedScaFeedbackWhereCourseDateBetween(FAR_PAST, cutoff);

        log.info("Deleted feedback of non-latest results of old courses (ended before {}): {} long feedback texts, {} text blocks, {} feedback entries", cutoff,
                ratedLongFeedbackTexts + nonRatedLongFeedbackTexts, ratedTextBlocks + nonRatedTextBlocks, ratedFeedback + nonRatedFeedback);
        return CleanupServiceExecutionRecordDTO.of(createCleanupJobExecution(CleanupJobType.FEEDBACK, null, null));
    }

    /**
     * Counts the feedback (feedback entries, long feedback texts, text blocks) of non-latest results that would be deleted
     * for courses that ended before the configured cutoff.
     *
     * @return an {@link OldFeedbackCleanupCountDTO} with the combined rated and non-rated counts
     */
    public OldFeedbackCleanupCountDTO countFeedbackOfNonLatestResultsOfOldCourses() {
        ZonedDateTime cutoff = ZonedDateTime.now().minusWeeks(dataCleanupProperties.oldFeedbackCutoffWeeks());
        int longFeedbackText = longFeedbackTextCleanupRepository.countLongFeedbackTextForRatedResultsWhereCourseDateBetween(FAR_PAST, cutoff)
                + longFeedbackTextCleanupRepository.countLongFeedbackTextForNonRatedResultsWhereCourseDateBetween(FAR_PAST, cutoff);
        int textBlock = textBlockCleanupRepository.countTextBlockForRatedResultsWhereCourseDateBetween(FAR_PAST, cutoff)
                + textBlockCleanupRepository.countTextBlockForNonRatedResultsWhereCourseDateBetween(FAR_PAST, cutoff);
        int feedback = feedbackCleanupRepository.countOldFeedbackThatAreNotLatestRatedResultsWhereCourseDateBetween(FAR_PAST, cutoff)
                + feedbackCleanupRepository.countOldNonRatedFeedbackWhereCourseDateBetween(FAR_PAST, cutoff)
                + testCaseFeedbackCleanupRepository.countOldTestCaseFeedbackThatAreNotLatestRatedResultsWhereCourseDateBetween(FAR_PAST, cutoff)
                + testCaseFeedbackCleanupRepository.countOldNonRatedTestCaseFeedbackWhereCourseDateBetween(FAR_PAST, cutoff)
                + scaFeedbackCleanupRepository.countOldScaFeedbackThatAreNotLatestRatedResultsWhereCourseDateBetween(FAR_PAST, cutoff)
                + scaFeedbackCleanupRepository.countOldNonRatedScaFeedbackWhereCourseDateBetween(FAR_PAST, cutoff);
        return new OldFeedbackCleanupCountDTO(longFeedbackText, textBlock, feedback);
    }

    /**
     * Deletes the submission versions (editor keystroke history) of all courses that ended before the configured cutoff.
     *
     * @return a {@link CleanupServiceExecutionRecordDTO} representing the execution record of the cleanup job
     */
    public CleanupServiceExecutionRecordDTO deleteOldCourseSubmissionVersions() {
        ZonedDateTime cutoff = ZonedDateTime.now().minusWeeks(dataCleanupProperties.oldSubmissionVersionsCutoffWeeks());
        int deleted = submissionVersionCleanupRepository.deleteSubmissionVersionsWhereCourseEndDateBefore(cutoff);
        log.info("Deleted {} submission versions of courses that ended before {}", deleted, cutoff);
        return CleanupServiceExecutionRecordDTO.of(createCleanupJobExecution(CleanupJobType.OLD_COURSE_SUBMISSION_VERSIONS, null, null));
    }

    /**
     * Counts the submission versions of courses that ended before the configured cutoff and would be deleted.
     *
     * @return a {@link SubmissionVersionsCleanupCountDTO} with the affected count
     */
    public SubmissionVersionsCleanupCountDTO countOldCourseSubmissionVersions() {
        ZonedDateTime cutoff = ZonedDateTime.now().minusWeeks(dataCleanupProperties.oldSubmissionVersionsCutoffWeeks());
        return new SubmissionVersionsCleanupCountDTO(submissionVersionCleanupRepository.countSubmissionVersionsWhereCourseEndDateBefore(cutoff));
    }

    /**
     * Phase 1 of the not-enrolled-user cleanup: emails every not-enrolled, inactive user a warning that their account
     * will be deleted after the grace period, and stamps the warning date. The account is only advanced to "warned" if a
     * warning was actually sent (mail configured, user activated with an email), so an account is never scheduled for
     * deletion without prior notice. The Iris bot and administrators are excluded.
     *
     * @return a {@link CleanupServiceExecutionRecordDTO} representing the execution record of the cleanup job
     */
    public CleanupServiceExecutionRecordDTO warnNotEnrolledUsers() {
        int warned = 0;
        if (!mailSendingService.isMailConfigured()) {
            log.warn("Mail is not configured; skipping the not-enrolled-user deletion warning so that no account is scheduled for deletion without prior notice");
        }
        else {
            var inactiveBefore = ZonedDateTime.now().minusMonths(dataCleanupProperties.notEnrolledUsersInactivityMonths()).toInstant();
            List<User> usersToWarn = userRepository.findNotEnrolledUsersToWarn(inactiveBefore).stream().filter(user -> !user.isBot()).toList();
            Map<String, Object> contextVariables = Map.of("gracePeriodDays", dataCleanupProperties.notEnrolledUsersWarningGracePeriodDays());
            // Counted once for the whole batch rather than per user: createImpact walks every reference policy, so the
            // per-user call repeated that count for every candidate.
            List<User> warnableUsers = usersToWarn.stream().filter(user -> user.getActivated() && user.getEmail() != null).toList();
            Set<Long> eligibleUserIds = userDeletionPlanService.createBulkImpact(warnableUsers, UserDeletionMode.AUTOMATIC).users().stream()
                    .filter(UserDeletionImpactDTO::automaticEligible).map(UserDeletionImpactDTO::userId).collect(Collectors.toSet());
            for (User user : usersToWarn) {
                if (!user.getActivated() || user.getEmail() == null) {
                    continue; // cannot warn this user, so do not schedule their account for deletion
                }
                if (!eligibleUserIds.contains(user.getId())) {
                    continue;
                }
                try {
                    // Send synchronously and only stamp the warning once it was actually delivered. Otherwise an SMTP
                    // outage would stamp every user as "warned" (async enqueue never reports the later delivery failure)
                    // and phase 2 would permanently delete accounts that never received any notice.
                    boolean sent = mailSendingService.buildAndSendSyncReporting(MailRecipientDTO.from(user), NOT_ENROLLED_DELETION_WARNING_SUBJECT_KEY, List.of(),
                            NOT_ENROLLED_DELETION_WARNING_EMAIL_TEMPLATE, contextVariables);
                    if (sent) {
                        userActivityService.recordDeletionWarning(user.getLogin(), ZonedDateTime.now().toInstant());
                        warned++;
                    }
                }
                catch (Exception e) {
                    log.error("Failed to warn not-enrolled user {} about the upcoming account deletion", user.getLogin(), e);
                }
            }
        }
        log.info("Warned {} not-enrolled, inactive user(s) about an upcoming account deletion", warned);
        return CleanupServiceExecutionRecordDTO.of(createCleanupJobExecution(CleanupJobType.NOT_ENROLLED_USERS_WARNING, null, null));
    }

    /**
     * Counts the not-enrolled, inactive users who have not yet been warned and would be warned by phase 1.
     *
     * @return a {@link NotEnrolledUsersCleanupCountDTO} with the affected user count
     */
    public NotEnrolledUsersCleanupCountDTO countNotEnrolledUsersWarning() {
        var inactiveBefore = ZonedDateTime.now().minusMonths(dataCleanupProperties.notEnrolledUsersInactivityMonths()).toInstant();
        // Mirror the exact filter of warnNotEnrolledUsers (not a bot, activated, has an email); otherwise the preview
        // would keep counting accounts that can never be warned (nor deleted), so the count would never drain to zero.
        List<User> candidates = userRepository.findNotEnrolledUsersToWarn(inactiveBefore).stream().filter(user -> !user.isBot() && user.getActivated() && user.getEmail() != null)
                .toList();
        return countDeletionEligibility(candidates);
    }

    /**
     * Phase 2 of the not-enrolled-user cleanup: physically deletes every eligible user who was warned, whose grace
     * period has elapsed, who is still enrolled in no course, and who has not logged in since the warning. Users who
     * "came back" (re-enrolled or logged in after the warning) first have their warning cleared and are spared. The Iris
     * bot is never deleted; admins and super-admins are already excluded by the repository query.
     *
     * @return a {@link CleanupServiceExecutionRecordDTO} representing the execution record of the cleanup job
     */
    public CleanupServiceExecutionRecordDTO deleteNotEnrolledUsers() {
        userActivityService.clearDeletionWarningForReturnedUsers();
        var warnedBefore = ZonedDateTime.now().minusDays(dataCleanupProperties.notEnrolledUsersWarningGracePeriodDays()).toInstant();
        List<String> logins = notEnrolledUserLoginsToDelete();
        int deleted = 0;
        int blocked = 0;
        for (String login : logins) {
            try {
                Optional<User> user = userRepository.findOneByLogin(login);
                if (user.isEmpty()) {
                    continue;
                }
                var result = permanentUserDeletionService.deleteAutomatically(user.get().getId(), warnedBefore);
                if (result.status() == UserDeletionResultStatus.DELETED) {
                    deleted++;
                }
                else {
                    blocked++;
                }
            }
            catch (Exception e) {
                log.error("Failed to permanently delete one not-enrolled user", e);
            }
        }

        int legacyPurged = 0;
        for (Long legacyUserId : userDeletionPlanService.findLegacyDeletedUserIds()) {
            try {
                if (permanentUserDeletionService.deleteLegacyTombstone(legacyUserId).status() == UserDeletionResultStatus.DELETED) {
                    legacyPurged++;
                }
            }
            catch (Exception e) {
                log.error("Failed to purge legacy user tombstone {}", legacyUserId, e);
            }
        }
        log.info("Permanently deleted {} warned user(s), blocked {} user(s), and purged {} legacy tombstone(s)", deleted, blocked, legacyPurged);
        return CleanupServiceExecutionRecordDTO.of(createCleanupJobExecution(CleanupJobType.NOT_ENROLLED_USERS, null, null));
    }

    /**
     * Counts the warned, past-grace, still-inactive users who would be permanently deleted or blocked by remaining references in phase 2.
     *
     * @return a {@link NotEnrolledUsersCleanupCountDTO} with the affected user count
     */
    public NotEnrolledUsersCleanupCountDTO countNotEnrolledUsers() {
        List<User> candidates = notEnrolledUserLoginsToDelete().stream().map(userRepository::findOneByLogin).flatMap(Optional::stream).toList();
        return countDeletionEligibility(candidates);
    }

    /**
     * Resolves the logins of warned users whose grace period has elapsed and who are still not-enrolled and inactive
     * (no login since the warning). The query excludes administrators, super-administrators, and the Iris bot.
     *
     * @return the logins to evaluate for permanent deletion
     */
    private List<String> notEnrolledUserLoginsToDelete() {
        var warnedBefore = ZonedDateTime.now().minusDays(dataCleanupProperties.notEnrolledUsersWarningGracePeriodDays()).toInstant();
        return userRepository.findNotEnrolledUserLoginsToDelete(warnedBefore);
    }

    private NotEnrolledUsersCleanupCountDTO countDeletionEligibility(List<User> candidates) {
        int eligible = (int) userDeletionPlanService.createBulkImpact(candidates, UserDeletionMode.AUTOMATIC).users().stream().filter(UserDeletionImpactDTO::automaticEligible)
                .count();
        int blocked = candidates.size() - eligible;
        return new NotEnrolledUsersCleanupCountDTO(eligible, blocked);
    }

    /**
     * Deletes all plagiarism cases (with their notification posts and answer posts) of courses that ended before the
     * grade-relevant retention cutoff. Plagiarism cases are grade-relevant records and therefore share the same 5-year
     * (configurable) retention period as other exam/grade data. If the plagiarism module is disabled, nothing is deleted
     * and the execution is still recorded so the operation appears consistent in the admin UI.
     *
     * @return a {@link CleanupServiceExecutionRecordDTO} representing the execution record of the cleanup job
     */
    public CleanupServiceExecutionRecordDTO deletePlagiarismCasesOfOldCourses() {
        ZonedDateTime cutoff = ZonedDateTime.now().minusYears(dataCleanupProperties.gradeRelevantRetentionYears());
        int deleted = plagiarismCaseApi.map(api -> api.deletePlagiarismCasesOfCoursesEndedBefore(cutoff)).orElse(0);
        log.info("Deleted {} plagiarism case(s) of courses that ended before {}", deleted, cutoff);
        return CleanupServiceExecutionRecordDTO.of(createCleanupJobExecution(CleanupJobType.PLAGIARISM_CASES, null, null));
    }

    /**
     * Counts the plagiarism cases of courses that ended before the grade-relevant retention cutoff and would be deleted.
     *
     * @return a {@link PlagiarismCasesCleanupCountDTO} with the affected plagiarism-case count
     */
    public PlagiarismCasesCleanupCountDTO countPlagiarismCasesOfOldCourses() {
        ZonedDateTime cutoff = ZonedDateTime.now().minusYears(dataCleanupProperties.gradeRelevantRetentionYears());
        int count = plagiarismCaseApi.map(api -> api.countPlagiarismCasesOfCoursesEndedBefore(cutoff)).orElse(0);
        return new PlagiarismCasesCleanupCountDTO(count);
    }

    private OldCoursesCleanupCountDTO toOldCoursesCleanupCountDTO(List<Course> courses) {
        return new OldCoursesCleanupCountDTO(courses.size());
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
