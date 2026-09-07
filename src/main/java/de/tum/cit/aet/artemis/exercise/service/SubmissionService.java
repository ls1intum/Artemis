package de.tum.cit.aet.artemis.exercise.service;

import static de.tum.cit.aet.artemis.core.config.Constants.MAX_NUMBER_OF_LOCKED_SUBMISSIONS_PER_TUTOR;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.assessment.domain.AssessmentNote;
import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.assessment.domain.Complaint;
import de.tum.cit.aet.artemis.assessment.domain.ComplaintType;
import de.tum.cit.aet.artemis.assessment.domain.Feedback;
import de.tum.cit.aet.artemis.assessment.domain.FeedbackType;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.assessment.repository.ComplaintRepository;
import de.tum.cit.aet.artemis.assessment.repository.FeedbackRepository;
import de.tum.cit.aet.artemis.assessment.repository.ResultRepository;
import de.tum.cit.aet.artemis.assessment.repository.ScaFeedbackRepository;
import de.tum.cit.aet.artemis.assessment.repository.TestCaseFeedbackRepository;
import de.tum.cit.aet.artemis.assessment.service.FeedbackService;
import de.tum.cit.aet.artemis.athena.api.AthenaApi;
import de.tum.cit.aet.artemis.core.dto.SearchResultPageDTO;
import de.tum.cit.aet.artemis.core.dto.pageablesearch.SearchTermPageableSearchDTO;
import de.tum.cit.aet.artemis.core.exception.AccessForbiddenAlertException;
import de.tum.cit.aet.artemis.core.exception.AccessForbiddenException;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.core.util.PageUtil;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.Submission;
import de.tum.cit.aet.artemis.exercise.domain.SubmissionType;
import de.tum.cit.aet.artemis.exercise.domain.participation.Participation;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.dto.SubmissionOwnerDTO;
import de.tum.cit.aet.artemis.exercise.dto.SubmissionWithComplaintDTO;
import de.tum.cit.aet.artemis.exercise.repository.ParticipationRepository;
import de.tum.cit.aet.artemis.exercise.repository.StudentParticipationRepository;
import de.tum.cit.aet.artemis.exercise.repository.SubmissionRepository;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingSubmission;
import de.tum.cit.aet.artemis.text.domain.TextSubmission;

@Profile(PROFILE_CORE)
@Lazy
@Service
// TODO: this class has too many dependencies to other services. We should reduce this
public class SubmissionService {

    private static final Logger log = LoggerFactory.getLogger(SubmissionService.class);

    private static final String ENTITY_NAME = "submission";

    private final ExerciseDateService exerciseDateService;

    protected final SubmissionRepository submissionRepository;

    protected final ResultRepository resultRepository;

    protected final AuthorizationCheckService authCheckService;

    protected final StudentParticipationRepository studentParticipationRepository;

    protected final ParticipationService participationService;

    protected final UserRepository userRepository;

    protected final FeedbackRepository feedbackRepository;

    protected final ParticipationRepository participationRepository;

    protected final ComplaintRepository complaintRepository;

    protected final FeedbackService feedbackService;

    private final Optional<AthenaApi> athenaApi;

    private final TestCaseFeedbackRepository testCaseFeedbackRepository;

    private final ScaFeedbackRepository scaFeedbackRepository;

    public SubmissionService(SubmissionRepository submissionRepository, UserRepository userRepository, AuthorizationCheckService authCheckService,
            ResultRepository resultRepository, StudentParticipationRepository studentParticipationRepository, ParticipationService participationService,
            FeedbackRepository feedbackRepository, ExerciseDateService exerciseDateService, ParticipationRepository participationRepository,
            ComplaintRepository complaintRepository, FeedbackService feedbackService, Optional<AthenaApi> athenaApi, TestCaseFeedbackRepository testCaseFeedbackRepository,
            ScaFeedbackRepository scaFeedbackRepository) {
        this.submissionRepository = submissionRepository;
        this.userRepository = userRepository;
        this.authCheckService = authCheckService;
        this.resultRepository = resultRepository;
        this.studentParticipationRepository = studentParticipationRepository;
        this.participationService = participationService;
        this.feedbackRepository = feedbackRepository;
        this.exerciseDateService = exerciseDateService;
        this.participationRepository = participationRepository;
        this.complaintRepository = complaintRepository;
        this.feedbackService = feedbackService;
        this.athenaApi = athenaApi;
        this.testCaseFeedbackRepository = testCaseFeedbackRepository;
        this.scaFeedbackRepository = scaFeedbackRepository;
    }

    /**
     * Check that the user is allowed to make the submission
     *
     * @param exercise    the exercise for which a submission should be saved
     * @param submission  the submission that should be saved
     * @param currentUser the current user with groups and authorities
     */
    public void checkSubmissionAllowanceElseThrow(Exercise exercise, Submission submission, User currentUser) {
        // The exercise was loaded from the database by the caller, so its course is a persisted entity and not something
        // the client could have tampered with. Re-reading it by id would only repeat a row we are already holding.
        final var course = exercise.getCourseViaExerciseGroupOrCourseMember();
        if (!authCheckService.isAtLeastStudentInCourse(course, currentUser)) {
            throw new AccessForbiddenException();
        }

        // Fetch the submission with the corresponding participation if the id is set (on update) and check that the
        // user of the participation is the same as the user who executes this call (or student in the team).
        // This prevents injecting submissions to other users.
        if (submission.getId() != null) {
            // Ask the database who owns the submission instead of loading it: the entity drags in its participation,
            // exercise, exercise group, exam and course through eager associations, which is several statements to
            // compare a login. This runs on every autosave.
            SubmissionOwnerDTO owner = submissionRepository.findOwnerBySubmissionId(submission.getId()).orElseThrow(AccessForbiddenException::new);
            if (owner.studentLogin() != null && !owner.studentLogin().equals(currentUser.getLogin())) {
                throw new AccessForbiddenException();
            }
            if (owner.teamShortName() != null && !authCheckService.isStudentInTeam(course, owner.teamShortName(), currentUser)) {
                throw new AccessForbiddenException();
            }
        }
    }

    /**
     * Check if the limit of simultaneously locked submissions (i.e. unfinished assessments) has been reached for the current user in the given course. Throws a
     * BadRequestAlertException if the limit has been reached.
     *
     * @param courseId the id of the course
     */
    public void checkSubmissionLockLimit(long courseId) {
        long numberOfLockedSubmissions = submissionRepository.countLockedSubmissionsByUserIdAndCourseId(userRepository.getUserWithAuthorities().getId(), courseId);
        if (numberOfLockedSubmissions >= MAX_NUMBER_OF_LOCKED_SUBMISSIONS_PER_TUTOR) {
            throw new BadRequestAlertException("The limit of locked submissions has been reached", "submission", "lockedSubmissionsLimitReached");
        }
    }

    /**
     * Get simultaneously locked submissions (i.e. unfinished assessments) for the current user in the given course.
     *
     * @param courseId the id of the course
     * @return the locked submissions for the current user in the given course
     */
    public List<Submission> getLockedSubmissions(long courseId) {
        return submissionRepository.getLockedSubmissionsAndResultsByUserIdAndCourseId(userRepository.getUserWithAuthorities().getId(), courseId);
    }

    /**
     * Given an exercise id and a tutor id, it returns all the submissions where the tutor has a result associated.
     *
     * @param exerciseId      - the id of the exercise we are looking for
     * @param correctionRound - the correction round we want our submission to have results for
     * @param tutor           - the tutor we are interested in
     * @param examMode        - flag should be set to ignore the test run submissions
     * @param <T>             the submission type
     * @return list of submissions
     */
    public <T extends Submission> List<T> getAllSubmissionsAssessedByTutorForCorrectionRoundAndExerciseIgnoreTestRuns(Long exerciseId, User tutor, boolean examMode,
            int correctionRound) {
        List<T> submissions;
        if (examMode) {
            var participations = this.studentParticipationRepository.findAllByParticipationExerciseIdAndResultAssessorAndCorrectionRoundIgnoreTestRuns(exerciseId, tutor);
            submissions = participations.stream().map(StudentParticipation::findLatestSubmission).filter(Optional::isPresent).map(Optional::get).map(submission -> (T) submission)
                    .filter(submission -> submission.hasResultForCorrectionRound(correctionRound)).collect(Collectors.toCollection(ArrayList::new));
        }
        else {
            submissions = this.submissionRepository.findAllByParticipationExerciseIdAndResultAssessorIgnoreTestRuns(exerciseId, tutor);
        }

        submissions.forEach(submission -> submission.getLatestResult().setSubmission(null));
        return submissions;
    }

    protected List<Submission> getAssessableSubmissions(Exercise exercise, boolean examMode, int correctionRound) {
        // TODO: it really does not make sense to fetch these submissions with all related data from the database just to select one submission afterwards
        // it would be better to fetch them with minimal related data (so we can select one) and then afterwards fetch the selected one with all related data

        final List<StudentParticipation> participations;
        if (examMode) {
            // Get all participations of submissions that are submitted and do not already have a manual result or belong to test run submissions.
            // No manual result means that no tutor has started an assessment for the corresponding submission yet.
            participations = studentParticipationRepository.findByExerciseIdWithLatestSubmissionWithoutManualResultsAndIgnoreTestRunParticipation(exercise.getId(),
                    correctionRound);
        }
        else {
            // Get all participations of submissions that are submitted and do not already have a manual result.
            // No manual result means that no user has started an assessment for the corresponding submission yet.
            // Does not fetch participations for which the due date has not yet passed.
            participations = studentParticipationRepository.findByExerciseIdWithLatestSubmissionWithoutManualResultsWithPassedIndividualDueDateIgnoreTestRuns(exercise.getId(),
                    ZonedDateTime.now());
        }

        var submissionsWithoutResult = participations.stream().map(Participation::findLatestSubmission).filter(Optional::isPresent).map(Optional::get).toList();

        if (correctionRound > 0) {
            // remove submission if user already assessed first correction round
            // if disabled, please switch tutorAssessUnique within the tests
            // TODO: we could move this check into the database call of the if clause above (examMode == true) to avoid fetching all results and assessors
            final var user = userRepository.getUser();
            submissionsWithoutResult = submissionsWithoutResult.stream().filter(submission -> {
                final var resultForCorrectionRound = submission.getResultForCorrectionRound(correctionRound - 1);
                return resultForCorrectionRound != null && !resultForCorrectionRound.getAssessor().equals(user);
            }).toList();
        }

        if (!examMode && exercise.getDueDate() != null) {
            submissionsWithoutResult = selectOnlySubmissionsBeforeDueDate(submissionsWithoutResult);
        }

        return submissionsWithoutResult;
    }

    /**
     * Given an exercise, find the submission to assess using Athena, if enabled.
     *
     * @param <S>                 the submission type
     * @param exercise            the exercise for which we want to retrieve a submission without manual result
     * @param skipAssessmentQueue skip the Athena assessment queue and return a random submission
     * @param examMode            flag to determine if test runs should be removed. This should be set to true for exam exercises
     * @param correctionRound     the correction round we want our submission to have results for
     * @param findSubmissionById  method to find a submission by id
     * @return a submission without any manual result or an empty Optional if no submission without manual result could be found
     */
    public <S extends Submission> Optional<S> getAthenaSubmissionToAssess(Exercise exercise, boolean skipAssessmentQueue, boolean examMode, int correctionRound,
            Function<Long, Optional<S>> findSubmissionById) {
        if (exercise.areFeedbackSuggestionsEnabled() && athenaApi.isPresent() && !skipAssessmentQueue && correctionRound == 0) {
            var assessableSubmissions = getAssessableSubmissions(exercise, examMode, correctionRound);
            var athenaSubmissionId = athenaApi.get().getProposedSubmissionId(exercise, assessableSubmissions.stream().map(Submission::getId).toList());
            if (athenaSubmissionId.isPresent()) {
                var submission = findSubmissionById.apply(athenaSubmissionId.get());
                // Test again if it is still assessable (Athena might have taken some time to respond and another assessment might have started in the meantime):
                if (submission.isPresent() && (submission.get().getLatestResult() == null || !submission.get().getLatestResult().isManual())) {
                    return submission;
                }
                else {
                    log.debug("Athena proposed submission {} is not assessable anymore", athenaSubmissionId.get());
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Given an exercise, find a submission to assess using Athena (or alternatively randomly).
     *
     * @param <S>                 the submission type
     * @param exercise            the exercise for which we want to retrieve a submission without manual result
     * @param skipAssessmentQueue skip the Athena assessment queue and return a random submission
     * @param correctionRound     the correction round we want our submission to have results for
     * @param examMode            flag to determine if test runs should be removed. This should be set to true for exam exercises
     * @param findSubmissionById  method to find a submission by id
     * @return a submission without any manual result or an empty Optional if no submission without manual result could be found
     */
    public <S extends Submission> Optional<S> getRandomAssessableSubmission(Exercise exercise, boolean skipAssessmentQueue, boolean examMode, int correctionRound,
            Function<Long, Optional<S>> findSubmissionById) {
        var submissionProposedByAthena = getAthenaSubmissionToAssess(exercise, skipAssessmentQueue, examMode, correctionRound, findSubmissionById);
        if (submissionProposedByAthena.isPresent()) {
            return submissionProposedByAthena;
        }

        return (Optional<S>) getRandomAssessableSubmission(exercise, examMode, correctionRound);
    }

    /**
     * Given an exercise, find a random submission for that exercise which still doesn't have any manual result.
     * No manual result means that no user has started an assessment for the corresponding submission yet.
     * For exam exercises we should also remove the test run participations as these should not be graded by the tutors.
     * If {@code correctionRound} is bigger than 0, only submissions are shown for which the user has not assessed the first result.
     *
     * @param exercise        the exercise for which we want to retrieve a submission without manual result
     * @param correctionRound the correction round we want our submission to have results for
     * @param examMode        flag to determine if test runs should be removed. This should be set to true for exam exercises
     * @return a submission without any manual result or an empty Optional if no submission without manual result could be found
     */
    public Optional<Submission> getRandomAssessableSubmission(Exercise exercise, boolean examMode, int correctionRound) {
        var assessableSubmissions = getAssessableSubmissions(exercise, examMode, correctionRound);
        return getRandomAssessableSubmission(assessableSubmissions);
    }

    /**
     * Given a list of submissions, find a random one.
     *
     * @param assessableSubmissions the list of submissions to choose from
     * @return a random submission or an empty Optional if no submission was passed
     */
    public Optional<Submission> getRandomAssessableSubmission(List<Submission> assessableSubmissions) {
        return assessableSubmissions.isEmpty() ? Optional.empty() : Optional.of(assessableSubmissions.get(ThreadLocalRandom.current().nextInt(assessableSubmissions.size())));
    }

    /**
     * Get all currently locked submissions across the given exercises (used for an exam).
     * These are all submissions for which users started, but did not yet finish the assessment.
     *
     * @param exerciseIds - the ids of the exam's exercises
     * @param user        - the user trying to access the locked submissions
     * @return - list of submissions that have locked results in the exam
     */
    public List<Submission> getLockedSubmissions(Collection<Long> exerciseIds, User user) {
        List<Submission> submissions = submissionRepository.getLockedSubmissionsAndResultsByExerciseIds(exerciseIds);

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
     * @param user       the currently logged-in user which is used for hiding specific submission details based on instructor and teaching assistant rights
     */
    public void hideDetails(Submission submission, User user) {
        // do not send old submissions or old results to the client
        if (submission.getParticipation() != null) {
            submission.getParticipation().setSubmissions(null);

            Exercise exercise = submission.getParticipation().getExercise();
            if (exercise != null) {
                // make sure that sensitive information is not sent to the client for students
                if (!authCheckService.isAtLeastTeachingAssistantForExercise(exercise, user)) {
                    exercise.filterSensitiveInformation();
                    submission.setResults(new HashSet<>());
                }
                // remove information about the student or team from the submission for tutors to ensure a double-blind assessment
                if (!authCheckService.isAtLeastInstructorForExercise(exercise, user)) {
                    StudentParticipation studentParticipation = (StudentParticipation) submission.getParticipation();

                    // the student themself is allowed to see the participant (i.e. themself or their team) of their participation
                    if (!authCheckService.isOwnerOfParticipation(studentParticipation, user)) {
                        studentParticipation.filterSensitiveInformation();
                    }
                }
            }
        }
    }

    /**
     * Creates a new Result object, assigns it to the given submission and stores the changes to the database.
     *
     * @param submission the submission for which a new result should be created
     * @return the newly created result
     */
    public Result saveNewEmptyResult(Submission submission) {
        return saveNewEmptyResult(submission, submission.getParticipation().getExercise().getId());
    }

    /**
     * Creates a new Result object, assigns it to the given submission and stores the changes to the database.
     *
     * @param submission the submission for which a new result should be created
     * @param exerciseId the id of the exercise to which the submission belongs
     * @return the newly created result
     */
    public Result saveNewEmptyResult(Submission submission, long exerciseId) {
        Result result = new Result();
        result.setSubmission(submission);
        result.setExerciseId(exerciseId);
        submission.addResult(result);
        result = resultRepository.save(result);
        submissionRepository.save(submission);
        return result;
    }

    private static void setExerciseIdFromSubmission(Submission submission, Result result) {
        if (submission.getParticipation() != null && submission.getParticipation().getExercise() != null) {
            result.setExerciseId(submission.getParticipation().getExercise().getId());
        }
    }

    /**
     * Copy Feedbacks from one Result to another Result
     *
     * @param newResult new result to copy feedback to
     * @param oldResult old result to copy feedback from
     * @return the set of newly created feedbacks
     */
    public Set<Feedback> copyFeedbackToNewResult(Result newResult, Result oldResult) {
        Collection<Feedback> oldFeedback = oldResult.getFeedbacks();
        copyFeedbackToResult(newResult, oldFeedback);
        copyTypedFeedbackToResult(newResult, oldResult);
        return newResult.getFeedbacks();
    }

    /**
     * Copies the typed automatic feedback (test-case and SCA rows) of the old result to the new result.
     * The rows are loaded from the database (the old result's collections may be uninitialized) and the
     * copies share the deduplicated message rows. No-op for results of non-programming exercises.
     *
     * @param newResult the result to copy the typed feedback to
     * @param oldResult the result to copy the typed feedback from
     */
    protected void copyTypedFeedbackToResult(Result newResult, Result oldResult) {
        if (oldResult == null || oldResult.getId() == null) {
            return;
        }
        // fetch the shared message rows eagerly: the copies keep the message reference, and the new result
        // may be synthesized for serialization right away (e.g. exam test-run drafts) - a lazy proxy would
        // fail there with a LazyInitializationException
        testCaseFeedbackRepository.findWithTestCaseAndMessageByResultIds(List.of(oldResult.getId())).stream().map(feedbackService::copyTestCaseFeedback)
                .forEach(newResult::addTestCaseFeedback);
        scaFeedbackRepository.findWithMessageByResultIds(List.of(oldResult.getId())).stream().map(feedbackService::copyScaFeedback).forEach(newResult::addScaFeedback);

        // Insert the copies right away when the target result already exists: a synthesized legacy view is addressed by the id of the row it comes from, so every caller that
        // serializes the new result afterwards needs those ids. The rows are new, so this persists them in place - the ids land on these very instances and the eagerly
        // fetched test cases and messages above survive, both of which a merge copy would lose. A result that is not persisted yet gets its rows through the caller's save.
        if (newResult.getId() != null) {
            newResult.setTestCaseFeedbacks(testCaseFeedbackRepository.saveAll(newResult.getTestCaseFeedbacks()));
            newResult.setScaFeedbacks(scaFeedbackRepository.saveAll(newResult.getScaFeedbacks()));
        }
    }

    /**
     * Copy feedback from a feedback list to a Result
     *
     * @param result    the result to copy feedback to
     * @param feedbacks the feedbacks which are copied
     */
    private void copyFeedbackToResult(Result result, Collection<Feedback> feedbacks) {
        if (feedbacks == null) {
            return;
        }
        feedbacks.forEach(feedback -> {
            Feedback newFeedback = feedbackService.copyFeedback(feedback);
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
     * @param oldResult  result to copy from
     * @return the newly created copy of the oldResult
     */
    public Result copyResultFromPreviousRoundAndSave(Submission submission, Result oldResult) {
        if (oldResult == null) {
            return saveNewEmptyResult(submission);
        }
        Result newResult = new Result();
        setExerciseIdFromSubmission(submission, newResult);
        // Set before copying the feedback, which saves the result: the result owns the foreign key to its submission.
        newResult.setSubmission(submission);
        copyFeedbackToNewResult(newResult, oldResult);
        return copyResultContentAndAddToSubmission(submission, newResult, oldResult);
    }

    /**
     * This method is used to create a new result, after a complaint has been accepted.
     * The new result contains the updated feedback of the result the complaint belongs to.
     *
     * @param submission         the submission where the original result and the result after the complaintResponse belong to
     * @param oldResult          the original result, before the response
     * @param feedbacks          the new feedbacks after the response
     * @param assessmentNoteText the new text of the assessment note
     * @return the newly created result
     */
    public Result createResultAfterComplaintResponse(Submission submission, Result oldResult, List<Feedback> feedbacks, String assessmentNoteText) {
        Result newResult = new Result();
        setExerciseIdFromSubmission(submission, newResult);
        // Set before the first save below: copyFeedbackToResult saves the result, and the result owns the foreign key
        // to its submission, so leaving it for copyResultContentAndAddToSubmission would insert a result without one.
        newResult.setSubmission(submission);
        updateAssessmentNoteAfterComplaintResponse(newResult, assessmentNoteText, submission.getLatestResult().getAssessor());
        List<Feedback> feedbackToCopy = new ArrayList<>(feedbacks);
        if (submission.getParticipation().getExercise() instanceof ProgrammingExercise) {
            // The client echoes the automatic test-case and SCA feedback items it received (synthesized
            // from the typed collections, hence without ids) - they are copied as typed rows below instead.
            feedbackToCopy.removeIf(feedback -> (feedback.getId() == null || feedback.getId() < 0) && (feedback.getTestCase() != null || feedback.isStaticCodeAnalysisFeedback()));
        }
        copyFeedbackToResult(newResult, feedbackToCopy);
        copyTypedFeedbackToResult(newResult, oldResult);
        newResult = copyResultContentAndAddToSubmission(submission, newResult, oldResult);
        return newResult;
    }

    private void updateAssessmentNoteAfterComplaintResponse(Result newResult, String assessmentNoteText, User assessor) {
        AssessmentNote newNote = new AssessmentNote();
        newNote.setCreator(assessor);
        newNote.setNote(assessmentNoteText);
        newResult.setAssessmentNote(newNote);
    }

    /**
     * Copies the content of one result to another, and adds the second result to the submission.
     *
     * @param submission the submission which both results belong to, the newResult comes after the oldResult in the result list
     * @param newResult  the result where the content is set
     * @param oldResult  the result from which the content is copied from
     * @return the newResult
     */
    private Result copyResultContentAndAddToSubmission(Submission submission, Result newResult, Result oldResult) {
        newResult.setScore(oldResult.getScore());
        newResult.setRated(oldResult.isRated());
        newResult.copyProgrammingExerciseCounters(oldResult);
        newResult.setSubmission(submission);
        var savedResult = resultRepository.save(newResult);
        submission.addResult(savedResult);
        submissionRepository.save(submission);
        return savedResult;
    }

    /**
     * used to assign and save results to submissions
     * Make sure submission.results is loaded
     *
     * @param submission the parent submission of the result
     * @param result     the result which we want to save and order
     * @return the result with correctly persisted relationship to its submission
     */
    public Result saveNewResult(Submission submission, final Result result) {
        result.setSubmission(submission);
        if (result.getSubmission().getParticipation() == null) {
            result.getSubmission().setParticipation(submission.getParticipation());
        }
        var savedResult = resultRepository.save(result);
        submission.addResult(savedResult);
        submissionRepository.save(submission);
        return savedResult;
    }

    /**
     * Add a result to the last {@link Submission} of a {@link StudentParticipation} if it does not exist yet, see {@link StudentParticipation#findLatestSubmission()}, with a
     * feedback of type {@link FeedbackType#AUTOMATIC}.
     * The assessment is counted as {@link AssessmentType#SEMI_AUTOMATIC} to make sure it is not considered for manual assessment, see
     * {@link StudentParticipationRepository#findByExerciseIdWithLatestSubmissionWithoutManualResultsAndIgnoreTestRunParticipation}.
     * Sets the feedback text and result score.
     *
     * @param studentParticipation the studentParticipation containing the latest result
     * @param assessor             the assessor
     * @param score                the score which should be set
     * @param feedbackText         the feedback text for the
     * @param correctionRound      the correction round (1 or 2)
     */
    // TODO: we should move this method into the resultService
    public void addResultWithFeedbackByCorrectionRound(StudentParticipation studentParticipation, User assessor, double score, String feedbackText, int correctionRound) {
        if (studentParticipation.getExercise().isExamExercise()) {
            var latestSubmission = studentParticipation.findLatestSubmission();
            if (latestSubmission.isPresent() && latestSubmission.get().getResultForCorrectionRound(correctionRound) == null) {
                Result result = new Result();
                setExerciseIdFromSubmission(latestSubmission.get(), result);
                result.setAssessor(assessor);
                result.setCompletionDate(ZonedDateTime.now());
                result.setScore(score, studentParticipation.getExercise().getCourseViaExerciseGroupOrCourseMember());
                result.rated(true);
                // we set the assessment type to semi-automatic so that it does not appear to the tutors for manual assessment
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
        if (submission.getLatestResult() != null && AssessmentType.AUTOMATIC == submission.getLatestResult().getAssessmentType()) {
            existingAutomaticResult = resultRepository.findByIdWithEagerFeedbacks(submission.getLatestResult().getId());
        }

        // we only support one correction round for test runs
        var draftAssessment = lockSubmission(submission, 0);

        // copy feedback from automatic result
        if (existingAutomaticResult.isPresent()) {
            draftAssessment.setAssessmentType(AssessmentType.SEMI_AUTOMATIC);
            draftAssessment.setFeedbacks(copyFeedbackToNewResult(draftAssessment, existingAutomaticResult.get()));
            // copyFeedbackToNewResult saves the draft before the typed test-case/SCA copies are attached -
            // save again so the typed rows are persisted with the draft. Deliberately keep (and return) the
            // original object instead of the merge result: the merge replaces the eagerly fetched test-case
            // and message associations of the copies with uninitialized proxies, which would break the
            // synthesized serialization of the draft.
            resultRepository.save(draftAssessment);
        }

        return draftAssessment;
    }

    /**
     * Soft locks the submission to prevent other tutors from receiving and assessing it. We set the assessor and save the result to soft lock the assessment in the client, i.e.
     * the client will not allow tutors to assess a submission when an assessor is already assigned. If no result exists for this submission we create one first.
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

        // The round this result belongs to is stored on the result itself. This is the one place where a manual result
        // for a correction round is created or claimed, so it is also where a result that predates the column gets its
        // round the first time a tutor opens it.
        result.setCorrectionRound(correctionRound);
        result.setAssessmentType(AssessmentType.MANUAL);
        // Deliberately keep (and return) the object the submission's result set already holds instead of the
        // merge result: the set ignores re-adding an equal copy, so a caller that adds the returned result back
        // would keep the stale instance. Returning the original also avoids the assessor (and every other
        // association) turning into an uninitialized proxy, which the merge result does.
        resultRepository.save(result);
        return result;
    }

    /**
     * Filters the submissions on each participation so that only the latest submission for each participation remains
     *
     * @param participations Participations for which to reduce the submissions
     * @param submittedOnly  Flag whether to only consider submitted submissions when finding the latest one
     */
    public void reduceParticipationSubmissionsToLatest(List<StudentParticipation> participations, boolean submittedOnly) {
        participations.forEach(participation -> {
            participation.getExercise().setStudentParticipations(null);
            Optional<Submission> optionalSubmission = participation.findLatestSubmission();
            if (optionalSubmission.isPresent() && (!submittedOnly || optionalSubmission.get().isSubmitted())) {
                participation.setSubmissions(Set.of(optionalSubmission.get()));
            }
            else {
                participation.setSubmissions(Set.of());
            }
        });
    }

    /**
     * Filters the submissions to contain only in-time submissions if there are any.
     * If not, the original list is returned.
     *
     * @param submissions The submissions to filter
     * @param <T>         Placeholder for subclass of {@link Submission} e.g. {@link TextSubmission}
     * @return The filtered list of submissions
     */
    protected <T extends Submission> List<T> selectOnlySubmissionsBeforeDueDate(List<T> submissions) {
        final List<T> submissionsBeforeDueDate = submissions.stream().filter(this::isBeforeDueDate).toList();
        if (!submissionsBeforeDueDate.isEmpty()) {
            return submissionsBeforeDueDate;
        }
        else {
            return submissions;
        }
    }

    /**
     * Checks if the submission was created before the due date of the exercise.
     *
     * @param submission a student’s submission
     * @return true, if the submission date was before the due date or the exercise has no due date.
     */
    private boolean isBeforeDueDate(Submission submission) {
        return ExerciseDateService.getDueDate(submission.getParticipation())
                .map(dueDate -> submission.getSubmissionDate() != null && submission.getSubmissionDate().isBefore(dueDate)).orElse(true);
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
            checkThatAssessmentIsPossibleElseThrow(exercise, null);
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

            if (exerciseDateService.isBeforeEarliestDueDate(exercise).orElse(false)) {
                log.debug("The due date of exercise '{}' has not been reached yet.", exercise.getTitle());
                throw new AccessForbiddenException("The due date of exercise '" + exercise.getTitle() + "' has not been reached yet.");
            }
        }
    }

    /**
     * Checks that manual assessment of the given exam exercise is already possible, i.e. that the exam is over for every
     * student and, for programming exercises, that the tests have run once more on the final submissions.
     * <p>
     * This is the shared gate every endpoint that opens an assessment goes through, for all exercise types: without it
     * tutors can start grading while students are still working, and would grade a submission the student then replaces
     * (see issue #13358). It is a no-op for course exercises (they are covered by
     * {@link #checkIfExerciseDueDateIsReached}) and for instructor test runs, which happen before the exam starts and
     * must stay assessable.
     * <p>
     * NOTE: the check is exam-wide, i.e. based on the latest individual end date of all student exams, not on the
     * individual student behind the given participation. Granting one student more working time therefore postpones
     * assessment of the whole exercise. For test exams the latest individual end date is derived from the start of the
     * availability window rather than from each attempt, so the gate opens earlier than the last attempt can end; test
     * exams cannot be assessed anyway, as they are validated to zero correction rounds.
     *
     * @param exercise      the exercise whose submission a tutor wants to assess
     * @param participation the participation the submission belongs to, or {@code null} if no specific participation is
     *                          addressed (then test runs cannot be detected, which is correct for endpoints that never
     *                          serve test run submissions)
     * @throws AccessForbiddenAlertException if assessment is not possible yet, carrying the date from which on it is
     */
    public void checkThatAssessmentIsPossibleElseThrow(Exercise exercise, @Nullable Participation participation) {
        if (!exercise.isExamExercise()) {
            return;
        }
        if (participation instanceof StudentParticipation studentParticipation && studentParticipation.isTestRun()) {
            return;
        }

        var examAssessmentDates = exerciseDateService.getExamAssessmentDates(exercise);
        if (examAssessmentDates == null) {
            // the exam has no dates yet, so there is nothing to wait for; this matches the previous behaviour
            return;
        }
        final ZonedDateTime now = ZonedDateTime.now();
        if (now.isBefore(examAssessmentDates.latestExamEndDate())) {
            log.debug("Assessment of exam exercise '{}' is not possible yet, the exam is over for all students at {}.", exercise.getTitle(),
                    examAssessmentDates.latestExamEndDate());
            throw new AccessForbiddenAlertException(
                    "Assessment is not possible yet, the exam of exercise '" + exercise.getTitle() + "' is still running until " + examAssessmentDates.latestExamEndDate(),
                    ENTITY_NAME, "assessmentNotPossibleExamRunning", Map.of("date", ISO_OFFSET_DATE_TIME.format(examAssessmentDates.latestExamEndDate())), true);
        }
        if (now.isBefore(examAssessmentDates.assessmentPossibleFrom())) {
            log.debug("Assessment of exam exercise '{}' is not possible yet, the tests still run on the final submissions until {}.", exercise.getTitle(),
                    examAssessmentDates.assessmentPossibleFrom());
            throw new AccessForbiddenAlertException(
                    "Assessment is not possible yet, the tests of exercise '" + exercise.getTitle() + "' still run on the final submissions until "
                            + examAssessmentDates.assessmentPossibleFrom(),
                    ENTITY_NAME, "assessmentNotPossibleTestsPending", Map.of("date", ISO_OFFSET_DATE_TIME.format(examAssessmentDates.assessmentPossibleFrom())), true);
        }
    }

    /**
     * Given an exerciseId, returns all the submissions for that exercise, including their results. Submissions can be filtered to include only already submitted
     * submissions
     *
     * @param exerciseId    - the id of the exercise we are interested into
     * @param submittedOnly - if true, it returns only submission with submitted flag set to true
     * @param examMode      - set flag to ignore exam test run submissions
     * @param <T>           the submission type
     * @return a list of modeling submissions for the given exercise id
     */
    public <T extends Submission> List<T> getAllSubmissionsForExercise(Long exerciseId, boolean submittedOnly, boolean examMode) {
        Collection<StudentParticipation> participations;
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
     * @param exerciseId          the exerciseId of the exercise of which the complaints are fetched
     * @param isAtLeastInstructor if the user is an instructor
     * @return a list of DTOs containing a complaint and its submission
     */
    public List<SubmissionWithComplaintDTO> getSubmissionsWithComplaintsForExercise(Long exerciseId, boolean isAtLeastInstructor) {
        // get all complaints which belong to the exercise
        List<Complaint> complaints = complaintRepository.getAllComplaintsByExerciseIdAndComplaintType(exerciseId, ComplaintType.COMPLAINT);

        if (!isAtLeastInstructor) {
            complaints = complaints.stream().filter(complaint -> !userRepository.getUser().equals(complaint.getResult().getAssessor())).toList();
        }

        return getSubmissionsWithComplaintsFromComplaints(complaints);
    }

    /**
     * This method gets all more feature requests of an exercise and returns them together with their corresponding submission in a DTO
     *
     * @param exerciseId the exerciseId of the exercise of which the complaints are fetched
     * @return a list of DTOs containing a complaint and its submission
     */
    public List<SubmissionWithComplaintDTO> getSubmissionsWithMoreFeedbackRequestsForExercise(Long exerciseId) {
        // get all requests which belong to the exercise
        List<Complaint> requests = complaintRepository.getAllComplaintsByExerciseIdAndComplaintType(exerciseId, ComplaintType.MORE_FEEDBACK);

        requests = requests.stream().filter(complaint -> complaint.getResult().getAssessor() == null || complaint.getResult().getAssessor().equals(userRepository.getUser()))
                .toList();

        return getSubmissionsWithComplaintsFromComplaints(requests);
    }

    /**
     * Splits a list of complaints into a DTO containing the corresponding complaint and its submission
     *
     * @param complaints the list of complaints that should be split
     * @return the list of DTOs
     */
    private List<SubmissionWithComplaintDTO> getSubmissionsWithComplaintsFromComplaints(List<Complaint> complaints) {
        List<SubmissionWithComplaintDTO> submissionWithComplaintDTOs = new ArrayList<>();

        if (!complaints.isEmpty()) {
            var complaintMap = complaints.stream().collect(Collectors.toMap(complaint -> complaint.getResult().getId(), value -> value));
            // get the ids of all results which have a complaint, and with those fetch all their submissions
            List<Long> submissionIds = complaints.stream().map(complaint -> complaint.getResult().getSubmission().getId()).toList();
            List<Submission> submissions = List.of();
            if (!submissionIds.isEmpty()) {
                // avoid the database query if the list is empty to prevent performance issues
                submissions = submissionRepository.findBySubmissionIdsWithEagerResults(submissionIds);
            }

            // add each submission with its complaint to the DTO
            submissions.forEach(submission -> {
                Result complainedResult = submission.getResultWithComplaint();
                if (complainedResult == null) {
                    return;
                }
                submission.setResults(submission.getNonAthenaResults());
                Complaint complaintOfSubmission = complaintMap.get(complainedResult.getId());
                prepareComplaintAndSubmission(complaintOfSubmission, submission);
                submissionWithComplaintDTOs.add(new SubmissionWithComplaintDTO(submission, complaintOfSubmission));
            });
        }

        return submissionWithComplaintDTOs;
    }

    /**
     * Helper method to prepare the complaint for the client
     *
     * @param complaint the complaint which gets prepared
     */
    private void prepareComplaintAndSubmission(Complaint complaint, Submission submission) {
        StudentParticipation studentParticipation = (StudentParticipation) complaint.getResult().getSubmission().getParticipation();
        studentParticipation.setParticipant(null);
        studentParticipation.setExercise(null);
        complaint.setParticipant(null);

        StudentParticipation submissionsParticipation = (StudentParticipation) submission.getParticipation();
        submissionsParticipation.setParticipant(null);
        submissionsParticipation.setExercise(null);
    }

    /**
     * Search for all submissions fitting a {@link SearchTermPageableSearchDTO search query}. The result is paged,
     * meaning that there is only a predefined portion of the result returned to the user, so that the server doesn't
     * have to send hundreds/thousands of submissions if there are that many in Artemis.
     *
     * @param search     DTO containing the search term and information required for pagination and sorting
     * @param exerciseId Id of the exercise the submissions belongs to
     * @return A wrapper object containing a list of all found submissions and the total number of pages
     */
    public SearchResultPageDTO<Submission> getSubmissionsOnPageWithSize(SearchTermPageableSearchDTO<String> search, Long exerciseId) {
        final var pageable = PageUtil.createDefaultPageRequest(search, PageUtil.ColumnMapping.STUDENT_PARTICIPATION);
        String searchTerm = search.getSearchTerm();
        Page<StudentParticipation> studentParticipationPage = studentParticipationRepository.findAllWithEagerSubmissionsAndResultsByExerciseId(exerciseId, searchTerm, pageable);

        var latestSubmissions = studentParticipationPage.getContent().stream().map(Participation::findLatestSubmission).filter(Optional::isPresent).map(Optional::get).toList();
        final Page<Submission> submissionPage = new PageImpl<>(latestSubmissions, pageable, latestSubmissions.size());
        return new SearchResultPageDTO<>(submissionPage.getContent(), studentParticipationPage.getTotalPages());
    }
}
