package de.tum.cit.aet.artemis.service;

import static de.tum.cit.aet.artemis.core.config.Constants.EXAM_START_WAIT_TIME_MINUTES;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static de.tum.cit.aet.artemis.service.util.RoundingUtil.roundScoreSpecifiedByCourseSettings;
import static java.time.ZonedDateTime.now;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.validation.constraints.NotNull;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.audit.AuditEvent;
import org.springframework.boot.actuate.audit.AuditEventRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.assessment.domain.ComplaintType;
import de.tum.cit.aet.artemis.assessment.domain.ExampleSubmission;
import de.tum.cit.aet.artemis.assessment.domain.Feedback;
import de.tum.cit.aet.artemis.assessment.domain.GradingCriterion;
import de.tum.cit.aet.artemis.assessment.domain.GradingInstruction;
import de.tum.cit.aet.artemis.assessment.domain.ParticipantScore;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.assessment.repository.ComplaintRepository;
import de.tum.cit.aet.artemis.assessment.repository.ComplaintResponseRepository;
import de.tum.cit.aet.artemis.assessment.repository.ExampleSubmissionRepository;
import de.tum.cit.aet.artemis.assessment.repository.FeedbackRepository;
import de.tum.cit.aet.artemis.assessment.repository.GradingCriterionRepository;
import de.tum.cit.aet.artemis.assessment.repository.ParticipantScoreRepository;
import de.tum.cit.aet.artemis.assessment.repository.ResultRepository;
import de.tum.cit.aet.artemis.atlas.domain.competency.CourseCompetency;
import de.tum.cit.aet.artemis.core.config.Constants;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseMode;
import de.tum.cit.aet.artemis.exercise.domain.Submission;
import de.tum.cit.aet.artemis.exercise.domain.Team;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseRepository;
import de.tum.cit.aet.artemis.exercise.repository.StudentParticipationRepository;
import de.tum.cit.aet.artemis.exercise.repository.SubmissionRepository;
import de.tum.cit.aet.artemis.exercise.repository.TeamRepository;
import de.tum.cit.aet.artemis.lti.domain.LtiResourceLaunch;
import de.tum.cit.aet.artemis.lti.repository.Lti13ResourceLaunchRepository;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;
import de.tum.cit.aet.artemis.service.exam.ExamLiveEventsService;
import de.tum.cit.aet.artemis.service.notifications.GroupNotificationScheduleService;
import de.tum.cit.aet.artemis.service.quiz.QuizBatchService;
import de.tum.cit.aet.artemis.web.rest.dto.CourseManagementOverviewExerciseStatisticsDTO;
import de.tum.cit.aet.artemis.web.rest.dto.DueDateStat;
import de.tum.cit.aet.artemis.web.rest.dto.StatsForDashboardDTO;
import de.tum.cit.aet.artemis.web.rest.dto.TutorLeaderboardDTO;
import de.tum.cit.aet.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.cit.aet.artemis.web.rest.errors.EntityNotFoundException;

/**
 * Service Implementation for managing Exercise.
 */
@Profile(PROFILE_CORE)
@Service
public class ExerciseService {

    private static final Logger log = LoggerFactory.getLogger(ExerciseService.class);

    private final AuthorizationCheckService authCheckService;

    private final ExerciseDateService exerciseDateService;

    private final TeamRepository teamRepository;

    private final AuditEventRepository auditEventRepository;

    private final ExerciseRepository exerciseRepository;

    private final ParticipantScoreRepository participantScoreRepository;

    private final SubmissionRepository submissionRepository;

    private final ResultRepository resultRepository;

    private final Lti13ResourceLaunchRepository lti13ResourceLaunchRepository;

    private final StudentParticipationRepository studentParticipationRepository;

    private final UserRepository userRepository;

    private final ComplaintRepository complaintRepository;

    private final TutorLeaderboardService tutorLeaderboardService;

    private final ComplaintResponseRepository complaintResponseRepository;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final GradingCriterionRepository gradingCriterionRepository;

    private final FeedbackRepository feedbackRepository;

    private final RatingService ratingService;

    private final ExampleSubmissionRepository exampleSubmissionRepository;

    private final QuizBatchService quizBatchService;

    private final ExamLiveEventsService examLiveEventsService;

    private final GroupNotificationScheduleService groupNotificationScheduleService;

    public ExerciseService(ExerciseRepository exerciseRepository, AuthorizationCheckService authCheckService, AuditEventRepository auditEventRepository,
            TeamRepository teamRepository, ProgrammingExerciseRepository programmingExerciseRepository, Lti13ResourceLaunchRepository lti13ResourceLaunchRepository,
            StudentParticipationRepository studentParticipationRepository, ResultRepository resultRepository, SubmissionRepository submissionRepository,
            ParticipantScoreRepository participantScoreRepository, UserRepository userRepository, ComplaintRepository complaintRepository,
            TutorLeaderboardService tutorLeaderboardService, ComplaintResponseRepository complaintResponseRepository, GradingCriterionRepository gradingCriterionRepository,
            FeedbackRepository feedbackRepository, RatingService ratingService, ExerciseDateService exerciseDateService, ExampleSubmissionRepository exampleSubmissionRepository,
            QuizBatchService quizBatchService, ExamLiveEventsService examLiveEventsService, GroupNotificationScheduleService groupNotificationScheduleService) {
        this.exerciseRepository = exerciseRepository;
        this.resultRepository = resultRepository;
        this.authCheckService = authCheckService;
        this.auditEventRepository = auditEventRepository;
        this.submissionRepository = submissionRepository;
        this.teamRepository = teamRepository;
        this.participantScoreRepository = participantScoreRepository;
        this.lti13ResourceLaunchRepository = lti13ResourceLaunchRepository;
        this.studentParticipationRepository = studentParticipationRepository;
        this.userRepository = userRepository;
        this.complaintRepository = complaintRepository;
        this.tutorLeaderboardService = tutorLeaderboardService;
        this.complaintResponseRepository = complaintResponseRepository;
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.gradingCriterionRepository = gradingCriterionRepository;
        this.feedbackRepository = feedbackRepository;
        this.exerciseDateService = exerciseDateService;
        this.ratingService = ratingService;
        this.exampleSubmissionRepository = exampleSubmissionRepository;
        this.quizBatchService = quizBatchService;
        this.examLiveEventsService = examLiveEventsService;
        this.groupNotificationScheduleService = groupNotificationScheduleService;
    }

    /**
     * Gets the subset of given exercises that a user is allowed to see
     *
     * @param exercises exercises to filter
     * @param user      user
     * @return subset of the exercises that a user is allowed to access
     */
    public Set<Exercise> filterOutExercisesThatUserShouldNotSee(Set<Exercise> exercises, User user) {
        if (exercises == null || user == null || exercises.isEmpty()) {
            return Set.of();
        }
        // Set is needed here to remove duplicates
        Set<Course> courses = exercises.stream().map(Exercise::getCourseViaExerciseGroupOrCourseMember).collect(Collectors.toSet());
        if (courses.size() != 1) {
            throw new IllegalArgumentException("All exercises must be from the same course!");
        }
        Course course = courses.stream().findFirst().get();

        Set<Exercise> exercisesUserIsAllowedToSee = new HashSet<>();
        if (authCheckService.isAtLeastTeachingAssistantInCourse(course, user)) {
            exercisesUserIsAllowedToSee = exercises;
        }
        else if (authCheckService.isOnlyStudentInCourse(course, user)) {
            if (course.isOnlineCourse()) {
                for (Exercise exercise : exercises) {
                    if (!exercise.isVisibleToStudents()) {
                        continue;
                    }
                    // students in online courses can only see exercises where the lti resource launch exists, otherwise the result cannot be reported later on
                    Collection<LtiResourceLaunch> ltiResourceLaunches = lti13ResourceLaunchRepository.findByUserAndExercise(user, exercise);
                    if (!ltiResourceLaunches.isEmpty()) {
                        exercisesUserIsAllowedToSee.add(exercise);
                    }
                }
            }
            else {
                exercisesUserIsAllowedToSee.addAll(exercises.stream().filter(Exercise::isVisibleToStudents).collect(Collectors.toSet()));
            }
        }
        return exercisesUserIsAllowedToSee;
    }

    /**
     * Given an exercise exerciseId, it creates an object node with numberOfSubmissions, totalNumberOfAssessments, numberOfComplaints and numberOfMoreFeedbackRequests, that are
     * used by both stats for assessment dashboard and for instructor dashboard
     * TODO: refactor and improve this method
     *
     * @param exercise - the exercise we are interested in
     * @param examMode - flag to determine if test run submissions should be deducted from the statistics
     * @return an object node with the stats
     */
    public StatsForDashboardDTO populateCommonStatistics(Exercise exercise, boolean examMode) {
        final long exerciseId = exercise.getId();
        StatsForDashboardDTO stats = new StatsForDashboardDTO();

        Course course = exercise.getCourseViaExerciseGroupOrCourseMember();

        DueDateStat numberOfSubmissions;
        DueDateStat totalNumberOfAssessments;

        if (exercise instanceof ProgrammingExercise) {
            numberOfSubmissions = new DueDateStat(programmingExerciseRepository.countLegalSubmissionsByExerciseIdSubmitted(exerciseId), 0L);
            totalNumberOfAssessments = new DueDateStat(programmingExerciseRepository.countAssessmentsByExerciseIdSubmitted(exerciseId), 0L);
        }
        else {
            numberOfSubmissions = submissionRepository.countSubmissionsForExercise(exerciseId);
            totalNumberOfAssessments = resultRepository.countNumberOfFinishedAssessmentsForExercise(exerciseId);
        }

        stats.setNumberOfSubmissions(numberOfSubmissions);
        stats.setTotalNumberOfAssessments(totalNumberOfAssessments);

        final DueDateStat[] numberOfAssessmentsOfCorrectionRounds;
        int numberOfCorrectionRounds = 1;
        if (examMode) {
            // set number of corrections specific to each correction round
            numberOfCorrectionRounds = exercise.getExerciseGroup().getExam().getNumberOfCorrectionRoundsInExam();
            numberOfAssessmentsOfCorrectionRounds = resultRepository.countNumberOfFinishedAssessmentsForExamExerciseForCorrectionRounds(exercise, numberOfCorrectionRounds);
        }
        else {
            // no examMode here, so correction rounds defaults to 1 and is the same as totalNumberOfAssessments
            numberOfAssessmentsOfCorrectionRounds = new DueDateStat[] { totalNumberOfAssessments };
        }

        stats.setNumberOfAssessmentsOfCorrectionRounds(numberOfAssessmentsOfCorrectionRounds);

        final DueDateStat[] numberOfLockedAssessmentByOtherTutorsOfCorrectionRound;
        numberOfLockedAssessmentByOtherTutorsOfCorrectionRound = resultRepository.countNumberOfLockedAssessmentsByOtherTutorsForExamExerciseForCorrectionRounds(exercise,
                numberOfCorrectionRounds, userRepository.getUserWithGroupsAndAuthorities());
        stats.setNumberOfLockedAssessmentByOtherTutorsOfCorrectionRound(numberOfLockedAssessmentByOtherTutorsOfCorrectionRound);

        final DueDateStat numberOfAutomaticAssistedAssessments = resultRepository.countNumberOfAutomaticAssistedAssessmentsForExercise(exerciseId);
        stats.setNumberOfAutomaticAssistedAssessments(numberOfAutomaticAssistedAssessments);

        final long numberOfMoreFeedbackRequests = complaintRepository.countComplaintsByExerciseIdAndComplaintType(exerciseId, ComplaintType.MORE_FEEDBACK);
        stats.setNumberOfMoreFeedbackRequests(numberOfMoreFeedbackRequests);

        long numberOfComplaints;
        if (examMode) {
            numberOfComplaints = complaintRepository.countByResultParticipationExerciseIdAndComplaintTypeIgnoreTestRuns(exerciseId, ComplaintType.COMPLAINT);
        }
        else {
            numberOfComplaints = complaintRepository.countComplaintsByExerciseIdAndComplaintType(exerciseId, ComplaintType.COMPLAINT);
        }
        stats.setNumberOfComplaints(numberOfComplaints);

        long numberOfComplaintResponses = complaintResponseRepository.countComplaintResponseByExerciseIdAndComplaintTypeAndSubmittedTimeIsNotNull(exerciseId,
                ComplaintType.COMPLAINT);

        stats.setNumberOfOpenComplaints(numberOfComplaints - numberOfComplaintResponses);

        long numberOfMoreFeedbackComplaintResponses = complaintResponseRepository.countComplaintResponseByExerciseIdAndComplaintTypeAndSubmittedTimeIsNotNull(exerciseId,
                ComplaintType.MORE_FEEDBACK);

        stats.setNumberOfOpenMoreFeedbackRequests(numberOfMoreFeedbackRequests - numberOfMoreFeedbackComplaintResponses);

        stats.setNumberOfRatings(ratingService.countRatingsByExerciseId(exerciseId));

        List<TutorLeaderboardDTO> leaderboardEntries = tutorLeaderboardService.getExerciseLeaderboard(exercise);
        stats.setTutorLeaderboardEntries(leaderboardEntries);
        final long totalNumberOfAssessmentLocks = submissionRepository.countLockedSubmissionsByExerciseId(exerciseId);
        stats.setTotalNumberOfAssessmentLocks(totalNumberOfAssessmentLocks);

        stats.setFeedbackRequestEnabled(course.getComplaintsEnabled());
        stats.setFeedbackRequestEnabled(course.getRequestMoreFeedbackEnabled());

        return stats;
    }

    /**
     * Loads exercises with all the necessary information to display them correctly in the Artemis dashboard
     *
     * @param exerciseIds exercises to load
     * @param user        user to load exercise information for
     * @return exercises with all the necessary information loaded for correct display in the Artemis dashboard
     */
    public Set<Exercise> loadExercisesWithInformationForDashboard(Set<Long> exerciseIds, User user) {
        if (exerciseIds == null || user == null) {
            throw new IllegalArgumentException();
        }
        if (exerciseIds.isEmpty()) {
            return new HashSet<>();
        }
        Set<Exercise> exercises = exerciseRepository.findByExerciseIdsWithCategories(exerciseIds);
        // Set is needed here to remove duplicates
        Set<Course> courses = exercises.stream().map(Exercise::getCourseViaExerciseGroupOrCourseMember).collect(Collectors.toSet());
        if (courses.size() != 1) {
            throw new IllegalArgumentException("All exercises must be from the same course!");
        }
        Course course = courses.stream().findFirst().get();
        var participationsOfUserInExercises = studentParticipationRepository.getAllParticipationsOfUserInExercises(user, exercises, false);
        boolean isStudent = !authCheckService.isAtLeastTeachingAssistantInCourse(course, user);
        for (Exercise exercise : exercises) {
            // add participation with submission and result to each exercise
            filterForCourseDashboard(exercise, participationsOfUserInExercises, isStudent);
            // remove sensitive information from the exercise for students
            if (isStudent) {
                exercise.filterSensitiveInformation();
            }
            setAssignedTeamIdForExerciseAndUser(exercise, user);
        }
        return exercises;
    }

    /**
     * Filter all exercises for a given course based on the user role and course settings
     * Assumes that the exercises are already been loaded (i.e. no proxy)
     *
     * @param course corresponding course: exercises
     * @param user   the user entity
     * @return a set of all Exercises for the given course
     */
    public Set<Exercise> filterExercisesForCourse(Course course, User user) {
        Set<Exercise> exercises = course.getExercises();
        if (authCheckService.isAtLeastTeachingAssistantInCourse(course, user)) {
            // no need to filter for tutors/editors/instructors/admins because they can see all exercises of the course
            return exercises;
        }

        if (course.isOnlineCourse()) {
            // this case happens rarely, so we can reload the relevant exercises from the database
            // students in online courses can only see exercises where the lti outcome url exists, otherwise the result cannot be reported later on
            exercises = exerciseRepository.findByCourseIdWhereLtiResourceLaunchExists(course.getId(), user.getLogin());
        }

        // students for this course might not have the right to see it, so we have to
        // filter out exercises that are not released (or explicitly made visible to students) yet
        return exercises.stream().filter(Exercise::isVisibleToStudents).collect(Collectors.toSet());
    }

    /**
     * Loads additional details for team exercises and for active quiz exercises
     * Assumes that the exercises are already been loaded (i.e. no proxy)
     *
     * @param course corresponding course: exercises
     * @param user   the user entity
     */
    public void loadExerciseDetailsIfNecessary(Course course, User user) {
        for (Exercise exercise : course.getExercises()) {
            // only necessary for team exercises
            setAssignedTeamIdForExerciseAndUser(exercise, user);

            // filter out questions and all statistical information about the quizPointStatistic from quizExercises (so users can't see which answer options are correct)
            if (exercise instanceof QuizExercise quizExercise) {
                quizExercise.filterSensitiveInformation();

                // if the quiz is not active the batches do not matter and there is no point in loading them
                if (quizExercise.isQuizStarted() && !quizExercise.isQuizEnded()) {
                    // delete the proxy as it doesn't work; getQuizBatchForStudent will load the batches from the DB directly
                    quizExercise.setQuizBatches(quizBatchService.getQuizBatchForStudentByLogin(quizExercise, user.getLogin()).stream().collect(Collectors.toSet()));
                }
            }
        }
    }

    /**
     * Updates the points of related exercises if the points of exercises have changed
     *
     * @param originalExercise the original exercise
     * @param updatedExercise  the updatedExercise
     */
    @Async
    public void updatePointsInRelatedParticipantScores(Exercise originalExercise, Exercise updatedExercise) {
        if (originalExercise.getMaxPoints().equals(updatedExercise.getMaxPoints()) && originalExercise.getBonusPoints().equals(updatedExercise.getBonusPoints())) {
            return; // nothing to do since points are still correct
        }

        List<ParticipantScore> participantScoreList = participantScoreRepository.findAllByExercise(updatedExercise);
        for (ParticipantScore participantScore : participantScoreList) {
            Double lastPoints = null;
            Double lastRatedPoints = null;
            if (participantScore.getLastScore() != null) {
                lastPoints = roundScoreSpecifiedByCourseSettings(participantScore.getLastScore() * 0.01 * updatedExercise.getMaxPoints(),
                        updatedExercise.getCourseViaExerciseGroupOrCourseMember());
            }
            if (participantScore.getLastRatedScore() != null) {
                lastRatedPoints = roundScoreSpecifiedByCourseSettings(participantScore.getLastRatedScore() * 0.01 * updatedExercise.getMaxPoints(),
                        updatedExercise.getCourseViaExerciseGroupOrCourseMember());
            }
            participantScore.setLastPoints(lastPoints);
            participantScore.setLastRatedPoints(lastRatedPoints);
        }
        participantScoreRepository.saveAll(participantScoreList);
    }

    public void logDeletion(Exercise exercise, Course course, User user) {
        var auditEvent = new AuditEvent(user.getLogin(), Constants.DELETE_EXERCISE, "exercise=" + exercise.getTitle(), "course=" + course.getTitle());
        auditEventRepository.add(auditEvent);
        log.info("User {} has requested to delete {} {} with id {}", user.getLogin(), exercise.getClass().getSimpleName(), exercise.getTitle(), exercise.getId());
    }

    public void logUpdate(Exercise exercise, Course course, User user) {
        var auditEvent = new AuditEvent(user.getLogin(), Constants.EDIT_EXERCISE, "exercise=" + exercise.getTitle(), "course=" + course.getTitle());
        auditEventRepository.add(auditEvent);
        log.info("User {} has updated {} {} with id {}", user.getLogin(), exercise.getClass().getSimpleName(), exercise.getTitle(), exercise.getId());
    }

    /**
     * checks the example submissions of the exercise and removes unnecessary associations to other objects
     *
     * @param exercise the exercise for which example submissions should be checked
     */
    public void checkExampleSubmissions(Exercise exercise) {
        // Avoid recursions
        if (!exercise.getExampleSubmissions().isEmpty()) {
            Set<ExampleSubmission> exampleSubmissionsWithResults = exampleSubmissionRepository.findAllWithResultByExerciseId(exercise.getId());
            exercise.setExampleSubmissions(exampleSubmissionsWithResults);
            exercise.getExampleSubmissions().forEach(exampleSubmission -> exampleSubmission.setExercise(null));
            exercise.getExampleSubmissions().forEach(exampleSubmission -> exampleSubmission.setTutorParticipations(null));
        }
    }

    /**
     * Check to ensure that an updatedExercise is not converted from a course exercise to an exam exercise and vice versa.
     *
     * @param updatedExercise the updated Exercise
     * @param oldExercise     the old Exercise
     * @param entityName      name of the entity
     * @throws BadRequestAlertException if updated exercise was converted
     */
    public void checkForConversionBetweenExamAndCourseExercise(Exercise updatedExercise, Exercise oldExercise, String entityName) throws BadRequestAlertException {
        if (updatedExercise.isExamExercise() != oldExercise.isExamExercise() || updatedExercise.isCourseExercise() != oldExercise.isCourseExercise()) {
            throw new BadRequestAlertException("Course exercise cannot be converted to exam exercise and vice versa", entityName, "conversionBetweenExamAndCourseExercise");
        }
    }

    /**
     * Find the participation in participations that belongs to the given exercise that includes the exercise data, plus the found participation with its most recent relevant
     * result. Filter everything else that is not relevant
     *
     * @param exercise       the exercise that should be filtered (this deletes many field values of the passed exercise object)
     * @param participations the set of participations, wherein to search for the relevant participation
     * @param isStudent      defines if the current user is a student
     */
    public void filterForCourseDashboard(Exercise exercise, Set<StudentParticipation> participations, boolean isStudent) {
        // remove the unnecessary inner course attribute
        exercise.setCourse(null);

        // remove the problem statement, which is loaded in the exercise details call
        exercise.setProblemStatement(null);

        if (exercise instanceof ProgrammingExercise programmingExercise) {
            programmingExercise.setTestRepositoryUri(null);
        }

        // get user's participation for the exercise
        Set<StudentParticipation> relevantParticipations = participations != null ? exercise.findRelevantParticipation(participations) : Set.of();

        // add relevant submission (relevancy depends on InitializationState) with its result to participation
        relevantParticipations.forEach(participation -> {
            // find the latest submission with a rated result, otherwise the latest submission with
            // an unrated result or alternatively the latest submission without a result
            Set<Submission> submissions = participation.getSubmissions();

            // only transmit the relevant result
            // TODO: we should sync the following two and make sure that we return the correct submission and/or result in all scenarios
            Submission submission = (submissions == null || submissions.isEmpty()) ? null : exercise.findAppropriateSubmissionByResults(submissions);
            Submission latestSubmissionWithRatedResult = participation.getExercise().findLatestSubmissionWithRatedResultWithCompletionDate(participation, false);

            Set<Result> results = Set.of();

            if (latestSubmissionWithRatedResult != null && latestSubmissionWithRatedResult.getLatestResult() != null) {
                results = Set.of(latestSubmissionWithRatedResult.getLatestResult());
                // remove inner participation from result
                latestSubmissionWithRatedResult.getLatestResult().setParticipation(null);
                // filter sensitive information about the assessor if the current user is a student
                if (isStudent) {
                    latestSubmissionWithRatedResult.getLatestResult().filterSensitiveInformation();
                }
            }

            // filter sensitive information in submission's result
            if (isStudent && submission != null && submission.getLatestResult() != null) {
                submission.getLatestResult().filterSensitiveInformation();
            }

            // add submission to participation or set it to null
            participation.setSubmissions(submission != null ? Set.of(submission) : null);

            participation.setResults(results);
            if (submission != null) {
                submission.setResults(new ArrayList<>(results));
            }

            // remove inner exercise from participation
            participation.setExercise(null);
        });

        exercise.setStudentParticipations(relevantParticipations);
    }

    /**
     * Get one exercise with all exercise hints and all student questions + answers and with all categories
     *
     * @param exerciseId the id of the exercise to find
     * @param user       the current user
     * @return the exercise
     */
    public Exercise findOneWithDetailsForStudents(Long exerciseId, User user) {
        var exercise = exerciseRepository.findByIdWithDetailsForStudent(exerciseId).orElseThrow(() -> new EntityNotFoundException("Exercise", exerciseId));
        setAssignedTeamIdForExerciseAndUser(exercise, user);
        return exercise;
    }

    /**
     * Sets the transient attribute "studentAssignedTeamId" that contains the id of the team to which the user is assigned
     *
     * @param exercise the exercise for which to set the attribute
     * @param user     the user for which to check to which team (or no team) he belongs to
     */
    private void setAssignedTeamIdForExerciseAndUser(Exercise exercise, User user) {
        // if the exercise is not team-based, there is nothing to do here
        if (exercise.isTeamMode()) {
            Optional<Team> team = teamRepository.findOneByExerciseIdAndUserId(exercise.getId(), user.getId());
            exercise.setStudentAssignedTeamId(team.map(Team::getId).orElse(null));
            exercise.setStudentAssignedTeamIdComputed(true);
        }
    }

    /**
     * Gets the exercise statistics by setting values for each field of the <code>CourseManagementOverviewExerciseStatisticsDTO</code>
     * Exercises with an assessment due date (or due date if there is no assessment due date) in the past are limited to the five most recent
     *
     * @param courseId                 the id of the course
     * @param amountOfStudentsInCourse the amount of students in the course
     * @return A list of filled <code>CourseManagementOverviewExerciseStatisticsDTO</code>
     */
    public List<CourseManagementOverviewExerciseStatisticsDTO> getStatisticsForCourseManagementOverview(Long courseId, Integer amountOfStudentsInCourse) {
        // We only display the latest five past exercises in the client, only calculate statistics for those
        List<Exercise> pastExercises = exerciseRepository.getPastExercisesForCourseManagementOverview(courseId, now());

        Comparator<Exercise> exerciseDateComparator = Comparator.comparing(
                exercise -> exercise.getAssessmentDueDate() != null ? exercise.getAssessmentDueDate() : exercise.getDueDate(), Comparator.nullsLast(Comparator.naturalOrder()));

        List<Exercise> lastFivePastExercises = pastExercises.stream().sorted(exerciseDateComparator.reversed()).limit(5).toList();
        Map<Long, Double> averageScoreById = new HashMap<>();
        if (!lastFivePastExercises.isEmpty()) {
            // Calculate the average score for all five exercises at once
            var averageScore = participantScoreRepository.findAverageScoreForExercises(lastFivePastExercises);
            for (var element : averageScore) {
                averageScoreById.put((Long) element.get("exerciseId"), (Double) element.get("averageScore"));
            }
        }

        // Fill statistics for all exercises potentially displayed on the client
        var exercisesForManagementOverview = exerciseRepository.getActiveExercisesForCourseManagementOverview(courseId, now());
        exercisesForManagementOverview.addAll(lastFivePastExercises);
        return generateCourseManagementDTOs(exercisesForManagementOverview, amountOfStudentsInCourse, averageScoreById);
    }

    /**
     * Generates a <code>CourseManagementOverviewExerciseStatisticsDTO</code> for each given exercise
     *
     * @param exercisesForManagementOverview a set of exercises to generate the statistics for
     * @param amountOfStudentsInCourse       the amount of students in the course
     * @param averageScoreById               the average score for each exercise indexed by exerciseId
     * @return A list of filled <code>CourseManagementOverviewExerciseStatisticsDTO</code>
     */
    private List<CourseManagementOverviewExerciseStatisticsDTO> generateCourseManagementDTOs(Set<Exercise> exercisesForManagementOverview, Integer amountOfStudentsInCourse,
            Map<Long, Double> averageScoreById) {
        List<CourseManagementOverviewExerciseStatisticsDTO> statisticsDTOS = new ArrayList<>();
        for (var exercise : exercisesForManagementOverview) {
            var exerciseId = exercise.getId();
            var exerciseStatisticsDTO = new CourseManagementOverviewExerciseStatisticsDTO();
            exerciseStatisticsDTO.setExerciseId(exerciseId);
            exerciseStatisticsDTO.setExerciseMaxPoints(exercise.getMaxPoints());

            participantScoreRepository.setAverageScoreForStatisticsDTO(exerciseStatisticsDTO, averageScoreById, exercise);
            setStudentsAndParticipationsAmountForStatisticsDTO(exerciseStatisticsDTO, amountOfStudentsInCourse, exercise);
            setAssessmentsAndSubmissionsForStatisticsDTO(exerciseStatisticsDTO, exercise);

            statisticsDTOS.add(exerciseStatisticsDTO);
        }
        return statisticsDTOS;
    }

    /**
     * Sets the amount of students, participations and teams for the given <code>CourseManagementOverviewExerciseStatisticsDTO</code>
     * Only the amount of students in the course is set if the exercise has ended, the rest is set to zero
     *
     * @param exerciseStatisticsDTO    the <code>CourseManagementOverviewExerciseStatisticsDTO</code> to set the amounts for
     * @param amountOfStudentsInCourse the amount of students in the course
     * @param exercise                 the exercise corresponding to the <code>CourseManagementOverviewExerciseStatisticsDTO</code>
     */
    private void setStudentsAndParticipationsAmountForStatisticsDTO(CourseManagementOverviewExerciseStatisticsDTO exerciseStatisticsDTO, Integer amountOfStudentsInCourse,
            Exercise exercise) {
        exerciseStatisticsDTO.setNoOfStudentsInCourse(amountOfStudentsInCourse);

        if (amountOfStudentsInCourse != null && amountOfStudentsInCourse != 0 && exerciseDateService.isBeforeLatestDueDate(exercise)) {
            if (exercise.getMode() == ExerciseMode.TEAM) {
                Long teamParticipations = exerciseRepository.getTeamParticipationCountById(exercise.getId());
                var participations = teamParticipations == null ? 0 : Math.toIntExact(teamParticipations);
                exerciseStatisticsDTO.setNoOfParticipatingStudentsOrTeams(participations);

                Integer teams = teamRepository.getNumberOfTeamsForExercise(exercise.getId());
                exerciseStatisticsDTO.setNoOfTeamsInCourse(teams);
                exerciseStatisticsDTO.setParticipationRateInPercent(teams == null || teams == 0 ? 0.0 : Math.round(participations * 1000.0 / teams) / 10.0);
            }
            else {
                Long studentParticipations = exerciseRepository.getStudentParticipationCountById(exercise.getId());
                var participations = studentParticipations == null ? 0 : Math.toIntExact(studentParticipations);
                exerciseStatisticsDTO.setNoOfParticipatingStudentsOrTeams(participations);

                exerciseStatisticsDTO.setParticipationRateInPercent(Math.round(participations * 1000.0 / amountOfStudentsInCourse) / 10.0);
            }
        }
        else {
            exerciseStatisticsDTO.setNoOfParticipatingStudentsOrTeams(0);
            exerciseStatisticsDTO.setParticipationRateInPercent(0D);
        }
    }

    /**
     * Sets the amount of rated assessments and submissions done for the given <code>CourseManagementOverviewExerciseStatisticsDTO</code>
     * The amounts are set to zero if the assessment due date has passed
     *
     * @param exerciseStatisticsDTO the <code>CourseManagementOverviewExerciseStatisticsDTO</code> to set the amounts for
     * @param exercise              the exercise corresponding to the <code>CourseManagementOverviewExerciseStatisticsDTO</code>
     */
    private void setAssessmentsAndSubmissionsForStatisticsDTO(CourseManagementOverviewExerciseStatisticsDTO exerciseStatisticsDTO, Exercise exercise) {
        if (ExerciseDateService.isAfterAssessmentDueDate(exercise)) {
            exerciseStatisticsDTO.setNoOfRatedAssessments(0L);
            exerciseStatisticsDTO.setNoOfSubmissionsInTime(0L);
            exerciseStatisticsDTO.setNoOfAssessmentsDoneInPercent(0D);
        }
        else {
            long numberOfRatedAssessments = resultRepository.countNumberOfRatedResultsForExercise(exercise.getId());
            long noOfSubmissionsInTime = submissionRepository.countByExerciseIdSubmittedBeforeDueDate(exercise.getId());
            exerciseStatisticsDTO.setNoOfRatedAssessments(numberOfRatedAssessments);
            exerciseStatisticsDTO.setNoOfSubmissionsInTime(noOfSubmissionsInTime);
            exerciseStatisticsDTO.setNoOfAssessmentsDoneInPercent(noOfSubmissionsInTime == 0 ? 0 : Math.round(numberOfRatedAssessments * 1000.0 / noOfSubmissionsInTime) / 10.0);
        }
    }

    /**
     * Checks the exercise structured grading instructions if any of them is associated with the feedback
     * then, sets the corresponding exercise field
     *
     * @param gradingCriteria grading criteria list of exercise
     * @param exercise        exercise to update *
     */
    public void checkExerciseIfStructuredGradingInstructionFeedbackUsed(Set<GradingCriterion> gradingCriteria, Exercise exercise) {
        boolean hasFeedbackFromStructuredGradingInstructionUsed = feedbackRepository.hasFeedbackByExerciseGradingCriteria(gradingCriteria);

        if (hasFeedbackFromStructuredGradingInstructionUsed) {
            exercise.setGradingInstructionFeedbackUsed(true);
        }
    }

    /**
     * Re-evaluates the exercise before saving
     * 1. The feedback associated with the exercise grading instruction needs to be updated
     * 2. After updating feedback, result needs to be re-calculated
     *
     * @param exercise                                    exercise to re-evaluate
     * @param deleteFeedbackAfterGradingInstructionUpdate boolean flag that indicates whether the associated feedback should be deleted or not *
     */
    public void reEvaluateExercise(Exercise exercise, boolean deleteFeedbackAfterGradingInstructionUpdate) {
        Set<GradingCriterion> gradingCriteria = exercise.getGradingCriteria();
        // retrieve the feedback associated with the structured grading instructions
        List<Feedback> feedbackToBeUpdated = feedbackRepository.findFeedbackByExerciseGradingCriteria(gradingCriteria);

        // collect all structured grading instructions into the list
        List<GradingInstruction> gradingInstructions = gradingCriteria.stream().flatMap(gradingCriterion -> gradingCriterion.getStructuredGradingInstructions().stream()).toList();

        // update the related fields for feedback
        for (GradingInstruction instruction : gradingInstructions) {
            for (Feedback feedback : feedbackToBeUpdated) {
                if (feedback.getGradingInstruction().getId().equals(instruction.getId())) {
                    feedback.setCredits(instruction.getCredits());
                    feedback.setPositiveViaCredits();
                }
            }
        }
        feedbackRepository.saveAll(feedbackToBeUpdated);

        List<Feedback> feedbackToBeDeleted = getFeedbackToBeDeletedAfterGradingInstructionUpdate(deleteFeedbackAfterGradingInstructionUpdate, gradingInstructions, exercise);

        // update the grading criteria to re-calculate the results considering the updated usage limits
        gradingCriterionRepository.saveAll(exercise.getGradingCriteria());

        List<Result> results = resultRepository.findWithEagerSubmissionAndFeedbackByParticipationExerciseId(exercise.getId());

        // add example submission results that belong exercise
        if (!exercise.getExampleSubmissions().isEmpty()) {
            results.addAll(resultRepository.getResultForExampleSubmissions(exercise.getExampleSubmissions()));
        }

        // re-calculate the results after updating the feedback
        for (Result result : results) {
            if (!feedbackToBeDeleted.isEmpty()) {
                List<Feedback> existingFeedback = result.getFeedbacks();
                if (!existingFeedback.isEmpty()) {
                    existingFeedback.removeAll(feedbackToBeDeleted);
                }
                // first save the feedback (that is not yet in the database) to prevent null index exception
                List<Feedback> savedFeedback = feedbackRepository.saveFeedbacks(existingFeedback);
                result.updateAllFeedbackItems(savedFeedback, exercise instanceof ProgrammingExercise);
            }

            if (!(exercise instanceof ProgrammingExercise programmingExercise)) {
                resultRepository.submitResult(result, exercise);
            }
            else {
                result.calculateScoreForProgrammingExercise(programmingExercise);
                resultRepository.save(result);
            }
        }
    }

    /**
     * Gets the list of feedback that is associated with deleted grading instructions
     *
     * @param deleteFeedbackAfterGradingInstructionUpdate boolean flag that indicates whether the associated feedback should be deleted or not
     * @param gradingInstructions                         grading instruction list to update
     * @param exercise                                    exercise for which the grading instructions have to be updated
     * @return list including Feedback entries that have to be deleted due to updated grading instructions
     */
    public List<Feedback> getFeedbackToBeDeletedAfterGradingInstructionUpdate(boolean deleteFeedbackAfterGradingInstructionUpdate, List<GradingInstruction> gradingInstructions,
            Exercise exercise) {
        List<Feedback> feedbackToBeDeleted = new ArrayList<>();
        // check if the user decided to remove the feedback after deleting the associated grading instructions
        if (deleteFeedbackAfterGradingInstructionUpdate) {
            Set<Long> updatedInstructionIds = gradingInstructions.stream().map(GradingInstruction::getId).collect(Collectors.toCollection(HashSet::new));
            // retrieve the grading instructions from database for backup
            Set<GradingCriterion> backupGradingCriteria = gradingCriterionRepository.findByExerciseIdWithEagerGradingCriteria(exercise.getId());
            List<Long> backupInstructionIds = backupGradingCriteria.stream().flatMap(gradingCriterion -> gradingCriterion.getStructuredGradingInstructions().stream())
                    .map(GradingInstruction::getId).toList();

            // collect deleted grading instruction ids into the list
            List<Long> gradingInstructionIdsToBeDeleted = backupInstructionIds.stream().filter(backupInstructionId -> !updatedInstructionIds.contains(backupInstructionId))
                    .toList();

            // determine the feedback to be deleted
            feedbackToBeDeleted = feedbackRepository.findFeedbackByGradingInstructionIds(gradingInstructionIdsToBeDeleted);
        }
        return feedbackToBeDeleted;
    }

    /**
     * Removes competency from all exercises.
     *
     * @param exercises  set of exercises
     * @param competency competency to remove
     */
    public void removeCompetency(@NotNull Set<Exercise> exercises, @NotNull CourseCompetency competency) {
        exercises.forEach(exercise -> exercise.getCompetencies().remove(competency));
        exerciseRepository.saveAll(exercises);
    }

    /**
     * Notifies students about exercise changes.
     * For course exercises, notifications are used. For exam exercises, live events are used instead.
     *
     * @param originalExercise the original exercise
     * @param updatedExercise  the updated exercise
     * @param notificationText custom notification text
     */
    public void notifyAboutExerciseChanges(Exercise originalExercise, Exercise updatedExercise, String notificationText) {
        if (originalExercise.isCourseExercise()) {
            groupNotificationScheduleService.checkAndCreateAppropriateNotificationsWhenUpdatingExercise(originalExercise, updatedExercise, notificationText);
        }
        // start sending problem statement updates within the last 5 minutes before the exam starts
        else if (now().plusMinutes(EXAM_START_WAIT_TIME_MINUTES).isAfter(originalExercise.getExam().getStartDate()) && originalExercise.isExamExercise()
                && !StringUtils.equals(originalExercise.getProblemStatement(), updatedExercise.getProblemStatement())) {
            User instructor = userRepository.getUser();
            this.examLiveEventsService.createAndSendProblemStatementUpdateEvent(updatedExercise, notificationText, instructor);
        }
    }
}
