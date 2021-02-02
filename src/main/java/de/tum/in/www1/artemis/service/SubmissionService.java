package de.tum.in.www1.artemis.service;

import static de.tum.in.www1.artemis.config.Constants.MAX_NUMBER_OF_LOCKED_SUBMISSIONS_PER_TUTOR;
import static de.tum.in.www1.artemis.web.rest.util.ResponseUtil.forbidden;
import static java.util.stream.Collectors.toList;

import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.AssessmentType;
import de.tum.in.www1.artemis.domain.enumeration.FeedbackType;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.repository.FeedbackRepository;
import de.tum.in.www1.artemis.repository.ResultRepository;
import de.tum.in.www1.artemis.repository.StudentParticipationRepository;
import de.tum.in.www1.artemis.repository.SubmissionRepository;
import de.tum.in.www1.artemis.web.rest.dto.DueDateStat;
import de.tum.in.www1.artemis.web.rest.errors.AccessForbiddenException;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

@Service
public class SubmissionService {

    private final Logger log = LoggerFactory.getLogger(SubmissionService.class);

    private ExamService examService;

    private CourseService courseService;

    protected final SubmissionRepository submissionRepository;

    protected final ResultRepository resultRepository;

    protected final AuthorizationCheckService authCheckService;

    protected final StudentParticipationRepository studentParticipationRepository;

    protected final ParticipationService participationService;

    protected final UserService userService;

    protected final FeedbackRepository feedbackRepository;

    public SubmissionService(SubmissionRepository submissionRepository, UserService userService, AuthorizationCheckService authCheckService, ResultRepository resultRepository,
            StudentParticipationRepository studentParticipationRepository, ParticipationService participationService, FeedbackRepository feedbackRepository) {
        this.submissionRepository = submissionRepository;
        this.userService = userService;
        this.authCheckService = authCheckService;
        this.resultRepository = resultRepository;
        this.studentParticipationRepository = studentParticipationRepository;
        this.participationService = participationService;
        this.feedbackRepository = feedbackRepository;
    }

    @Autowired
    // break the dependency cycle
    public void setExamService(ExamService examService) {
        this.examService = examService;
    }

    @Autowired
    // break the dependency cycle
    public void setCourseService(CourseService courseService) {
        this.courseService = courseService;
    }

    /**
     * Check that the user is allowed to make the submission
     *
     * @param exercise      the exercise for which a submission should be saved
     * @param submission    the submission that should be saved
     * @param currentUser   the current user with groups and authorities
     * @param <T>           The type of the return type of the requesting route so that the
     *                      response can be returned there
     * @return an Optional with a typed ResponseEntity. If it is empty all checks passed
     */
    public <T> Optional<ResponseEntity<T>> checkSubmissionAllowance(Exercise exercise, Submission submission, User currentUser) {
        // Fetch course from database to make sure client didn't change groups
        final Course course = courseService.findOne(exercise.getCourseViaExerciseGroupOrCourseMember().getId());
        if (!authCheckService.isAtLeastStudentInCourse(course, currentUser)) {
            return Optional.of(forbidden());
        }

        // Fetch the submission with the corresponding participation if the id is set (on update) and check that the
        // user of the participation is the same as the user who executes this call (or student in the team).
        // This prevents injecting submissions to other users.
        if (submission.getId() != null) {
            Optional<Submission> existingSubmission = submissionRepository.findById(submission.getId());
            if (existingSubmission.isEmpty()) {
                return Optional.of(forbidden());
            }

            StudentParticipation participation = (StudentParticipation) existingSubmission.get().getParticipation();
            if (participation != null) {
                Optional<User> user = participation.getStudent();
                if (user.isPresent() && !user.get().equals(currentUser)) {
                    return Optional.of(forbidden());
                }

                Optional<Team> team = participation.getTeam();
                if (team.isPresent() && !authCheckService.isStudentInTeam(course, team.get().getShortName(), currentUser)) {
                    return Optional.of(forbidden());
                }
            }
        }

        return Optional.empty();
    }

    /**
     * Check if the limit of simultaneously locked submissions (i.e. unfinished assessments) has been reached for the current user in the given course. Throws a
     * BadRequestAlertException if the limit has been reached.
     *
     * @param courseId the id of the course
     */
    public void checkSubmissionLockLimit(long courseId) {
        long numberOfLockedSubmissions = submissionRepository.countLockedSubmissionsByUserIdAndCourseId(userService.getUserWithGroupsAndAuthorities().getId(), courseId);
        if (numberOfLockedSubmissions >= MAX_NUMBER_OF_LOCKED_SUBMISSIONS_PER_TUTOR) {
            throw new BadRequestAlertException("The limit of locked submissions has been reached", "submission", "lockedSubmissionsLimitReached");
        }
    }

    /**
     * Get the number of simultaneously locked submissions (i.e. unfinished assessments) for the current user in the given course.
     *
     * @param courseId the id of the course
     * @return number of locked submissions for the current user in the given course
     */
    public long countSubmissionLocks(long courseId) {
        return submissionRepository.countLockedSubmissionsByUserIdAndCourseId(userService.getUserWithGroupsAndAuthorities().getId(), courseId);
    }

    /**
     * Get simultaneously locked submissions (i.e. unfinished assessments) for the current user in the given course.
     *
     * @param courseId the id of the course
     * @return number of locked submissions for the current user in the given course
     */
    public List<Submission> getLockedSubmissions(long courseId) {
        return submissionRepository.getLockedSubmissionsAndResultsByUserIdAndCourseId(userService.getUserWithGroupsAndAuthorities().getId(), courseId);
    }

    /**
     * Given an exercise id and a tutor id, it returns all the submissions where the tutor has a result associated.
     *
     * @param exerciseId - the id of the exercise we are looking for
     * @param correctionRound - the correction round we want our submission to have results for
     * @param tutor - the tutor we are interested in
     * @param examMode - flag should be set to ignore the test run submissions
     * @param <T> the submission type
     * @return a list of Submissions
     */
    public <T extends Submission> List<T> getAllSubmissionsAssessedByTutorForCorrectionRoundAndExercise(Long exerciseId, User tutor, boolean examMode, int correctionRound) {
        List<T> submissions;
        if (examMode) {
            var participations = this.studentParticipationRepository.findAllByParticipationExerciseIdAndResultAssessorAndCorrectionRoundIgnoreTestRuns(exerciseId, tutor);
            submissions = participations.stream().map(StudentParticipation::findLatestSubmission).filter(Optional::isPresent).map(Optional::get).map(submission -> (T) submission)
                    .filter(submission -> submission.getResults().size() - 1 >= correctionRound && submission.getResults().get(correctionRound) != null).collect(toList());
        }
        else {
            submissions = this.submissionRepository.findAllByParticipationExerciseIdAndResultAssessor(exerciseId, tutor);
        }

        submissions.forEach(submission -> submission.getLatestResult().setSubmission(null));
        return submissions;
    }

    /**
     * Given an exercise id, find a random submission for that exercise which still doesn't have any manual result.
     * No manual result means that no user has started an assessment for the corresponding submission yet.
     * For exam exercises we should also remove the test run participations as these should not be graded by the tutors.
     * If @param correctionRound is bigger than 0, only submission are shown for which the user has not assessed the first result.
     *
     * @param exercise the exercise for which we want to retrieve a submission without manual result
     * @param correctionRound - the correction round we want our submission to have results for
     * @param examMode flag to determine if test runs should be removed. This should be set to true for exam exercises
     * @return a submission without any manual result or an empty Optional if no submission without manual result could be found
     */
    public Optional<Submission> getRandomSubmissionEligibleForNewAssessment(Exercise exercise, boolean examMode, int correctionRound) {
        Random random = new Random();
        List<StudentParticipation> participations;

        if (examMode) {
            participations = participationService.findByExerciseIdWithLatestSubmissionWithoutManualResultsAndNoTestRun(exercise.getId(), correctionRound);
        }
        else {
            participations = participationService.findByExerciseIdWithLatestSubmissionWithoutManualResults(exercise.getId());
        }

        List<Submission> submissionsWithoutResult = participations.stream().map(StudentParticipation::findLatestSubmission).filter(Optional::isPresent).map(Optional::get)
                .collect(Collectors.toList());

        if (correctionRound > 0) {
            // remove submission if user already assessed first correction round
            // if disabled, please switch tutorAssessUnique within the tests
            submissionsWithoutResult = submissionsWithoutResult.stream()
                    .filter(submission -> !submission.getResultForCorrectionRound(correctionRound - 1).getAssessor().equals(userService.getUser())).collect(Collectors.toList());
        }

        if (submissionsWithoutResult.isEmpty()) {
            return Optional.empty();
        }

        submissionsWithoutResult = selectOnlySubmissionsBeforeDueDateOrAll(submissionsWithoutResult, exercise.getDueDate());

        var submissionWithoutResult = submissionsWithoutResult.get(random.nextInt(submissionsWithoutResult.size()));
        return Optional.of(submissionWithoutResult);
    }

    /**
     * Get the submission with the given id from the database. The submission is loaded together with its results and the assessors. Throws an EntityNotFoundException if no
     * submission could be found for the given id.
     *
     * @param submissionId the id of the submission that should be loaded from the database
     * @return the submission with the given id
     */
    public Submission findOneWithEagerResults(long submissionId) {
        return submissionRepository.findWithEagerResultsAndAssessorById(submissionId)
                .orElseThrow(() -> new EntityNotFoundException("Submission with id \"" + submissionId + "\" does not exist"));
    }

    /**
     * Get the submission with the given id from the database. The submission is loaded together with its result, the feedback of the result and the assessor of the
     * result. Throws an EntityNotFoundException if no submission could be found for the given id.
     *
     * @param submissionId the id of the submission that should be loaded from the database
     * @return the submission with the given id
     */
    public Submission findOneWithEagerResultAndFeedback(long submissionId) {
        return submissionRepository.findWithEagerResultAndFeedbackById(submissionId)
                .orElseThrow(() -> new EntityNotFoundException("Submission with id \"" + submissionId + "\" does not exist"));
    }

    /**
     * Count number of in-time submissions for course. Only submissions for Text, Modeling and File Upload exercises are included.
     * @param courseId the course id we are interested in
     * @return the number of submissions belonging to the course id, which have the submitted flag set to true and the submission date before the exercise due date, or no exercise
     *         due date at all
     */
    public long countInTimeSubmissionsForCourse(long courseId) {
        return submissionRepository.countByCourseIdSubmittedBeforeDueDate(courseId);
    }

    /**
     * Count number of late submissions for course. Only submissions for Text, Modeling and File Upload exercises are included.
     * @param courseId the course id we are interested in
     * @return the number of submissions belonging to the course id, which have the submitted flag set to true and the submission date after the exercise due date
     */
    public long countLateSubmissionsForCourse(long courseId) {
        return submissionRepository.countByCourseIdSubmittedAfterDueDate(courseId);
    }

    /**
     * Count number of submissions for exercise.
     * @param exerciseId the exercise id we are interested in
     * @param examMode should be set to ignore the test run submissions
     * @return the number of submissions belonging to the exercise id, which have the submitted flag set to true, separated into before and after the due date
     */
    public DueDateStat countSubmissionsForExercise(long exerciseId, boolean examMode) {
        if (examMode) {
            return new DueDateStat(submissionRepository.countByExerciseIdSubmittedBeforeDueDateIgnoreTestRuns(exerciseId), 0L);
        }
        return new DueDateStat(submissionRepository.countByExerciseIdSubmittedBeforeDueDate(exerciseId), submissionRepository.countByExerciseIdSubmittedAfterDueDate(exerciseId));
    }

    /**
     * Removes sensitive information (e.g. example solution of the exercise) from the submission based on the role of the current user. This should be called before sending a
     * submission to the client.
     * ***IMPORTANT***: Do not call this method from a transactional context as this would remove the sensitive information also from the entities in the
     * database without explicitly saving them.
     *
     * @param submission Submission to be modified.
     * @param user the currently logged in user which is used for hiding specific submission details based on instructor and teaching assistant rights
     */
    public void hideDetails(Submission submission, User user) {
        // do not send old submissions or old results to the client
        if (submission.getParticipation() != null) {
            submission.getParticipation().setSubmissions(null);
            submission.getParticipation().setResults(null);

            Exercise exercise = submission.getParticipation().getExercise();
            if (exercise != null) {
                // make sure that sensitive information is not sent to the client for students
                if (!authCheckService.isAtLeastTeachingAssistantForExercise(exercise, user)) {
                    exercise.filterSensitiveInformation();
                    submission.setResults(new ArrayList<Result>());
                }
                // remove information about the student or team from the submission for tutors to ensure a double-blind assessment
                if (!authCheckService.isAtLeastInstructorForExercise(exercise, user)) {
                    StudentParticipation studentParticipation = (StudentParticipation) submission.getParticipation();

                    // the student himself is allowed to see the participant (i.e. himself or his team) of his participation
                    if (!authCheckService.isOwnerOfParticipation(studentParticipation, user)) {
                        studentParticipation.filterSensitiveInformation();
                    }
                }
            }
        }
    }

    /**
     * Creates a new Result object, assigns it to the given submission and stores the changes to the database.
     * @param submission the submission for which a new result should be created
     * @return the newly created result
     */
    public Result saveNewEmptyResult(Submission submission) {
        Result result = new Result();
        result.setParticipation(submission.getParticipation());
        result = resultRepository.save(result);
        result.setSubmission(submission);
        submission.addResult(result);
        submissionRepository.save(submission);
        return result;
    }

    /**
     * Copy Feedbacks from one Result to a another Result
     * @param newResult new result to copy feedback to
     * @param oldResult old result to copy feedback from
     * @return the list of newly created feedbacks
     */
    public List<Feedback> copyFeedbackToNewResult(Result newResult, Result oldResult) {
        List<Feedback> oldFeedback = oldResult.getFeedbacks();
        oldFeedback.forEach(feedback -> {
            Feedback newFeedback = feedback.copyFeedback();
            newResult.addFeedback(newFeedback);
        });
        resultRepository.save(newResult);
        return newResult.getFeedbacks();
    }

    /**
     * This method is used to create a copy of a result, used in the exam mode with correctionRound > 1,
     * because an assessment with current correctionRound > 1 contains all previous work,
     * which the tutor can then edit. Assigns the newly created Result to the submission
     *
     * @param submission submission to which the new Result is assigned
     * @param oldResult result to copy from
     * @return the newly created copy of the oldResult
     */
    public Result copyResultFromPreviousRoundAndSave(Submission submission, Result oldResult) {
        if (oldResult == null) {
            return saveNewEmptyResult(submission);
        }
        Result newResult = new Result();
        newResult.setParticipation(submission.getParticipation());
        copyFeedbackToNewResult(newResult, oldResult);

        newResult.setResultString(oldResult.getResultString());
        newResult.setScore(oldResult.getScore());
        newResult.setHasFeedback(oldResult.getHasFeedback());
        newResult.setRated(oldResult.isRated());
        newResult = resultRepository.save(newResult);
        newResult.setSubmission(submission);
        submission.addResult(newResult);
        submissionRepository.save(submission);
        return newResult;
    }

    /**
     * used to assign and save results to submissions
     * Make sure submission.results is loaded
     *
     * @param submission the parent submission of the result
     * @param result the result which we want to save and order
     * @return the result with correctly persisted relationship to its submission
     */
    public Result saveNewResult(Submission submission, final Result result) {
        result.setSubmission(null);
        if (result.getParticipation() == null) {
            result.setParticipation(submission.getParticipation());
        }
        var savedResult = resultRepository.save(result);
        savedResult.setSubmission(submission);
        submission.addResult(savedResult);
        submissionRepository.save(submission);
        return savedResult;
    }

    /**
     * Add a result to the last {@link Submission} of a {@link StudentParticipation}, see {@link StudentParticipation#findLatestSubmission()}, with a feedback of type {@link FeedbackType#AUTOMATIC}.
     * The assessment is counted as {@link AssessmentType#SEMI_AUTOMATIC} to make sure it is not considered for manual assessment, see {@link StudentParticipationRepository#findByExerciseIdWithLatestSubmissionWithoutManualResultsAndIgnoreTestRunParticipation}.
     * Sets the feedback text and result score.
     *
     * @param studentParticipation the studentParticipation containing the latest result
     * @param assessor the assessor
     * @param score the score which should be set
     * @param feedbackText the feedback text for the
     * @param correctionRound the currectCorrection round
     */
    public void addResultWithFeedbackByCorrectionRound(StudentParticipation studentParticipation, User assessor, long score, String feedbackText, int correctionRound) {
        if (studentParticipation.getExercise().isExamExercise() && studentParticipation.findLatestSubmission().isPresent()
                && studentParticipation.findLatestSubmission().get().getResultForCorrectionRound(correctionRound) == null) {
            final var latestSubmission = studentParticipation.findLatestSubmission().get();
            Result result = new Result();
            result.setParticipation(studentParticipation);
            result.setAssessor(assessor);
            result.setCompletionDate(ZonedDateTime.now());
            result.setScore(score);
            result.rated(true);
            // we set the assessment type to semi automatic so that it does not appear to the tutors for manual assessment
            // if we would use AssessmentType.AUTOMATIC, it would be eligable for manual assessment
            result.setAssessmentType(AssessmentType.SEMI_AUTOMATIC);
            result = saveNewResult(latestSubmission, result);

            var feedback = new Feedback();
            feedback.setCredits(0.0);
            feedback.setDetailText(feedbackText);
            feedback.setPositive(false);
            feedback.setType(FeedbackType.AUTOMATIC);
            feedback = feedbackRepository.save(feedback);
            feedback.setResult(result);
            result.setFeedbacks(List.of(feedback));
            resultRepository.save(result);
        }
    }

    public Result prepareTestRunSubmissionForAssessment(Submission submission) {
        Optional<Result> existingAutomaticResult = Optional.empty();
        if (submission.getLatestResult() != null && AssessmentType.AUTOMATIC.equals(submission.getLatestResult().getAssessmentType())) {
            existingAutomaticResult = Optional.of(submission.getLatestResult());
        }

        // we only support one correction round for test runs
        var draftAssessment = lockSubmission(submission, 0);

        // copy feedback from automatic result
        if (existingAutomaticResult.isPresent()) {
            draftAssessment.setAssessmentType(AssessmentType.SEMI_AUTOMATIC);
            draftAssessment.setResultString(existingAutomaticResult.get().getResultString());
            // also saves the draft assessment
            draftAssessment.setFeedbacks(copyFeedbackToNewResult(draftAssessment, existingAutomaticResult.get()));
        }

        return draftAssessment;
    }

    /**
     * Soft locks the submission to prevent other tutors from receiving and assessing it. We set the assessor and save the result to soft lock the assessment in the client, i.e. the client will not allow
     * tutors to assess a submission when an assessor is already assigned. If no result exists for this submission we create one first.
     *
     * @param submission the submission to lock
     */
    protected Result lockSubmission(Submission submission, int correctionRound) {
        Result result = submission.getResultForCorrectionRound(correctionRound);
        if (result == null && correctionRound > 0L) {
            // copy the result of the previous correction round
            result = copyResultFromPreviousRoundAndSave(submission, submission.getResultForCorrectionRound(correctionRound - 1));
        }
        else if (result == null) {
            result = saveNewEmptyResult(submission);
        }

        if (result.getAssessor() == null) {
            result.setAssessor(userService.getUser());
        }

        result.setAssessmentType(AssessmentType.MANUAL);
        // Workaround to prevent the assessor turning into a proxy object after saving
        var assessor = result.getAssessor();
        result = resultRepository.save(result);
        result.setAssessor(assessor);
        return result;
    }

    /**
     * Filters the submissions on each participation so that only the latest submission for each participation remains
     * @param participations Participations for which to reduce the submissions
     * @param submittedOnly Flag whether to only consider submitted submissions when finding the latest one
     */
    public void reduceParticipationSubmissionsToLatest(List<StudentParticipation> participations, boolean submittedOnly) {
        participations.forEach(participation -> {
            participation.getExercise().setStudentParticipations(null);
            Optional<Submission> optionalSubmission = participation.findLatestSubmission();
            if (optionalSubmission.isPresent() && (!submittedOnly || optionalSubmission.get().isSubmitted())) {
                participation.setSubmissions(Set.of(optionalSubmission.get()));
                Optional.ofNullable(optionalSubmission.get().getLatestResult()).ifPresent(result -> participation.setResults(Set.of(result)));
            }
            else {
                participation.setSubmissions(Set.of());
            }
        });
    }

    /**
     * Filters the submissions to contain only in-time submissions if there are any.
     * If not, the original list is returned.
     * @param submissions The submissions to filter
     * @param dueDate The due-date to filter by
     * @param <T> Placeholder for subclass of {@link Submission} e.g. {@link TextSubmission}
     * @return The filtered list of submissions
     */
    protected <T extends Submission> List<T> selectOnlySubmissionsBeforeDueDateOrAll(List<T> submissions, ZonedDateTime dueDate) {
        if (dueDate == null) {
            // this is an edge case, then basically all submissions are before due date
            return submissions;
        }

        boolean hasInTimeSubmissions = submissions.stream().anyMatch(submission -> submission.getSubmissionDate() != null && submission.getSubmissionDate().isBefore(dueDate));
        if (hasInTimeSubmissions) {
            return submissions.stream().filter(submission -> submission.getSubmissionDate() != null && submission.getSubmissionDate().isBefore(dueDate))
                    .collect(Collectors.toList());
        }
        else {
            return submissions;
        }
    }

    /**
     * Checks if the exercise due date has passed. For exam exercises it checks if the latest possible exam end date has passed.
     *
     * @param exercise course exercise or exam exercise that is checked
     */
    public void checkIfExerciseDueDateIsReached(Exercise exercise) throws AccessForbiddenException {
        final boolean isExamMode = exercise.isExamExercise();
        // Tutors cannot start assessing submissions if the exercise due date hasn't been reached yet
        if (isExamMode) {
            ZonedDateTime latestIndividualExamEndDate = this.examService.getLatestIndividualExamEndDate(exercise.getExerciseGroup().getExam());
            if (latestIndividualExamEndDate != null && latestIndividualExamEndDate.isAfter(ZonedDateTime.now())) {
                log.debug("The due date of exercise '" + exercise.getTitle() + "' has not been reached yet.");
                throw new AccessForbiddenException("The due date of exercise '" + exercise.getTitle() + "' has not been reached yet.");
            }
        }
        else {
            // special check for programming exercises as they use buildAndTestStudentSubmissionAfterDueDate instead of dueDate
            if (exercise instanceof ProgrammingExercise) {
                ProgrammingExercise programmingExercise = (ProgrammingExercise) exercise;
                if (programmingExercise.getBuildAndTestStudentSubmissionsAfterDueDate() != null
                        && programmingExercise.getBuildAndTestStudentSubmissionsAfterDueDate().isAfter(ZonedDateTime.now())) {
                    log.debug("The due date to build and test of exercise '" + exercise.getTitle() + "' has not been reached yet.");
                    throw new AccessForbiddenException("The due date to build and test of exercise '" + exercise.getTitle() + "' has not been reached yet.");
                }
            }

            if (exercise.getDueDate() != null && exercise.getDueDate().isAfter(ZonedDateTime.now())) {
                log.debug("The due date of exercise '" + exercise.getTitle() + "' has not been reached yet.");
                throw new AccessForbiddenException("The due date of exercise '" + exercise.getTitle() + "' has not been reached yet.");
            }
        }
    }

    /**
     * Given an exerciseId, returns all the submissions for that exercise, including their results. Submissions can be filtered to include only already submitted
     * submissions
     *
     * @param exerciseId    - the id of the exercise we are interested into
     * @param submittedOnly - if true, it returns only submission with submitted flag set to true
     * @param examMode - set flag to ignore exam test run submissions
     * @param <T> the submission type
     * @return a list of modeling submissions for the given exercise id
     */
    public <T extends Submission> List<T> getAllSubmissionsForExercise(Long exerciseId, boolean submittedOnly, boolean examMode) {
        List<StudentParticipation> participations;
        if (examMode) {
            participations = studentParticipationRepository.findAllWithEagerSubmissionsAndEagerResultsAndEagerAssessorByExerciseIdIgnoreTestRuns(exerciseId);
        }
        else {
            participations = studentParticipationRepository.findAllWithEagerSubmissionsAndEagerResultsAndEagerAssessorByExerciseId(exerciseId);
        }
        List<T> submissions = new ArrayList<>();
        participations.stream().peek(participation -> participation.getExercise().setStudentParticipations(null)).map(StudentParticipation::findLatestSubmission)
                // filter out non submitted submissions if the flag is set to true
                .filter(submission -> submission.isPresent() && (!submittedOnly || submission.get().isSubmitted())).forEach(submission -> submissions.add((T) submission.get()));
        return submissions;
    }
}
