package de.tum.in.www1.artemis.service;

import static de.tum.in.www1.artemis.config.Constants.MAX_NUMBER_OF_LOCKED_SUBMISSIONS_PER_TUTOR;
import static de.tum.in.www1.artemis.web.rest.util.ResponseUtil.forbidden;
import static java.util.stream.Collectors.toList;

import java.security.Principal;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.AssessmentType;
import de.tum.in.www1.artemis.domain.enumeration.ComplaintType;
import de.tum.in.www1.artemis.domain.enumeration.FeedbackType;
import de.tum.in.www1.artemis.domain.enumeration.SubmissionType;
import de.tum.in.www1.artemis.domain.participation.Participation;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.service.exam.ExamDateService;
import de.tum.in.www1.artemis.web.rest.dto.SubmissionWithComplaintDTO;
import de.tum.in.www1.artemis.web.rest.errors.AccessForbiddenException;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;

@Service
public class SubmissionService {

    private final Logger log = LoggerFactory.getLogger(SubmissionService.class);

    private final ExamDateService examDateService;

    private final CourseRepository courseRepository;

    protected final SubmissionRepository submissionRepository;

    protected final ResultRepository resultRepository;

    protected final AuthorizationCheckService authCheckService;

    protected final StudentParticipationRepository studentParticipationRepository;

    protected final ParticipationService participationService;

    protected final UserRepository userRepository;

    protected final FeedbackRepository feedbackRepository;

    protected final ParticipationRepository participationRepository;

    protected final ComplaintRepository complaintRepository;

    public SubmissionService(SubmissionRepository submissionRepository, UserRepository userRepository, AuthorizationCheckService authCheckService,
            ResultRepository resultRepository, StudentParticipationRepository studentParticipationRepository, ParticipationService participationService,
            FeedbackRepository feedbackRepository, ExamDateService examDateService, CourseRepository courseRepository, ParticipationRepository participationRepository,
            ComplaintRepository complaintRepository) {
        this.submissionRepository = submissionRepository;
        this.userRepository = userRepository;
        this.authCheckService = authCheckService;
        this.resultRepository = resultRepository;
        this.studentParticipationRepository = studentParticipationRepository;
        this.participationService = participationService;
        this.feedbackRepository = feedbackRepository;
        this.examDateService = examDateService;
        this.courseRepository = courseRepository;
        this.participationRepository = participationRepository;
        this.complaintRepository = complaintRepository;
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
        final var courseId = exercise.getCourseViaExerciseGroupOrCourseMember().getId();
        final var course = courseRepository.findByIdElseThrow(courseId);
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
        long numberOfLockedSubmissions = submissionRepository.countLockedSubmissionsByUserIdAndCourseId(userRepository.getUserWithGroupsAndAuthorities().getId(), courseId);
        if (numberOfLockedSubmissions >= MAX_NUMBER_OF_LOCKED_SUBMISSIONS_PER_TUTOR) {
            throw new BadRequestAlertException("The limit of locked submissions has been reached", "submission", "lockedSubmissionsLimitReached");
        }
    }

    /**
     * Get simultaneously locked submissions (i.e. unfinished assessments) for the current user in the given course.
     *
     * @param courseId the id of the course
     * @return number of locked submissions for the current user in the given course
     */
    public List<Submission> getLockedSubmissions(long courseId) {
        return submissionRepository.getLockedSubmissionsAndResultsByUserIdAndCourseId(userRepository.getUserWithGroupsAndAuthorities().getId(), courseId);
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
            submissions = participations.stream().map(StudentParticipation::findLatesLegalOrIllegalSubmission).filter(Optional::isPresent).map(Optional::get)
                    .map(submission -> (T) submission)
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
            // Get all participations of submissions that are submitted and do not already have a manual result or belong to test run submissions.
            // No manual result means that no user has started an assessment for the corresponding submission yet.
            participations = studentParticipationRepository.findByExerciseIdWithLatestSubmissionWithoutManualResultsAndIgnoreTestRunParticipation(exercise.getId(),
                    correctionRound);
        }
        else {
            // Get all participations of submissions that are submitted and do not already have a manual result. No manual result means that no user has started an assessment for
            // the
            // corresponding submission yet.
            participations = studentParticipationRepository.findByExerciseIdWithLatestSubmissionWithoutManualResults(exercise.getId());
        }

        List<Submission> submissionsWithoutResult = participations.stream().map(Participation::findLatesLegalOrIllegalSubmission).filter(Optional::isPresent).map(Optional::get)
                .collect(toList());

        if (correctionRound > 0) {
            // remove submission if user already assessed first correction round
            // if disabled, please switch tutorAssessUnique within the tests
            submissionsWithoutResult = submissionsWithoutResult.stream()
                    .filter(submission -> !submission.getResultForCorrectionRound(correctionRound - 1).getAssessor().equals(userRepository.getUser())).collect(Collectors.toList());
        }

        if (submissionsWithoutResult.isEmpty()) {
            return Optional.empty();
        }

        submissionsWithoutResult = selectOnlySubmissionsBeforeDueDateOrAll(submissionsWithoutResult, exercise.getDueDate());

        var submissionWithoutResult = submissionsWithoutResult.get(random.nextInt(submissionsWithoutResult.size()));
        return Optional.of(submissionWithoutResult);
    }

    /**
     * Get all currently locked submissions for all users in the given exam.
     * These are all submissions for which users started, but did not yet finish the assessment.
     *
     * @param examId  - the exam id
     * @param user    - the user trying to access the locked submissions
     * @return        - list of submissions that have locked results in the exam
     */
    public List<Submission> getLockedSubmissions(Long examId, User user) {
        List<Submission> submissions = submissionRepository.getLockedSubmissionsAndResultsByExamId(examId);

        for (Submission submission : submissions) {
            hideDetails(submission, user);
        }
        return submissions;
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
                    submission.setResults(new ArrayList<>());
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
        copyFeedbackToResult(newResult, oldFeedback);
        return newResult.getFeedbacks();
    }

    /**
     * Copy feedback from a feedback list to a Result
     * @param result the result to copy feedback to
     * @param feedbacks the feedbacks which are copied
     */
    private void copyFeedbackToResult(Result result, List<Feedback> feedbacks) {
        feedbacks.forEach(feedback -> {
            Feedback newFeedback = feedback.copyFeedback();
            newFeedback.setPositive(newFeedback.getCredits() > 0);
            result.addFeedback(newFeedback);
        });
        resultRepository.save(result);
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
        return copyResultContentAndAddToSubmission(submission, newResult, oldResult);
    }

    /**
     * This method is used to create a new result, after a complaint has been accepted.
     * The new result contains the updated feedback of the result the complaint belongs to.
     *
     * @param submission the submission where the original result and the result after the complaintResponse belong to
     * @param oldResult the original result, before the response
     * @param feedbacks the new feedbacks after the response
     * @return the newly created result
     */
    public Result createResultAfterComplaintResponse(Submission submission, Result oldResult, List<Feedback> feedbacks) {
        Result newResult = new Result();
        newResult.setParticipation(submission.getParticipation());
        copyFeedbackToResult(newResult, feedbacks);
        newResult = copyResultContentAndAddToSubmission(submission, newResult, oldResult);
        return newResult;
    }

    /**
     * Copies the content of one result to another, and adds the second result to the submission.
     *
     * @param submission the submission which both results belong to, the newResult comes after the oldResult in the resultlist
     * @param newResult the result where the content is set
     * @param oldResult the result from which the content is copied from
     * @return the newResult
     */
    private Result copyResultContentAndAddToSubmission(Submission submission, Result newResult, Result oldResult) {
        newResult.setResultString(oldResult.getResultString());
        newResult.setScore(oldResult.getScore());
        newResult.setHasFeedback(oldResult.getHasFeedback());
        newResult.setRated(oldResult.isRated());
        var savedResult = resultRepository.save(newResult);
        savedResult.setSubmission(submission);
        submission.addResult(savedResult);
        submissionRepository.save(submission);
        return savedResult;
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
     * Add a result to the last {@link Submission} of a {@link StudentParticipation} if it does not exist yet, see {@link StudentParticipation#findLatestSubmission()}, with a feedback of type {@link FeedbackType#AUTOMATIC}.
     * The assessment is counted as {@link AssessmentType#SEMI_AUTOMATIC} to make sure it is not considered for manual assessment, see {@link StudentParticipationRepository#findByExerciseIdWithLatestSubmissionWithoutManualResultsAndIgnoreTestRunParticipation}.
     * Sets the feedback text and result score.
     *
     * @param studentParticipation the studentParticipation containing the latest result
     * @param assessor the assessor
     * @param score the score which should be set
     * @param feedbackText the feedback text for the
     * @param correctionRound the correction round (1 or 2)
     */
    public void addResultWithFeedbackByCorrectionRound(StudentParticipation studentParticipation, User assessor, double score, String feedbackText, int correctionRound) {
        if (studentParticipation.getExercise().isExamExercise()) {
            var latestSubmission = studentParticipation.findLatestSubmission();
            if (latestSubmission.isPresent() && latestSubmission.get().getResultForCorrectionRound(correctionRound) == null) {
                Result result = new Result();
                result.setParticipation(studentParticipation);
                result.setAssessor(assessor);
                result.setCompletionDate(ZonedDateTime.now());
                result.setScore(score);
                result.rated(true);
                // we set the assessment type to semi automatic so that it does not appear to the tutors for manual assessment
                // if we would use AssessmentType.AUTOMATIC, it would be eligible for manual assessment
                result.setAssessmentType(AssessmentType.SEMI_AUTOMATIC);
                result = saveNewResult(latestSubmission.get(), result);

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
    }

    /**
     * Adds a new and empty programmingSubmission to the provided studentParticipation.
     *
     * @param studentParticipation the studentParticipation a new empty programming submission is created for
     */
    public void addEmptyProgrammingSubmissionToParticipation(StudentParticipation studentParticipation) {
        if (studentParticipation.getExercise().isExamExercise()) {
            Submission submission = new ProgrammingSubmission();
            submission.setSubmissionDate(ZonedDateTime.now());
            submission.setType(SubmissionType.INSTRUCTOR);
            submission = submissionRepository.save(submission);
            studentParticipation.setSubmissions(Set.of(submission));
            submission.setParticipation(studentParticipation);
            participationRepository.save(studentParticipation);
        }
    }

    /**
     * Serves as a wrapper method to {@link SubmissionService#lockSubmission} for exam test runs
     * Creates an empty draft assessment with the user as an assessor and copies the automatic feedback (if present) into the new result.
     * NOTE: We only support one correction round for test runs.
     *
     * @param submission the submission
     * @return the draft assessment
     */
    public Result prepareTestRunSubmissionForAssessment(Submission submission) {
        Optional<Result> existingAutomaticResult = Optional.empty();
        if (submission.getLatestResult() != null && AssessmentType.AUTOMATIC.equals(submission.getLatestResult().getAssessmentType())) {
            existingAutomaticResult = resultRepository.findByIdWithEagerFeedbacks(submission.getLatestResult().getId());
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
        if (result == null && correctionRound > 0) {
            // copy the result of the previous correction round
            result = copyResultFromPreviousRoundAndSave(submission, submission.getResultForCorrectionRound(correctionRound - 1));
        }
        else if (result == null) {
            result = saveNewEmptyResult(submission);
        }

        if (result.getAssessor() == null) {
            result.setAssessor(userRepository.getUser());
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
    public void checkIfExerciseDueDateIsReached(Exercise exercise) {
        final boolean isExamMode = exercise.isExamExercise();
        // Tutors cannot start assessing submissions if the exercise due date hasn't been reached yet
        if (isExamMode) {
            ZonedDateTime latestIndividualExamEndDate = examDateService.getLatestIndividualExamEndDate(exercise.getExerciseGroup().getExam());
            if (latestIndividualExamEndDate != null && latestIndividualExamEndDate.isAfter(ZonedDateTime.now())) {
                log.debug("The due date of exercise '{}' has not been reached yet.", exercise.getTitle());
                throw new AccessForbiddenException("The due date of exercise '" + exercise.getTitle() + "' has not been reached yet.");
            }
        }
        else {
            // special check for programming exercises as they use buildAndTestStudentSubmissionAfterDueDate instead of dueDate
            if (exercise instanceof ProgrammingExercise programmingExercise) {
                if (programmingExercise.getBuildAndTestStudentSubmissionsAfterDueDate() != null
                        && programmingExercise.getBuildAndTestStudentSubmissionsAfterDueDate().isAfter(ZonedDateTime.now())) {
                    log.debug("The due date to build and test of exercise '{}' has not been reached yet.", exercise.getTitle());
                    throw new AccessForbiddenException("The due date to build and test of exercise '" + exercise.getTitle() + "' has not been reached yet.");
                }
            }

            if (exercise.getDueDate() != null && exercise.getDueDate().isAfter(ZonedDateTime.now())) {
                log.debug("The due date of exercise '{}' has not been reached yet.", exercise.getTitle());
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
        // we don't have illegal submissions for other exercises than programming
        participations.stream().peek(participation -> participation.getExercise().setStudentParticipations(null)).map(StudentParticipation::findLatestSubmission)
                // filter out non submitted submissions if the flag is set to true
                .filter(submission -> submission.isPresent() && (!submittedOnly || submission.get().isSubmitted())).forEach(submission -> submissions.add((T) submission.get()));
        return submissions;
    }

    /**
     * This method gets all complaints of an exercise and returns them together with their corresponding submission in a DTO
     *
     * @param exerciseId the exerciseId of the exercise of which the complaints are fetched
     * @param principal the current user
     * @param isAtLeastInstructor if the user is an instructor
     * @return a list of DTOs containing a complaint and its submission
     */
    public List<SubmissionWithComplaintDTO> getSubmissionsWithComplaintsForExercise(Long exerciseId, Principal principal, boolean isAtLeastInstructor) {
        List<SubmissionWithComplaintDTO> submissionWithComplaintDTOs = new ArrayList<>();

        // get all complaints which belong to the exercise
        List<Complaint> complaints = complaintRepository.getAllComplaintsByExerciseIdAndComplaintType(exerciseId, ComplaintType.COMPLAINT);
        var complaintMap = complaints.stream().collect(Collectors.toMap(complaint -> complaint.getResult().getId(), value -> value));

        if (complaints.isEmpty()) {
            return submissionWithComplaintDTOs;
        }
        // get the ids of all results which have a complaint, and with those fetch all their submissions
        List<Long> submissionIds = complaints.stream().map(complaint -> complaint.getResult().getSubmission().getId()).collect(toList());
        List<Submission> submissions = submissionRepository.findBySubmissionIdsWithEagerResults(submissionIds);

        // add each submission with its complaint to the DTO
        submissions.stream().filter(submission -> submission.getResultWithComplaint() != null).forEach(submission -> {
            // get the complaint which belongs to the submission
            Complaint complaintOfSubmission = complaintMap.get(submission.getResultWithComplaint().getId());
            prepareComplaintAndSubmission(complaintOfSubmission, submission);
            submissionWithComplaintDTOs.add(new SubmissionWithComplaintDTO(submission, complaintOfSubmission));
        });

        return submissionWithComplaintDTOs;
    }

    /**
     * Helper method to prepare the complaint for the client
     * @param complaint the complaint which gets prepared
     */
    private void prepareComplaintAndSubmission(Complaint complaint, Submission submission) {
        StudentParticipation studentParticipation = (StudentParticipation) complaint.getResult().getParticipation();
        studentParticipation.setParticipant(null);
        studentParticipation.setExercise(null);
        complaint.setParticipant(null);

        StudentParticipation submissionsParticipation = (StudentParticipation) submission.getParticipation();
        submissionsParticipation.setParticipant(null);
        submissionsParticipation.setExercise(null);
    }
}
