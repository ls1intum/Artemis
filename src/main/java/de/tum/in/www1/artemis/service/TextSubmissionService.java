package de.tum.in.www1.artemis.service;

import static java.util.stream.Collectors.toList;

import java.security.Principal;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.InitializationState;
import de.tum.in.www1.artemis.domain.enumeration.SubmissionType;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.service.exam.ExamDateService;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

@Service
public class TextSubmissionService extends SubmissionService {

    private final Logger log = LoggerFactory.getLogger(TextSubmissionService.class);

    private final TextSubmissionRepository textSubmissionRepository;

    private final Optional<TextAssessmentQueueService> textAssessmentQueueService;

    private final SubmissionVersionService submissionVersionService;

    public TextSubmissionService(TextSubmissionRepository textSubmissionRepository, SubmissionRepository submissionRepository,
            StudentParticipationRepository studentParticipationRepository, ParticipationService participationService, ResultRepository resultRepository,
            UserRepository userRepository, Optional<TextAssessmentQueueService> textAssessmentQueueService, AuthorizationCheckService authCheckService,
            SubmissionVersionService submissionVersionService, FeedbackRepository feedbackRepository, ExamDateService examDateService, CourseRepository courseRepository,
            ParticipationRepository participationRepository, ComplaintRepository complaintRepository) {
        super(submissionRepository, userRepository, authCheckService, resultRepository, studentParticipationRepository, participationService, feedbackRepository, examDateService,
                courseRepository, participationRepository, complaintRepository);
        this.textSubmissionRepository = textSubmissionRepository;
        this.textAssessmentQueueService = textAssessmentQueueService;
        this.submissionVersionService = submissionVersionService;
    }

    /**
     * Handles text submissions sent from the client and saves them in the database.
     *
     * @param textSubmission the text submission that should be saved
     * @param textExercise   the corresponding text exercise
     * @param principal      the user principal
     * @return the saved text submission
     */
    public TextSubmission handleTextSubmission(TextSubmission textSubmission, TextExercise textExercise, Principal principal) {
        // Don't allow submissions after the due date (except if the exercise was started after the due date)
        final var dueDate = textExercise.getDueDate();
        final var optionalParticipation = participationService.findOneByExerciseAndStudentLoginWithEagerSubmissionsAnyState(textExercise, principal.getName());
        if (optionalParticipation.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.FAILED_DEPENDENCY, "No participation found for " + principal.getName() + " in exercise " + textExercise.getId());
        }
        final var participation = optionalParticipation.get();
        // Important: for exam exercises, we should NOT check the exercise due date, we only check if for course exercises
        if (textExercise.isCourseExercise()) {
            if (dueDate != null && participation.getInitializationDate().isBefore(dueDate) && dueDate.isBefore(ZonedDateTime.now())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN);
            }
        }

        // NOTE: from now on we always set submitted to true to prevent problems here! Except for late submissions of course exercises to prevent issues in auto-save
        if (textExercise.isExamExercise() || !textExercise.isEnded()) {
            textSubmission.setSubmitted(true);
        }
        textSubmission = save(textSubmission, participation, textExercise, principal);
        return textSubmission;
    }

    /**
     * Saves the given submission. Is used for creating and updating text submissions.
     *
     * @param textSubmission the submission that should be saved
     * @param participation  the participation the submission belongs to
     * @param textExercise   the exercise the submission belongs to
     * @param principal      the principal of the user
     * @return the textSubmission entity that was saved to the database
     */
    public TextSubmission save(TextSubmission textSubmission, StudentParticipation participation, TextExercise textExercise, Principal principal) {
        // update submission properties
        textSubmission.setSubmissionDate(ZonedDateTime.now());
        textSubmission.setType(SubmissionType.MANUAL);
        textSubmission.setParticipation(participation);

        // remove result from submission (in the unlikely case it is passed here), so that students cannot inject a result
        textSubmission.setResults(new ArrayList<>());
        textSubmission = textSubmissionRepository.save(textSubmission);

        // versioning of submission
        try {
            if (textExercise.isTeamMode()) {
                submissionVersionService.saveVersionForTeam(textSubmission, principal.getName());
            }
            else if (textExercise.isExamExercise()) {
                submissionVersionService.saveVersionForIndividual(textSubmission, principal.getName());
            }
        }
        catch (Exception ex) {
            log.error("Text submission version could not be saved", ex);
        }

        participation.addSubmission(textSubmission);
        participation.setInitializationState(InitializationState.FINISHED);
        StudentParticipation savedParticipation = studentParticipationRepository.save(participation);
        if (textSubmission.getId() == null) {
            Optional<Submission> optionalTextSubmission = savedParticipation.findLatestSubmission();
            if (optionalTextSubmission.isPresent()) {
                textSubmission = (TextSubmission) optionalTextSubmission.get();
            }
        }

        return textSubmission;
    }

    /**
     * Given an exercise id, find a random text submission for that exercise which still doesn't have any manual result. No manual result means that no user has started an
     * assessment for the corresponding submission yet.
     *
     * @param textExercise the exercise for which we want to retrieve a submission without manual result
     * @param correctionRound - the correction round we want our submission to have results for
     * @param examMode flag to determine if test runs should be ignored. This should be set to true for exam exercises
     * @return a textSubmission without any manual result or an empty Optional if no submission without manual result could be found
     */
    public Optional<TextSubmission> getRandomTextSubmissionEligibleForNewAssessment(TextExercise textExercise, boolean examMode, int correctionRound) {
        return getRandomTextSubmissionEligibleForNewAssessment(textExercise, false, examMode, correctionRound);
    }

    /**
     * Given an exercise id, find a random text submission for that exercise which still doesn't have any manual result. No manual result means that no user has started an
     * assessment for the corresponding submission yet.
     *
     * @param textExercise the exercise for which we want to retrieve a submission without manual result
     * @param correctionRound - the correction round we want our submission to have results for
     * @param skipAssessmentQueue skip using the assessment queue and do NOT optimize the assessment order (default: false)
     * @param examMode flag to determine if test runs should be removed. This should be set to true for exam exercises
     * @return a textSubmission without any manual result or an empty Optional if no submission without manual result could be found
     */
    public Optional<TextSubmission> getRandomTextSubmissionEligibleForNewAssessment(TextExercise textExercise, boolean skipAssessmentQueue, boolean examMode, int correctionRound) {
        if (textExercise.isAutomaticAssessmentEnabled() && textAssessmentQueueService.isPresent() && !skipAssessmentQueue) {
            return textAssessmentQueueService.get().getProposedTextSubmission(textExercise);
        }
        var submissionWithoutResult = super.getRandomSubmissionEligibleForNewAssessment(textExercise, examMode, correctionRound);
        if (submissionWithoutResult.isPresent()) {
            TextSubmission textSubmission = (TextSubmission) submissionWithoutResult.get();
            return Optional.of(textSubmission);
        }
        return Optional.empty();
    }

    /**
     * Given an exercise id and a tutor id, it returns all the text submissions where the tutor has a result associated
     *
     * @param exerciseId - the id of the exercise we are looking for
     * @param correctionRound - the correction round we want our submission to have results for
     * @param tutor - the tutor we are interested in
     * @param examMode - flag should be set to ignore the test run submissions
     * @return a list of text Submissions
     */
    public List<TextSubmission> getAllTextSubmissionsAssessedByTutorWithForExercise(Long exerciseId, User tutor, boolean examMode, int correctionRound) {
        var submissions = super.getAllSubmissionsAssessedByTutorForCorrectionRoundAndExercise(exerciseId, tutor, examMode, correctionRound);
        return submissions.stream().map(submission -> (TextSubmission) submission).collect(toList());
    }

    /**
     * Given an exerciseId, returns all the submissions for that exercise, including their results. Submissions can be filtered to include only already submitted submissions
     *
     * @param exerciseId    - the id of the exercise we are interested into
     * @param submittedOnly - if true, it returns only submission with submitted flag set to true
     * @param examMode - set flag to ignore test run submissions
     * @return a list of text submissions for the given exercise id
     */
    public List<TextSubmission> getTextSubmissionsByExerciseId(Long exerciseId, boolean submittedOnly, boolean examMode) {
        // Instructors assume to see all submissions on the submissions page independent whether they already have results or not.
        List<StudentParticipation> participations;
        if (examMode) {
            participations = studentParticipationRepository.findAllWithEagerSubmissionsAndEagerResultsAndEagerAssessorByExerciseIdIgnoreTestRuns(exerciseId);
        }
        else {
            participations = studentParticipationRepository.findAllWithEagerSubmissionsAndEagerResultsAndEagerAssessorByExerciseId(exerciseId);
        }

        List<TextSubmission> textSubmissions = new ArrayList<>();

        for (StudentParticipation participation : participations) {
            // We don't have illegal submissions for text exercises
            Optional<Submission> optionalTextSubmission = participation.findLatestSubmission();

            if (optionalTextSubmission.isEmpty()) {
                continue;
            }

            if (submittedOnly && !Boolean.TRUE.equals(optionalTextSubmission.get().isSubmitted())) {
                continue;
            }

            textSubmissions.add((TextSubmission) optionalTextSubmission.get());
        }
        return textSubmissions;
    }

    /**
     * Find a text submission of the given exercise that still needs to be assessed and lock it to prevent other tutors from receiving and assessing it.
     *
     * @param textExercise the exercise the submission should belong to
     * @param correctionRound get submission with results in the correction round
     * @param ignoreTestRunParticipations flag to determine if test runs should be removed. This should be set to true for exam exercises
     * @return a locked modeling submission that needs an assessment
     */
    public TextSubmission findAndLockTextSubmissionToBeAssessed(TextExercise textExercise, boolean ignoreTestRunParticipations, int correctionRound) {
        TextSubmission textSubmission = getRandomTextSubmissionEligibleForNewAssessment(textExercise, ignoreTestRunParticipations, correctionRound)
                .orElseThrow(() -> new EntityNotFoundException("Text submission for exercise " + textExercise.getId() + " could not be found"));
        lockSubmission(textSubmission, correctionRound);
        return textSubmission;
    }

    /**
     * Lock a given text submission that still needs to be assessed to prevent other tutors from receiving and assessing it.
     *
     * @param textSubmission textSubmission to be locked
     * @param correctionRound get submission with results in the correction round
     * @return a locked modeling submission that needs an assessment
     */
    public TextSubmission lockTextSubmissionToBeAssessed(TextSubmission textSubmission, int correctionRound) {
        lockSubmission(textSubmission, correctionRound);
        return textSubmission;
    }

    public TextSubmission findOneWithEagerResultFeedbackAndTextBlocks(Long submissionId) {
        return textSubmissionRepository.findWithEagerResultsAndFeedbackAndTextBlocksById(submissionId).get();
    }
}
