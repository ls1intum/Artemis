package de.tum.cit.aet.artemis.core.service.course;

import static de.tum.cit.aet.artemis.assessment.domain.ComplaintType.COMPLAINT;
import static de.tum.cit.aet.artemis.assessment.domain.ComplaintType.MORE_FEEDBACK;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static de.tum.cit.aet.artemis.core.util.DateUtil.getWeekOfDate;
import static de.tum.cit.aet.artemis.core.util.DateUtil.sortDataIntoWeeks;
import static de.tum.cit.aet.artemis.core.util.RoundingUtil.roundScoreSpecifiedByCourseSettings;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.IsoFields;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.assessment.domain.GradingScale;
import de.tum.cit.aet.artemis.assessment.repository.ComplaintRepository;
import de.tum.cit.aet.artemis.assessment.repository.ComplaintResponseRepository;
import de.tum.cit.aet.artemis.assessment.repository.ParticipantScoreRepository;
import de.tum.cit.aet.artemis.assessment.repository.RatingRepository;
import de.tum.cit.aet.artemis.assessment.repository.ResultRepository;
import de.tum.cit.aet.artemis.assessment.service.ComplaintService;
import de.tum.cit.aet.artemis.assessment.service.PresentationPointsCalculationService;
import de.tum.cit.aet.artemis.assessment.service.TutorLeaderboardService;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.dto.CourseManagementDetailViewDTO;
import de.tum.cit.aet.artemis.core.dto.DueDateStat;
import de.tum.cit.aet.artemis.core.dto.StatisticsEntry;
import de.tum.cit.aet.artemis.core.dto.StatsForDashboardDTO;
import de.tum.cit.aet.artemis.core.dto.TutorLeaderboardDTO;
import de.tum.cit.aet.artemis.core.repository.CourseRepository;
import de.tum.cit.aet.artemis.core.repository.LLMTokenUsageTraceRepository;
import de.tum.cit.aet.artemis.core.repository.StatisticsRepository;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.util.TimeUtil;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.IncludedInOverallScore;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseRepository;
import de.tum.cit.aet.artemis.exercise.repository.StudentParticipationRepository;
import de.tum.cit.aet.artemis.exercise.repository.SubmissionRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;

/**
 * Service for retrieving course statistics, such as active students, average scores, and other metrics.
 * This service is used in the course management detail view and dashboard.
 */
@Service
@Profile(PROFILE_CORE)
@Lazy
public class CourseStatsService {

    private static final Logger log = LoggerFactory.getLogger(CourseStatsService.class);

    private final CourseRepository courseRepository;

    private final StatisticsRepository statisticsRepository;

    private final UserRepository userRepository;

    private final ExerciseRepository exerciseRepository;

    private final ResultRepository resultRepository;

    private final SubmissionRepository submissionRepository;

    private final PresentationPointsCalculationService presentationPointsCalculationService;

    private final ComplaintService complaintService;

    private final TutorLeaderboardService tutorLeaderboardService;

    private final ParticipantScoreRepository participantScoreRepository;

    private final StudentParticipationRepository studentParticipationRepository;

    private final ComplaintRepository complaintRepository;

    private final ComplaintResponseRepository complaintResponseRepository;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final RatingRepository ratingRepository;

    private final LLMTokenUsageTraceRepository llmTokenUsageTraceRepository;

    public CourseStatsService(CourseRepository courseRepository, StatisticsRepository statisticsRepository, UserRepository userRepository, ExerciseRepository exerciseRepository,
            ResultRepository resultRepository, SubmissionRepository submissionRepository, PresentationPointsCalculationService presentationPointsCalculationService,
            ComplaintService complaintService, TutorLeaderboardService tutorLeaderboardService, ParticipantScoreRepository participantScoreRepository,
            StudentParticipationRepository studentParticipationRepository, ComplaintRepository complaintRepository, ComplaintResponseRepository complaintResponseRepository,
            ProgrammingExerciseRepository programmingExerciseRepository, RatingRepository ratingRepository, LLMTokenUsageTraceRepository llmTokenUsageTraceRepository) {
        this.courseRepository = courseRepository;
        this.statisticsRepository = statisticsRepository;
        this.userRepository = userRepository;
        this.exerciseRepository = exerciseRepository;
        this.resultRepository = resultRepository;
        this.submissionRepository = submissionRepository;
        this.presentationPointsCalculationService = presentationPointsCalculationService;
        this.complaintService = complaintService;
        this.tutorLeaderboardService = tutorLeaderboardService;
        this.participantScoreRepository = participantScoreRepository;
        this.studentParticipationRepository = studentParticipationRepository;
        this.complaintRepository = complaintRepository;
        this.complaintResponseRepository = complaintResponseRepository;
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.ratingRepository = ratingRepository;
        this.llmTokenUsageTraceRepository = llmTokenUsageTraceRepository;
    }

    /**
     * Get the active students for these particular exercise ids
     *
     * @param exerciseIds the ids to get the active students for
     * @param periodIndex the deviation from the current time
     * @param length      the length of the chart which we want to fill. This can either be 4 for the course overview or 17 for the course detail view
     * @param date        the date for which the active students' calculation should end (e.g. now)
     * @return An Integer list containing active students for each index. An index corresponds to a week
     */
    public List<Integer> getActiveStudents(Set<Long> exerciseIds, long periodIndex, int length, ZonedDateTime date) {
        /*
         * If the course did not start yet, the length of the chart will be negative (as the time difference between the start date end the current date is passed). In this case,
         * we return an empty list.
         */
        if (length < 0) {
            return new ArrayList<>(0);
        }
        LocalDateTime localStartDate = date.toLocalDateTime().with(DayOfWeek.MONDAY);
        LocalDateTime localEndDate = date.toLocalDateTime().with(DayOfWeek.SUNDAY);
        ZoneId zone = date.getZone();
        // startDate is the starting point of the data collection which is the Monday 3 weeks ago +/- the deviation from the current timeframe
        ZonedDateTime startDate = localStartDate.atZone(zone).minusWeeks((length - 1) + (length * (-periodIndex))).withHour(0).withMinute(0).withSecond(0).withNano(0);
        // the endDate depends on whether the current week is shown. If it is, the endDate is the Sunday of the current week at 23:59.
        // If the timeframe was adapted (periodIndex != 0), the endDate needs to be adapted according to the deviation
        ZonedDateTime endDate = periodIndex != 0 ? localEndDate.atZone(zone).minusWeeks(length * (-periodIndex)).withHour(23).withMinute(59).withSecond(59)
                : localEndDate.atZone(zone).withHour(23).withMinute(59).withSecond(59);
        if (exerciseIds.isEmpty()) {
            // avoid database call if there are no exercises to reduce performance issues
            return List.of();
        }
        long start = System.currentTimeMillis();
        List<StatisticsEntry> outcome = courseRepository.getActiveStudents(exerciseIds, startDate, endDate);
        log.debug("courseRepository.getActiveStudents took {} ms for exercises with ids {} between start {} and end {}", System.currentTimeMillis() - start, exerciseIds, startDate,
                endDate);
        List<StatisticsEntry> distinctOutcome = removeDuplicateActiveUserRows(outcome, startDate);
        List<Integer> result = new ArrayList<>(Collections.nCopies(length, 0));
        sortDataIntoWeeks(distinctOutcome, result, startDate);
        return result;
    }

    /**
     * The List of StatisticsEntries can contain duplicated entries, which means that a user has two entries in the same week.
     * This method compares the values and returns a List<StatisticsEntry> without duplicated entries.
     *
     * @param activeUserRows a list of entries
     * @param startDate      the startDate of the period
     * @return a List<StatisticsEntry> containing date and amount of active users in this period
     */

    private List<StatisticsEntry> removeDuplicateActiveUserRows(List<StatisticsEntry> activeUserRows, ZonedDateTime startDate) {
        int startIndex = getWeekOfDate(startDate);
        Map<Integer, Set<String>> usersByDate = new HashMap<>();
        for (StatisticsEntry listElement : activeUserRows) {
            // listElement.date has the form "2021-05-04", to convert it to ZonedDateTime, it needs a time
            String dateOfElement = listElement.getDate() + " 10:00";
            var zone = startDate.getZone();
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
            ZonedDateTime date = LocalDateTime.parse(dateOfElement, formatter).atZone(zone);
            int index = getWeekOfDate(date);
            /*
             * The database stores entries in UTC, so it can happen that entries lay in the calendar week one week before the calendar week of the startDate If startDate lays in a
             * calendar week other than the first one, we simply check whether the calendar week of the entry equals to the calendar week of startDate - 1. If startDate lays in the
             * first calendar week, we check whether the calendar week of the entry equals the last calendar week of the prior year. In either case, if the condition resolves to
             * true, we shift the index the submission is sorted in to the calendar week of startDate, as this is the first bucket in the timeframe of interest.
             */
            var unifiedDateWeekBeforeStartIndex = startIndex == 1 ? Math.toIntExact(IsoFields.WEEK_OF_WEEK_BASED_YEAR.rangeRefinedBy(startDate.minusWeeks(1)).getMaximum())
                    : startIndex - 1;
            index = index == unifiedDateWeekBeforeStartIndex ? startIndex : index;
            statisticsRepository.addUserToTimeslot(usersByDate, listElement, index);
        }
        List<StatisticsEntry> returnList = new ArrayList<>();
        usersByDate.forEach((date, users) -> {
            int year = date < getWeekOfDate(startDate) ? startDate.getYear() + 1 : startDate.getYear();
            ZonedDateTime firstDateOfYear = ZonedDateTime.of(year, 1, 1, 0, 0, 0, 0, startDate.getZone());
            ZonedDateTime start = getWeekOfDate(firstDateOfYear) == 1 ? firstDateOfYear.plusWeeks(date - 1) : firstDateOfYear.plusWeeks(date);
            StatisticsEntry listElement = new StatisticsEntry(start, users.size());
            returnList.add(listElement);
        });
        return returnList;
    }

    /**
     * Fetches Course Management Detail View data from repository and returns a DTO
     *
     * @param course       the course for with the details should be calculated
     * @param gradingScale the grading scale for the course
     * @return The DTO for the course management detail view
     */
    public CourseManagementDetailViewDTO getStatsForDetailView(Course course, GradingScale gradingScale) {

        long start = System.currentTimeMillis();
        Set<Exercise> courseExercises = exerciseRepository.findAllExercisesByCourseId(course.getId());
        log.debug("exerciseRepository.findAllExercisesByCourseId took {} ms for course with id {}", System.currentTimeMillis() - start, course.getId());
        start = System.currentTimeMillis();
        Set<Long> courseExerciseIdsWithManualAssessments = exerciseRepository.findExerciseIdsWithManualAssessmentByCourseId(course.getId());
        log.debug("exerciseRepository.findExerciseIdsWithManualAssessmentsByCourseId took {} ms for course with id {}", System.currentTimeMillis() - start, course.getId());
        if (courseExercises == null || courseExercises.isEmpty()) {
            return new CourseManagementDetailViewDTO(0.0, 0L, 0L, 0.0, 0L, 0L, 0.0, 0L, 0L, 0.0, 0.0, 0.0, 0.0);
        }
        // For the average score we need to only consider scores which are included completely or as bonus
        Set<Exercise> includedExercises = courseExercises.stream().filter(Exercise::isCourseExercise)
                .filter(exercise -> !exercise.getIncludedInOverallScore().equals(IncludedInOverallScore.NOT_INCLUDED)).collect(Collectors.toSet());
        start = System.currentTimeMillis();
        Double averageScoreForCourse = participantScoreRepository.findAvgRatedScore(includedExercises);
        log.debug("participantScoreRepository.findAvgRatedScore took {} ms for exercises with ids {}", System.currentTimeMillis() - start,
                includedExercises.stream().map(Exercise::getId).collect(Collectors.toSet()));
        averageScoreForCourse = averageScoreForCourse != null ? averageScoreForCourse : 0.0;
        double currentMaxAverageScore = includedExercises.stream().map(Exercise::getMaxPoints).mapToDouble(Double::doubleValue).sum();

        // calculate scores taking presentation points into account, if a grading scale is present and set for graded presentations
        if (gradingScale != null && course.equals(gradingScale.getCourse()) && gradingScale.getPresentationsNumber() != null && gradingScale.getPresentationsWeight() != null) {
            double maxBaseScore = includedExercises.stream().filter(e -> !e.getIncludedInOverallScore().equals(IncludedInOverallScore.INCLUDED_AS_BONUS))
                    .map(Exercise::getMaxPoints).mapToDouble(Double::doubleValue).sum();
            start = System.currentTimeMillis();
            currentMaxAverageScore += presentationPointsCalculationService.calculateReachablePresentationPoints(gradingScale, maxBaseScore);
            log.debug("presentationPointsCalculationService.calculateReachablePresentationPoints took {} ms for grading scale with id {}", System.currentTimeMillis() - start,
                    gradingScale.getId());
            start = System.currentTimeMillis();
            double avgPresentationScore = studentParticipationRepository.getAvgPresentationScoreByCourseId(course.getId());
            log.debug("studentParticipationRepository.getAvgPresentationScoreByCourseId took {} ms for course with id {}", System.currentTimeMillis() - start, course.getId());
            averageScoreForCourse = gradingScale.getPresentationsWeight() / 100.0 * avgPresentationScore
                    + (100.0 - gradingScale.getPresentationsWeight()) / 100.0 * averageScoreForCourse;
        }

        Set<Long> exerciseIds = courseExercises.stream().map(Exercise::getId).collect(Collectors.toSet());
        start = System.currentTimeMillis();
        long numberOfAssessments = resultRepository.countNumberOfFinishedAssessmentsForExerciseIdsIgnoreTestRuns(courseExerciseIdsWithManualAssessments);
        log.debug("resultRepository.countNumberOfFinishedAssessmentsForExerciseIdsIgnoreTestRuns took {} ms for exercises with ids {}", System.currentTimeMillis() - start,
                courseExerciseIdsWithManualAssessments);
        start = System.currentTimeMillis();
        long numberOfInTimeSubmissions = submissionRepository.countAllByExerciseIdsSubmittedBeforeDueDate(exerciseIds)
                + programmingExerciseRepository.countAllSubmissionsByExerciseIdsSubmitted(exerciseIds);
        log.debug("number of in time submissions: {}", numberOfInTimeSubmissions);
        log.debug(
                "submissionRepository.countAllByExerciseIdsSubmittedBeforeDueDate and programmingExerciseRepository.countAllSubmissionsByExerciseIdsSubmitted took {} ms for exercises with ids {}",
                System.currentTimeMillis() - start, exerciseIds);
        start = System.currentTimeMillis();
        long numberOfLateSubmissions = submissionRepository.countAllByExerciseIdsSubmittedAfterDueDate(exerciseIds);
        log.debug("submissionRepository.countAllByExerciseIdsSubmittedAfterDueDate took {} ms for exercises with ids {}", System.currentTimeMillis() - start, exerciseIds);

        long numberOfSubmissions = numberOfInTimeSubmissions + numberOfLateSubmissions;
        log.debug("number of late submissions: {}, total number of submissions: {}", numberOfLateSubmissions, numberOfSubmissions);
        var currentPercentageAssessments = calculatePercentage(numberOfAssessments, numberOfSubmissions);

        long currentAbsoluteComplaints = 0;
        long currentMaxComplaints = 0;
        double currentPercentageComplaints = 0.0;

        if (course.getComplaintsEnabled()) {
            start = System.currentTimeMillis();
            currentAbsoluteComplaints = complaintResponseRepository.countComplaintResponsesForExerciseIdsAndComplaintType(exerciseIds, COMPLAINT);
            log.debug("complaintResponseRepository.countComplaintResponsesForExerciseIdsAndComplaintType took {} ms for course with id {}", System.currentTimeMillis() - start,
                    course.getId());
            start = System.currentTimeMillis();
            currentMaxComplaints = complaintRepository.countByExerciseIdsAndComplaintType(exerciseIds, COMPLAINT);
            log.debug("complaintRepository.countByExerciseIdsAndComplaintType took {} ms for course with id {}", System.currentTimeMillis() - start, course.getId());
            currentPercentageComplaints = calculatePercentage(currentAbsoluteComplaints, currentMaxComplaints);
        }

        long currentAbsoluteMoreFeedbacks = 0;
        long currentMaxMoreFeedbacks = 0;
        double currentPercentageMoreFeedbacks = 0;

        if (course.getRequestMoreFeedbackEnabled()) {
            start = System.currentTimeMillis();
            currentAbsoluteMoreFeedbacks = complaintResponseRepository.countComplaintResponsesForExerciseIdsAndComplaintType(exerciseIds, MORE_FEEDBACK);
            log.debug("complaintResponseRepository.countComplaintResponsesForExerciseIdsAndComplaintType took {} ms for course with id {}", System.currentTimeMillis() - start,
                    course.getId());
            start = System.currentTimeMillis();
            currentMaxMoreFeedbacks = complaintRepository.countByExerciseIdsAndComplaintType(exerciseIds, MORE_FEEDBACK);
            log.debug("complaintRepository.countByExerciseIdsAndComplaintType took {} ms for course with id {}", System.currentTimeMillis() - start, course.getId());
            currentPercentageMoreFeedbacks = calculatePercentage(currentAbsoluteMoreFeedbacks, currentMaxMoreFeedbacks);
        }
        double currentAbsoluteAverageScore = roundScoreSpecifiedByCourseSettings((averageScoreForCourse / 100.0) * currentMaxAverageScore, course);
        double currentPercentageAverageScore = currentMaxAverageScore > 0.0 ? roundScoreSpecifiedByCourseSettings(averageScoreForCourse, course) : 0.0;
        start = System.currentTimeMillis();
        double currentTotalLlmCostInEur = llmTokenUsageTraceRepository.calculateTotalLlmCostInEurForCourse(course.getId());
        log.debug("llmTokenUsageTraceRepository.calculateTotalLlmCostInEurForCourse took {} ms for course with id {}", System.currentTimeMillis() - start, course.getId());

        return new CourseManagementDetailViewDTO(currentPercentageAssessments, numberOfAssessments, numberOfSubmissions, currentPercentageComplaints, currentAbsoluteComplaints,
                currentMaxComplaints, currentPercentageMoreFeedbacks, currentAbsoluteMoreFeedbacks, currentMaxMoreFeedbacks, currentPercentageAverageScore,
                currentAbsoluteAverageScore, currentMaxAverageScore, currentTotalLlmCostInEur);
    }

    private double calculatePercentage(double positive, double total) {
        return total > 0.0 ? Math.round(positive * 1000.0 / total) / 10.0 : 0.0;
    }

    /**
     * calculate statistics for the course administration dashboard
     *
     * @param course the course for which the statistics should be calculated
     * @return a DTO containing the statistics
     */
    public StatsForDashboardDTO getStatsForDashboardDTO(Course course) {
        Set<Long> courseExerciseIds = exerciseRepository.findExerciseIdsByCourseId(course.getId());
        Set<Long> courseExerciseIdsWithManualAssessments = exerciseRepository.findExerciseIdsWithManualAssessmentByCourseId(course.getId());

        StatsForDashboardDTO stats = new StatsForDashboardDTO();

        long numberOfInTimeSubmissions = submissionRepository.countAllByExerciseIdsSubmittedBeforeDueDate(courseExerciseIds);
        numberOfInTimeSubmissions += programmingExerciseRepository.countAllSubmissionsByExerciseIdsSubmitted(courseExerciseIds);

        final long numberOfLateSubmissions = submissionRepository.countAllByExerciseIdsSubmittedAfterDueDate(courseExerciseIds);
        long numberOfAssessments = resultRepository.countNumberOfAssessments(courseExerciseIdsWithManualAssessments);
        stats.setTotalNumberOfAssessments(numberOfAssessments);

        stats.setNumberOfSubmissions(new DueDateStat(numberOfInTimeSubmissions, numberOfLateSubmissions));

        final long numberOfMoreFeedbackRequests = complaintService.countMoreFeedbackRequestsByExerciseIds(courseExerciseIds);
        stats.setNumberOfMoreFeedbackRequests(numberOfMoreFeedbackRequests);
        final long numberOfMoreFeedbackComplaintResponses = complaintService.countMoreFeedbackRequestResponsesByExerciseIds(courseExerciseIds);
        stats.setNumberOfOpenMoreFeedbackRequests(numberOfMoreFeedbackRequests - numberOfMoreFeedbackComplaintResponses);
        final long numberOfComplaints = complaintService.countComplaintsByExerciseIds(courseExerciseIds);
        stats.setNumberOfComplaints(numberOfComplaints);
        final long numberOfComplaintResponses = complaintService.countComplaintResponsesByExerciseIds(courseExerciseIds);
        stats.setNumberOfOpenComplaints(numberOfComplaints - numberOfComplaintResponses);
        final long numberOfAssessmentLocks = submissionRepository.countLockedSubmissionsByUserIdAndCourseId(userRepository.getUserWithGroupsAndAuthorities().getId(),
                course.getId());
        stats.setNumberOfAssessmentLocks(numberOfAssessmentLocks);
        final long totalNumberOfAssessmentLocks = submissionRepository.countLockedSubmissionsByCourseId(course.getId());
        stats.setTotalNumberOfAssessmentLocks(totalNumberOfAssessmentLocks);

        List<TutorLeaderboardDTO> leaderboardEntries = tutorLeaderboardService.getCourseLeaderboard(course, courseExerciseIdsWithManualAssessments);
        stats.setTutorLeaderboardEntries(leaderboardEntries);
        stats.setNumberOfRatings(ratingRepository.countByResult_Submission_Participation_Exercise_Course_Id(course.getId()));
        return stats;
    }

    /**
     * Determines end date for the displayed time span of active student charts
     * If the course end date is passed, only information until this date are collected and sent
     *
     * @param course the corresponding course the active students should be collected
     * @return end date of the time span
     */
    public ZonedDateTime determineEndDateForActiveStudents(Course course) {
        var endDate = TimeUtil.now();
        if (course.getEndDate() != null && TimeUtil.now().isAfter(course.getEndDate())) {
            endDate = course.getEndDate();
        }
        return endDate;
    }

    /**
     * Determines the allowed time span for active student charts
     * The span time can be restricted if the temporal distance between the course start date
     * and the priorly determined end date is smaller than the intended time frame
     *
     * @param course      the corresponding course the time frame should be computed
     * @param endDate     the priorly determined end date of the time span
     * @param maximalSize the normal time span size
     * @return the allowed time span size
     */
    public int determineTimeSpanSizeForActiveStudents(Course course, ZonedDateTime endDate, int maximalSize) {
        var spanTime = maximalSize;
        if (course.getStartDate() != null) {
            long amountOfWeeksBetween = calculateWeeksBetweenDates(course.getStartDate(), endDate);
            spanTime = Math.toIntExact(Math.min(maximalSize, amountOfWeeksBetween));
        }
        return spanTime;
    }

    /**
     * Auxiliary method that returns the number of weeks between two dates
     * Note: The calculation includes the week of the end date. This is needed for the active students line charts
     *
     * @param startDate the start date of the period to calculate
     * @param endDate   the end date of the period to calculate
     * @return the number of weeks the period contains + one week
     */
    public long calculateWeeksBetweenDates(ZonedDateTime startDate, ZonedDateTime endDate) {
        startDate = startDate.withZoneSameInstant(endDate.getZone());
        var mondayInWeekOfStart = startDate.with(DayOfWeek.MONDAY).withHour(0).withMinute(0).withSecond(0).withNano(0);
        var mondayInWeekOfEnd = endDate.plusWeeks(1).with(DayOfWeek.MONDAY).withHour(0).withMinute(0).withSecond(0).withNano(0);
        return mondayInWeekOfStart.until(mondayInWeekOfEnd, ChronoUnit.WEEKS);
    }

}
