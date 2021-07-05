package de.tum.in.www1.artemis.service;

import static de.tum.in.www1.artemis.service.util.RoundingUtil.round;

import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.audit.AuditEvent;
import org.springframework.boot.actuate.audit.AuditEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import de.tum.in.www1.artemis.config.Constants;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.ComplaintType;
import de.tum.in.www1.artemis.domain.enumeration.ExerciseMode;
import de.tum.in.www1.artemis.domain.enumeration.InitializationState;
import de.tum.in.www1.artemis.domain.exam.Exam;
import de.tum.in.www1.artemis.domain.exam.StudentExam;
import de.tum.in.www1.artemis.domain.lecture.ExerciseUnit;
import de.tum.in.www1.artemis.domain.modeling.ModelingExercise;
import de.tum.in.www1.artemis.domain.metis.AnswerPost;
import de.tum.in.www1.artemis.domain.metis.Post;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseStudentParticipation;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.domain.quiz.QuizExercise;
import de.tum.in.www1.artemis.domain.quiz.QuizSubmission;
import de.tum.in.www1.artemis.domain.scores.ParticipantScore;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.repository.metis.AnswerPostRepository;
import de.tum.in.www1.artemis.repository.metis.PostRepository;
import de.tum.in.www1.artemis.service.programming.ProgrammingAssessmentService;
import de.tum.in.www1.artemis.service.programming.ProgrammingExerciseService;
import de.tum.in.www1.artemis.service.scheduled.quiz.QuizScheduleService;
import de.tum.in.www1.artemis.web.rest.dto.CourseManagementOverviewExerciseStatisticsDTO;
import de.tum.in.www1.artemis.web.rest.dto.DueDateStat;
import de.tum.in.www1.artemis.web.rest.dto.StatsForDashboardDTO;
import de.tum.in.www1.artemis.web.rest.dto.TutorLeaderboardDTO;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

/**
 * Service Implementation for managing Exercise.
 */
@Service
public class ExerciseService {

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final Logger log = LoggerFactory.getLogger(ExerciseService.class);

    private final ParticipationService participationService;

    private final AuthorizationCheckService authCheckService;

    private final ProgrammingExerciseService programmingExerciseService;

    private final ModelingExerciseService modelingExerciseService;

    private final QuizExerciseService quizExerciseService;

    private final QuizScheduleService quizScheduleService;

    private final ExampleSubmissionService exampleSubmissionService;

    private final ProgrammingAssessmentService programmingAssessmentService;

    private final TeamRepository teamRepository;

    private final ExamRepository examRepository;

    private final StudentExamRepository studentExamRepository;

    private final AuditEventRepository auditEventRepository;

    private final ExerciseUnitRepository exerciseUnitRepository;

    private final ExerciseRepository exerciseRepository;

    private final TutorParticipationRepository tutorParticipationRepository;

    private final ParticipantScoreRepository participantScoreRepository;

    private final SubmissionRepository submissionRepository;

    private final ResultRepository resultRepository;

    private final LtiOutcomeUrlRepository ltiOutcomeUrlRepository;

    private final StudentParticipationRepository studentParticipationRepository;

    private final LectureUnitService lectureUnitService;

    private final UserRepository userRepository;

    private final ComplaintRepository complaintRepository;

    private final TutorLeaderboardService tutorLeaderboardService;

    private final ComplaintResponseRepository complaintResponseRepository;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final GradingCriterionRepository gradingCriterionRepository;

    private final FeedbackRepository feedbackRepository;

    private final PostRepository postRepository;

    private final AnswerPostRepository answerPostRepository;

    private final PlagiarismResultRepository plagiarismResultRepository;

    public ExerciseService(ExerciseRepository exerciseRepository, ExerciseUnitRepository exerciseUnitRepository, ParticipationService participationService,
            AuthorizationCheckService authCheckService, ProgrammingExerciseService programmingExerciseService, ModelingExerciseService modelingExerciseService,
            QuizExerciseService quizExerciseService, QuizScheduleService quizScheduleService, TutorParticipationRepository tutorParticipationRepository,
            ExampleSubmissionService exampleSubmissionService, AuditEventRepository auditEventRepository, TeamRepository teamRepository,
            StudentExamRepository studentExamRepository, ExamRepository examRepository, ProgrammingExerciseRepository programmingExerciseRepository,
            LtiOutcomeUrlRepository ltiOutcomeUrlRepository, StudentParticipationRepository studentParticipationRepository, ResultRepository resultRepository,
            SubmissionRepository submissionRepository, ParticipantScoreRepository participantScoreRepository, LectureUnitService lectureUnitService, UserRepository userRepository,
            ComplaintRepository complaintRepository, TutorLeaderboardService tutorLeaderboardService, ComplaintResponseRepository complaintResponseRepository,
            PlagiarismResultRepository plagiarismResultRepository, GradingCriterionRepository gradingCriterionRepository, FeedbackRepository feedbackRepository,
            ProgrammingAssessmentService programmingAssessmentService, PostRepository postRepository,AnswerPostRepository answerPostRepository) {
        this.exerciseRepository = exerciseRepository;
        this.resultRepository = resultRepository;
        this.examRepository = examRepository;
        this.participationService = participationService;
        this.authCheckService = authCheckService;
        this.programmingExerciseService = programmingExerciseService;
        this.modelingExerciseService = modelingExerciseService;
        this.tutorParticipationRepository = tutorParticipationRepository;
        this.exampleSubmissionService = exampleSubmissionService;
        this.auditEventRepository = auditEventRepository;
        this.quizExerciseService = quizExerciseService;
        this.quizScheduleService = quizScheduleService;
        this.studentExamRepository = studentExamRepository;
        this.exerciseUnitRepository = exerciseUnitRepository;
        this.submissionRepository = submissionRepository;
        this.teamRepository = teamRepository;
        this.participantScoreRepository = participantScoreRepository;
        this.ltiOutcomeUrlRepository = ltiOutcomeUrlRepository;
        this.studentParticipationRepository = studentParticipationRepository;
        this.lectureUnitService = lectureUnitService;
        this.userRepository = userRepository;
        this.complaintRepository = complaintRepository;
        this.tutorLeaderboardService = tutorLeaderboardService;
        this.complaintResponseRepository = complaintResponseRepository;
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.postRepository = postRepository;
        this.answerPostRepository = answerPostRepository;
        this.gradingCriterionRepository = gradingCriterionRepository;
        this.feedbackRepository = feedbackRepository;
        this.programmingAssessmentService = programmingAssessmentService;
        this.plagiarismResultRepository = plagiarismResultRepository;
    }

    /**
     * Gets the subset of given exercises that a user is allowed to see
     *
     * @param exercises exercises to filter
     * @param user      user
     * @return subset of the exercises that a user allowed to see
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
                    // students in online courses can only see exercises where the lti outcome url exists, otherwise the result cannot be reported later on
                    Optional<LtiOutcomeUrl> ltiOutcomeUrlOptional = ltiOutcomeUrlRepository.findByUserAndExercise(user, exercise);
                    if (ltiOutcomeUrlOptional.isPresent()) {
                        exercisesUserIsAllowedToSee.add(exercise);
                    }
                }
            }
            else {
                // disclaimer: untested syntax, something along those lines should do the job however
                exercisesUserIsAllowedToSee.addAll(exercises.stream().filter(Exercise::isVisibleToStudents).collect(Collectors.toSet()));
            }
        }
        return exercisesUserIsAllowedToSee;
    }

    /**
     * Given an exercise exerciseId, it creates an object node with numberOfSubmissions, totalNumberOfAssessments, numberOfComplaints and numberOfMoreFeedbackRequests, that are used by both
     * stats for assessment dashboard and for instructor dashboard
     * TODO: refactor and improve this method
     *
     * @param exercise - the exercise we are interested in
     * @param examMode - flag to determine if test run submissions should be deducted from the statistics
     * @return a object node with the stats
     */
    public StatsForDashboardDTO populateCommonStatistics(Exercise exercise, boolean examMode) {
        final Long exerciseId = exercise.getId();
        StatsForDashboardDTO stats = new StatsForDashboardDTO();

        Course course = exercise.getCourseViaExerciseGroupOrCourseMember();

        DueDateStat numberOfSubmissions;
        DueDateStat totalNumberOfAssessments;

        if (exercise instanceof ProgrammingExercise) {
            numberOfSubmissions = new DueDateStat(programmingExerciseRepository.countLegalSubmissionsByExerciseIdSubmitted(exerciseId, examMode), 0L);
            totalNumberOfAssessments = new DueDateStat(programmingExerciseRepository.countAssessmentsByExerciseIdSubmitted(exerciseId, examMode), 0L);
        }
        else {
            numberOfSubmissions = submissionRepository.countSubmissionsForExercise(exerciseId, examMode);
            totalNumberOfAssessments = resultRepository.countNumberOfFinishedAssessmentsForExercise(exerciseId, examMode);
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
        Set<Exercise> exercises = exerciseRepository.findByExerciseIdWithCategories(exerciseIds);
        // Set is needed here to remove duplicates
        Set<Course> courses = exercises.stream().map(Exercise::getCourseViaExerciseGroupOrCourseMember).collect(Collectors.toSet());
        if (courses.size() != 1) {
            throw new IllegalArgumentException("All exercises must be from the same course!");
        }
        Course course = courses.stream().findFirst().get();
        List<StudentParticipation> participationsOfUserInExercises = studentParticipationRepository.getAllParticipationsOfUserInExercises(user, exercises);
        boolean isStudent = !authCheckService.isAtLeastTeachingAssistantInCourse(course, user);
        for (Exercise exercise : exercises) {
            // add participation with submission and result to each exercise
            filterForCourseDashboard(exercise, participationsOfUserInExercises, user.getLogin(), isStudent);
            // remove sensitive information from the exercise for students
            if (isStudent) {
                exercise.filterSensitiveInformation();
            }
            setAssignedTeamIdForExerciseAndUser(exercise, user);
        }
        return exercises;
    }

    /**
     * Finds all Exercises for a given Course
     *
     * @param course corresponding course
     * @param user   the user entity
     * @return a List of all Exercises for the given course
     */
    public Set<Exercise> findAllForCourse(Course course, User user) {
        Set<Exercise> exercises = null;
        if (authCheckService.isAtLeastTeachingAssistantInCourse(course, user)) {
            // tutors/instructors/admins can see all exercises of the course
            exercises = exerciseRepository.findByCourseIdWithCategories(course.getId());
        }
        else if (authCheckService.isOnlyStudentInCourse(course, user)) {

            if (course.isOnlineCourse()) {
                // students in online courses can only see exercises where the lti outcome url exists, otherwise the result cannot be reported later on
                exercises = exerciseRepository.findByCourseIdWhereLtiOutcomeUrlExists(course.getId(), user.getLogin());
            }
            else {
                exercises = exerciseRepository.findByCourseIdWithCategories(course.getId());
            }

            // students for this course might not have the right to see it so we have to
            // filter out exercises that are not released (or explicitly made visible to students) yet
            exercises = exercises.stream().filter(Exercise::isVisibleToStudents).collect(Collectors.toSet());
        }

        if (exercises != null) {
            for (Exercise exercise : exercises) {
                setAssignedTeamIdForExerciseAndUser(exercise, user);

                // filter out questions and all statistical information about the quizPointStatistic from quizExercises (so users can't see which answer options are correct)
                if (exercise instanceof QuizExercise quizExercise) {
                    quizExercise.filterSensitiveInformation();
                }
            }
        }

        return exercises;
    }

    /**
     * Checks if the exercise has any test runs and sets the transient property if it does
     * @param exercise - the exercise for which we check if test runs exist
     */
    public void checkTestRunsExist(Exercise exercise) {
        Long containsTestRunParticipations = studentParticipationRepository.countParticipationsOnlyTestRunsByExerciseId(exercise.getId());
        if (containsTestRunParticipations != null && containsTestRunParticipations > 0) {
            exercise.setTestRunParticipationsExist(Boolean.TRUE);
        }
    }

    /**
     * Resets an Exercise by deleting all its participations, plagiarism results
     * and anonymizing its Postings
     * @param exercise which should be reset
     */
    public void reset(Exercise exercise) {
        log.debug("Request reset Exercise : {}", exercise.getId());

        // delete all plagiarism results for this exercise
        plagiarismResultRepository.deletePlagiarismResultsByExerciseId(exercise.getId());

        // delete all participations for this exercise
        participationService.deleteAllByExerciseId(exercise.getId(), true, true);

        // anonymize Postings
        exercise = exerciseRepository.findByIdWithDetailsForStudent(exercise.getId()).orElseThrow();
        Optional<User> anonymousUser = userRepository.findOneByLogin("anonymous");
        if (anonymousUser.isPresent()) {
            for (Post post : exercise.getPosts()) {
                post.setAuthor(anonymousUser.get());

                for (AnswerPost answerPost : post.getAnswers()) {
                    answerPost.setAuthor(anonymousUser.get());
                    answerPostRepository.save(answerPost);
                }
                postRepository.save(post);
            }
        }
        else {
            log.warn("Anonymization could not be completed. Anonymous user missing.");
        }
        if (exercise instanceof QuizExercise) {
            quizExerciseService.resetExercise(exercise.getId());
        }
    }

    /**
     * Delete the exercise by id and all its participations.
     *
     * @param exerciseId                   the exercise to be deleted
     * @param deleteStudentReposBuildPlans whether the student repos and build plans should be deleted (can be true for programming exercises and should be false for all other exercise types)
     * @param deleteBaseReposBuildPlans    whether the template and solution repos and build plans should be deleted (can be true for programming exercises and should be false for all other exercise types)
     */
    @Transactional // ok
    public void delete(long exerciseId, boolean deleteStudentReposBuildPlans, boolean deleteBaseReposBuildPlans) {
        // Delete has a transactional mechanism. Therefore, all lazy objects that are deleted below, should be fetched when needed.
        final var exercise = exerciseRepository.findByIdElseThrow(exerciseId);

        log.info("Checking if exercise is modeling exercise", exercise.getId());
        if (exercise instanceof ModelingExercise) {
            log.info("Deleting clusters and elements", exercise.getId());

            modelingExerciseService.deleteClustersAndElements((ModelingExercise) exercise);
        }

        participantScoreRepository.deleteAllByExerciseIdTransactional(exerciseId);
        // delete all exercise units linking to the exercise
        List<ExerciseUnit> exerciseUnits = this.exerciseUnitRepository.findByIdWithLearningGoalsBidirectional(exerciseId);
        for (ExerciseUnit exerciseUnit : exerciseUnits) {
            this.lectureUnitService.removeLectureUnit(exerciseUnit);
        }

        // delete all plagiarism results belonging to this exercise
        plagiarismResultRepository.deletePlagiarismResultsByExerciseId(exerciseId);

        // delete all participations belonging to this quiz
        participationService.deleteAllByExerciseId(exercise.getId(), deleteStudentReposBuildPlans, deleteStudentReposBuildPlans);
        // clean up the many to many relationship to avoid problems when deleting the entities but not the relationship table
        // to avoid a ConcurrentModificationException, we need to use a copy of the set
        var exampleSubmissions = new HashSet<>(exercise.getExampleSubmissions());
        for (ExampleSubmission exampleSubmission : exampleSubmissions) {
            exampleSubmissionService.deleteById(exampleSubmission.getId());
        }
        // make sure tutor participations are deleted before the exercise is deleted
        tutorParticipationRepository.deleteAllByAssessedExerciseId(exercise.getId());

        if (exercise.isExamExercise()) {
            Exam exam = examRepository.findOneWithEagerExercisesGroupsAndStudentExams(exercise.getExerciseGroup().getExam().getId());
            for (StudentExam studentExam : exam.getStudentExams()) {
                if (studentExam.getExercises().contains(exercise)) {
                    // remove exercise reference from student exam
                    List<Exercise> exerciseList = studentExam.getExercises();
                    exerciseList.remove(exercise);
                    studentExam.setExercises(exerciseList);
                    studentExamRepository.save(studentExam);
                }
            }
        }

        // Programming exercises have some special stuff that needs to be cleaned up (solution/template participation, build plans, etc.).
        if (exercise instanceof ProgrammingExercise) {
            // TODO: delete all schedules related to this programming exercise
            programmingExerciseService.delete(exercise.getId(), deleteBaseReposBuildPlans);
        }
        else {
            exerciseRepository.delete(exercise);
        }
    }

    /**
     * Updates the points of related exercises if the points of exercises have changed
     *
     * @param originalExercise the original exercise
     * @param updatedExercise  the updatedExercise
     */
    public void updatePointsInRelatedParticipantScores(Exercise originalExercise, Exercise updatedExercise) {
        if (originalExercise.getMaxPoints().equals(updatedExercise.getMaxPoints()) && originalExercise.getBonusPoints().equals(updatedExercise.getBonusPoints())) {
            return; // nothing to do since points are still correct
        }

        List<ParticipantScore> participantScoreList = participantScoreRepository.findAllByExercise(updatedExercise);
        for (ParticipantScore participantScore : participantScoreList) {
            Double lastPoints = null;
            Double lastRatedPoints = null;
            if (participantScore.getLastScore() != null) {
                lastPoints = round(participantScore.getLastScore() * 0.01 * updatedExercise.getMaxPoints());
            }
            if (participantScore.getLastRatedScore() != null) {
                lastRatedPoints = round(participantScore.getLastRatedScore() * 0.01 * updatedExercise.getMaxPoints());
            }
            participantScore.setLastPoints(lastPoints);
            participantScore.setLastRatedPoints(lastRatedPoints);
        }
        participantScoreRepository.saveAll(participantScoreList);
    }

    /**
     * Delete student build plans (except BASE/SOLUTION) and optionally git repositories of all exercise student participations.
     *
     * @param exerciseId         programming exercise for which build plans in respective student participations are deleted
     * @param deleteRepositories if true, the repositories gets deleted
     */
    public void cleanup(Long exerciseId, boolean deleteRepositories) {
        Exercise exercise = exerciseRepository.findByIdWithStudentParticipationsElseThrow(exerciseId);
        log.info("Request to cleanup all participations for Exercise : {}", exercise.getTitle());

        if (exercise instanceof ProgrammingExercise) {
            for (StudentParticipation participation : exercise.getStudentParticipations()) {
                participationService.cleanupBuildPlan((ProgrammingExerciseStudentParticipation) participation);
            }

            if (!deleteRepositories) {
                return;    // in this case, we are done
            }

            for (StudentParticipation participation : exercise.getStudentParticipations()) {
                participationService.cleanupRepository((ProgrammingExerciseStudentParticipation) participation);
            }

        }
        else {
            log.warn("Exercise with exerciseId {} is not an instance of ProgrammingExercise. Ignoring the request to cleanup repositories and build plan", exerciseId);
        }
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
     * @param username       used to get quiz submission for the user
     * @param isStudent      defines if the current user is a student
     */
    public void filterForCourseDashboard(Exercise exercise, List<StudentParticipation> participations, String username, boolean isStudent) {
        // remove the unnecessary inner course attribute
        exercise.setCourse(null);

        // remove the problem statement, which is loaded in the exercise details call
        exercise.setProblemStatement(null);

        if (exercise instanceof ProgrammingExercise programmingExercise) {
            programmingExercise.setTestRepositoryUrl(null);
        }

        // get user's participation for the exercise
        StudentParticipation participation = participations != null ? exercise.findRelevantParticipation(participations) : null;

        // for quiz exercises also check SubmissionHashMap for submission by this user (active participation)
        // if participation was not found in database
        if (participation == null && exercise instanceof QuizExercise) {
            QuizSubmission submission = quizScheduleService.getQuizSubmission(exercise.getId(), username);
            if (submission.getSubmissionDate() != null) {
                participation = new StudentParticipation().exercise(exercise);
                participation.initializationState(InitializationState.INITIALIZED);
            }
        }

        // add relevant submission (relevancy depends on InitializationState) with its result to participation
        if (participation != null) {
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

            // remove inner exercise from participation
            participation.setExercise(null);

            // add participation into an array
            exercise.setStudentParticipations(Set.of(participation));
        }
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
     * @param courseId the id of the course
     * @param amountOfStudentsInCourse the amount of students in the course
     * @return A list of filled <code>CourseManagementOverviewExerciseStatisticsDTO</code>
     */
    public List<CourseManagementOverviewExerciseStatisticsDTO> getStatisticsForCourseManagementOverview(Long courseId, Integer amountOfStudentsInCourse) {
        // We only display the latest five past exercises in the client, only calculate statistics for those
        var pastExercises = exerciseRepository.getPastExercisesForCourseManagementOverview(courseId, ZonedDateTime.now());
        pastExercises.sort((exerciseA, exerciseB) -> {
            var dueDateA = exerciseA.getAssessmentDueDate() != null ? exerciseA.getAssessmentDueDate() : exerciseA.getDueDate();
            var dueDateB = exerciseB.getAssessmentDueDate() != null ? exerciseB.getAssessmentDueDate() : exerciseB.getDueDate();
            if (dueDateA.equals(dueDateB)) {
                return 0;
            }

            return dueDateA.isBefore(dueDateB) ? 1 : -1;
        });
        var fivePastExercises = pastExercises.stream().limit(5).collect(Collectors.toList());

        // Calculate the average score for all five exercises at once
        var averageScore = participantScoreRepository.findAverageScoreForExercises(fivePastExercises);
        Map<Long, Double> averageScoreById = new HashMap<>();
        for (var element : averageScore) {
            averageScoreById.put((Long) element.get("exerciseId"), (Double) element.get("averageScore"));
        }

        // Fill statistics for all exercises potentially displayed on the client
        var exercisesForManagementOverview = exerciseRepository.getActiveExercisesForCourseManagementOverview(courseId, ZonedDateTime.now());
        exercisesForManagementOverview.addAll(fivePastExercises);
        return generateCourseManagementDTOs(exercisesForManagementOverview, amountOfStudentsInCourse, averageScoreById);
    }

    /**
     * Generates a <code>CourseManagementOverviewExerciseStatisticsDTO</code> for each given exercise
     *
     * @param exercisesForManagementOverview a set of exercises to generate the statistics for
     * @param amountOfStudentsInCourse the amount of students in the course
     * @param averageScoreById the average score for each exercise indexed by exerciseId
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

            setAverageScoreForStatisticsDTO(exerciseStatisticsDTO, averageScoreById, exercise);
            setStudentsAndParticipationsAmountForStatisticsDTO(exerciseStatisticsDTO, amountOfStudentsInCourse, exercise);
            setAssessmentsAndSubmissionsForStatisticsDTO(exerciseStatisticsDTO, exercise);

            statisticsDTOS.add(exerciseStatisticsDTO);
        }
        return statisticsDTOS;
    }

    /**
     * Sets the average for the given <code>CourseManagementOverviewExerciseStatisticsDTO</code>
     * using the value provided in averageScoreById
     *
     * Quiz Exercises are a special case: They don't have a due date set in the database,
     * therefore it is hard to tell if they are over, so always calculate a score for them
     *
     * @param exerciseStatisticsDTO the <code>CourseManagementOverviewExerciseStatisticsDTO</code> to set the amounts for
     * @param averageScoreById the average score for each exercise indexed by exerciseId
     * @param exercise the exercise corresponding to the <code>CourseManagementOverviewExerciseStatisticsDTO</code>
     */
    private void setAverageScoreForStatisticsDTO(CourseManagementOverviewExerciseStatisticsDTO exerciseStatisticsDTO, Map<Long, Double> averageScoreById, Exercise exercise) {
        if (exercise instanceof QuizExercise) {
            var averageScore = participantScoreRepository.findAverageScoreForExercise(exercise.getId());
            exerciseStatisticsDTO.setAverageScoreInPercent(averageScore != null ? averageScore : 0.0);
        }
        else {
            var averageScore = averageScoreById.get(exercise.getId());
            exerciseStatisticsDTO.setAverageScoreInPercent(averageScore != null ? averageScore : 0.0);
        }
    }

    /**
     * Sets the amount of students, participations and teams for the given <code>CourseManagementOverviewExerciseStatisticsDTO</code>
     * Only the amount of students in the course is set if the exercise has ended, the rest is set to zero
     *
     * @param exerciseStatisticsDTO the <code>CourseManagementOverviewExerciseStatisticsDTO</code> to set the amounts for
     * @param amountOfStudentsInCourse the amount of students in the course
     * @param exercise the exercise corresponding to the <code>CourseManagementOverviewExerciseStatisticsDTO</code>
     */
    private void setStudentsAndParticipationsAmountForStatisticsDTO(CourseManagementOverviewExerciseStatisticsDTO exerciseStatisticsDTO, Integer amountOfStudentsInCourse,
            Exercise exercise) {
        exerciseStatisticsDTO.setNoOfStudentsInCourse(amountOfStudentsInCourse);

        if (amountOfStudentsInCourse != null && amountOfStudentsInCourse != 0 && !exercise.isEnded()) {
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
     * @param exercise the exercise corresponding to the <code>CourseManagementOverviewExerciseStatisticsDTO</code>
     */
    private void setAssessmentsAndSubmissionsForStatisticsDTO(CourseManagementOverviewExerciseStatisticsDTO exerciseStatisticsDTO, Exercise exercise) {
        if (exercise.getAssessmentDueDate() != null && exercise.getAssessmentDueDate().isAfter(ZonedDateTime.now())) {
            long numberOfRatedAssessments = resultRepository.countNumberOfRatedResultsForExercise(exercise.getId());
            long noOfSubmissionsInTime = submissionRepository.countUniqueSubmissionsByExerciseId(exercise.getId());
            exerciseStatisticsDTO.setNoOfRatedAssessments(numberOfRatedAssessments);
            exerciseStatisticsDTO.setNoOfSubmissionsInTime(noOfSubmissionsInTime);
            exerciseStatisticsDTO.setNoOfAssessmentsDoneInPercent(noOfSubmissionsInTime == 0 ? 0 : Math.round(numberOfRatedAssessments * 1000.0 / noOfSubmissionsInTime) / 10.0);
        }
        else {
            exerciseStatisticsDTO.setNoOfRatedAssessments(0L);
            exerciseStatisticsDTO.setNoOfSubmissionsInTime(0L);
            exerciseStatisticsDTO.setNoOfAssessmentsDoneInPercent(0D);
        }
    }

    public void validateGeneralSettings(Exercise exercise) {
        validateScoreSettings(exercise);
        exercise.validateDates();
    }

    /**
     * Validates score settings
     * 1. The maxScore needs to be greater than 0
     * 2. If the specified amount of bonus points is valid depending on the IncludedInOverallScore value
     *
     * @param exercise exercise to validate
     */
    public void validateScoreSettings(Exercise exercise) {
        // Check if max score is set
        if (exercise.getMaxPoints() == null || exercise.getMaxPoints() <= 0) {
            throw new BadRequestAlertException("The max score needs to be greater than 0", "Exercise", "maxScoreInvalid");
        }

        if (exercise.getBonusPoints() == null) {
            // make sure the default value is set properly
            exercise.setBonusPoints(0.0);
        }

        // Check IncludedInOverallScore
        if (exercise.getIncludedInOverallScore() == null) {
            throw new BadRequestAlertException("The IncludedInOverallScore-property must be set", "Exercise", "includedInOverallScoreNotSet");
        }

        if (!exercise.getIncludedInOverallScore().validateBonusPoints(exercise.getBonusPoints())) {
            throw new BadRequestAlertException("The provided bonus points are not allowed", "Exercise", "bonusPointsInvalid");
        }
    }

    /**
     * Checks the exercise structured grading instructions if any of them is associated with the feedback
     * then, sets the corresponding exercise field
     *
     * @param gradingCriteria grading criteria list of exercise
     * @param exercise exercise to update     *
     */
    public void checkExerciseIfStructuredGradingInstructionFeedbackUsed(List<GradingCriterion> gradingCriteria, Exercise exercise) {
        List<Feedback> feedback = feedbackRepository.findFeedbackByExerciseGradingCriteria(gradingCriteria);

        if (!feedback.isEmpty()) {
            exercise.setGradingInstructionFeedbackUsed(true);
        }
    }

    /**
     * Re-evaluates the exercise before saving
     * 1. The feedback associated with the exercise grading instruction needs to be updated
     * 2. After updating feedback, result needs to be re-calculated
     *
     * @param exercise exercise to re-evaluate
     * @param deleteFeedbackAfterGradingInstructionUpdate  boolean flag that indicates whether the associated feedback should be deleted or not     *
     */
    public void reEvaluateExercise(Exercise exercise, boolean deleteFeedbackAfterGradingInstructionUpdate) {

        List<GradingCriterion> gradingCriteria = exercise.getGradingCriteria();
        // retrieve the feedback associated with the structured grading instructions
        List<Feedback> feedbackToBeUpdated = feedbackRepository.findFeedbackByExerciseGradingCriteria(gradingCriteria);

        // collect all structured grading instructions into the list
        List<GradingInstruction> gradingInstructions = gradingCriteria.stream().flatMap(gradingCriterion -> gradingCriterion.getStructuredGradingInstructions().stream()).toList();

        // update the related fields for feedback
        for (GradingInstruction instruction : gradingInstructions) {
            for (Feedback feedback : feedbackToBeUpdated) {
                if (feedback.getGradingInstruction().getId().equals(instruction.getId())) {
                    feedback.setCredits(instruction.getCredits());
                    feedback.setPositive(feedback.getCredits() >= 0);
                    feedback.setDetailText(instruction.getFeedback());
                }
            }
        }
        feedbackRepository.saveAll(feedbackToBeUpdated);

        List<Feedback> feedbackToBeDeleted = getFeedbackToBeDeletedAfterGradingInstructionUpdate(deleteFeedbackAfterGradingInstructionUpdate, gradingInstructions, exercise);

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

            if (!(exercise instanceof ProgrammingExercise)) {
                resultRepository.submitResult(result, exercise);
            }
            else {
                double totalScore = programmingAssessmentService.calculateTotalScore(result);
                result.setScore(totalScore, exercise.getMaxPoints());
                /*
                 * Result string has following structure e.g: "1 of 13 passed, 2 issues, 10 of 100 points" The last part of the result string has to be updated, as the points the
                 * student has achieved have changed
                 */
                String[] resultStringParts = result.getResultString().split(", ");
                resultStringParts[resultStringParts.length - 1] = result.createResultString(totalScore, exercise.getMaxPoints());
                result.setResultString(String.join(", ", resultStringParts));
                resultRepository.save(result);
            }
        }
    }

    /**
     * Gets the list of feedback that is associated with deleted grading instructions
     *
     * @param deleteFeedbackAfterGradingInstructionUpdate  boolean flag that indicates whether the associated feedback should be deleted or not
     * @param gradingInstructions grading instruction list to update
     * @param exercise exercise for which the grading instructions have to be updated
     * @return list including Feedback entries that have to be deleted due to updated grading instructions
     */
    public List<Feedback> getFeedbackToBeDeletedAfterGradingInstructionUpdate(boolean deleteFeedbackAfterGradingInstructionUpdate, List<GradingInstruction> gradingInstructions,
            Exercise exercise) {
        List<Feedback> feedbackToBeDeleted = new ArrayList<>();
        // check if the user decided to remove the feedback after deleting the associated grading instructions
        if (deleteFeedbackAfterGradingInstructionUpdate) {
            List<Long> updatedInstructionIds = gradingInstructions.stream().map(GradingInstruction::getId).toList();
            // retrieve the grading instructions from database for backup
            List<GradingCriterion> backupGradingCriteria = gradingCriterionRepository.findByExerciseIdWithEagerGradingCriteria(exercise.getId());
            List<Long> backupInstructionIds = backupGradingCriteria.stream().flatMap(gradingCriterion -> gradingCriterion.getStructuredGradingInstructions().stream())
                    .map(GradingInstruction::getId).toList();

            // collect deleted grading instruction ids into the list
            List<Long> gradingInstructionIdsToBeDeleted = backupInstructionIds.stream().filter(backupinstructionId -> !updatedInstructionIds.contains(backupinstructionId))
                    .toList();

            // determine the feedback to be deleted
            feedbackToBeDeleted = feedbackRepository.findFeedbackByGradingInstructionIds(gradingInstructionIdsToBeDeleted);
        }
        return feedbackToBeDeleted;
    }
}
