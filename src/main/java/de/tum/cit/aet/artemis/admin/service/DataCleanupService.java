package de.tum.cit.aet.artemis.admin.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.account.service.user.UserService;
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
import de.tum.cit.aet.artemis.admin.dto.PlagiarismComparisonCleanupCountDTO;
import de.tum.cit.aet.artemis.admin.dto.SubmissionVersionsCleanupCountDTO;
import de.tum.cit.aet.artemis.admin.repository.CleanupJobExecutionRepository;
import de.tum.cit.aet.artemis.assessment.repository.cleanup.FeedbackCleanupRepository;
import de.tum.cit.aet.artemis.assessment.repository.cleanup.LongFeedbackTextCleanupRepository;
import de.tum.cit.aet.artemis.assessment.repository.cleanup.PlagiarismComparisonCleanupRepository;
import de.tum.cit.aet.artemis.assessment.repository.cleanup.RatingCleanupRepository;
import de.tum.cit.aet.artemis.assessment.repository.cleanup.ResultCleanupRepository;
import de.tum.cit.aet.artemis.assessment.repository.cleanup.StudentScoreCleanupRepository;
import de.tum.cit.aet.artemis.assessment.repository.cleanup.SubmissionVersionCleanupRepository;
import de.tum.cit.aet.artemis.assessment.repository.cleanup.TeamScoreCleanupRepository;
import de.tum.cit.aet.artemis.assessment.repository.cleanup.TextBlockCleanupRepository;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.course.service.CourseDataRetentionService;

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

    private final TextBlockCleanupRepository textBlockCleanupRepository;

    private final LongFeedbackTextCleanupRepository longFeedbackTextCleanupRepository;

    private final StudentScoreCleanupRepository studentScoreCleanupRepository;

    private final TeamScoreCleanupRepository teamScoreCleanupRepository;

    private final SubmissionVersionCleanupRepository submissionVersionCleanupRepository;

    private final DataCleanupProperties dataCleanupProperties;

    private final CourseDataRetentionService courseDataRetentionService;

    private final UserService userService;

    private final UserRepository userRepository;

    // A date safely before any real course start date, used to convert the existing "course date range" feedback cleanup
    // queries (which filter startDate > deleteFrom AND endDate < deleteTo) into an age-only ("ended before X") cleanup.
    private static final ZonedDateTime FAR_PAST = ZonedDateTime.of(1900, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);

    public DataCleanupService(CleanupJobExecutionRepository cleanupJobExecutionRepository, PlagiarismComparisonCleanupRepository plagiarismComparisonCleanupRepository,
            ResultCleanupRepository resultCleanupRepository, RatingCleanupRepository ratingCleanupRepository, FeedbackCleanupRepository feedbackCleanupRepository,
            TextBlockCleanupRepository textBlockCleanupRepository, LongFeedbackTextCleanupRepository longFeedbackTextCleanupRepository,
            StudentScoreCleanupRepository studentScoreCleanupRepository, TeamScoreCleanupRepository teamScoreCleanupRepository,
            SubmissionVersionCleanupRepository submissionVersionCleanupRepository, DataCleanupProperties dataCleanupProperties,
            CourseDataRetentionService courseDataRetentionService, UserService userService, UserRepository userRepository) {
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
        this.userService = userService;
        this.userRepository = userRepository;
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
        int orphanFeedbackForOrphanResultsCount = feedbackCleanupRepository.countFeedbackForOrphanResults();
        int orphanRatingCount = ratingCleanupRepository.countOrphanRating();
        int orphanResultsWithoutParticipationCount = resultCleanupRepository.countResultWithoutParticipationAndSubmission();

        return new OrphanCleanupCountDTO(orphanFeedbackCount, orphanLongFeedbackTextCount, orphanTextBlockCount, orphanStudentScoreCount, orphanTeamScoreCount,
                orphanFeedbackForOrphanResultsCount, orphanLongFeedbackTextForOrphanResultsCount, orphanTextBlockForOrphanResultsCount, orphanRatingCount,
                orphanResultsWithoutParticipationCount);
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
        int feedbackCount = feedbackCleanupRepository.countOldNonRatedFeedbackWhereCourseDateBetween(deleteFrom, deleteTo);

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
        int feedbackCount = feedbackCleanupRepository.countOldFeedbackThatAreNotLatestRatedResultsWhereCourseDateBetween(deleteFrom, deleteTo);

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
        int ratedFeedback = feedbackCleanupRepository.deleteOldFeedbackThatAreNotLatestRatedResultsWhereCourseDateBetween(FAR_PAST, cutoff);

        // Non-rated
        int nonRatedLongFeedbackTexts = longFeedbackTextCleanupRepository.deleteLongFeedbackTextForNonRatedResultsWhereCourseDateBetween(FAR_PAST, cutoff);
        int nonRatedTextBlocks = textBlockCleanupRepository.deleteTextBlockForNonRatedResultsWhereCourseDateBetween(FAR_PAST, cutoff);
        int nonRatedFeedback = feedbackCleanupRepository.deleteOldNonRatedFeedbackWhereCourseDateBetween(FAR_PAST, cutoff);

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
                + feedbackCleanupRepository.countOldNonRatedFeedbackWhereCourseDateBetween(FAR_PAST, cutoff);
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
     * Soft-deletes (and anonymizes) all users who are enrolled in no course and have been inactive beyond the configured
     * guard period. The Iris bot is never deleted; admins and super-admins are already excluded by the repository query.
     *
     * @return a {@link CleanupServiceExecutionRecordDTO} representing the execution record of the cleanup job
     */
    public CleanupServiceExecutionRecordDTO deleteNotEnrolledUsers() {
        List<String> logins = notEnrolledUserLogins();
        log.info("Soft-deleting {} not-enrolled, inactive user(s)", logins.size());
        logins.forEach(login -> {
            try {
                userService.softDeleteUser(login);
            }
            catch (Exception e) {
                log.error("Failed to soft-delete not-enrolled user {}", login, e);
            }
        });
        return CleanupServiceExecutionRecordDTO.of(createCleanupJobExecution(CleanupJobType.NOT_ENROLLED_USERS, null, null));
    }

    /**
     * Counts the users who are enrolled in no course and inactive beyond the guard period and would be soft-deleted.
     *
     * @return a {@link NotEnrolledUsersCleanupCountDTO} with the affected user count
     */
    public NotEnrolledUsersCleanupCountDTO countNotEnrolledUsers() {
        return new NotEnrolledUsersCleanupCountDTO(notEnrolledUserLogins().size());
    }

    /**
     * Resolves the logins of users enrolled in no course and inactive beyond the configured guard period.
     * <p>
     * Inactivity is measured by the user's last login (falling back to the creation date for accounts that never logged
     * in), a real activity signal rather than {@code lastModifiedDate} (which is bumped by any write to the user row,
     * e.g. group synchronization). The operation is admin-only and has a count-preview endpoint, so an admin can review
     * the affected users before confirming the (irreversible) soft delete.
     *
     * @return the logins to soft-delete, excluding the Iris bot (admins/super-admins are already excluded by the query)
     */
    private List<String> notEnrolledUserLogins() {
        var inactiveBefore = ZonedDateTime.now().minusMonths(dataCleanupProperties.notEnrolledUsersInactivityMonths()).toInstant();
        return userRepository.findAllNotEnrolledUsersInactiveBefore(inactiveBefore).stream().filter(login -> !User.IRIS_BOT_LOGIN.equals(login)).toList();
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
