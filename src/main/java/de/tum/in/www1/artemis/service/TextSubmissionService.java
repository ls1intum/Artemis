package de.tum.in.www1.artemis.service;

import static java.util.stream.Collectors.*;

import java.security.Principal;
import java.time.ZonedDateTime;
import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.InitializationState;
import de.tum.in.www1.artemis.domain.enumeration.Language;
import de.tum.in.www1.artemis.domain.enumeration.SubmissionType;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

@Service
public class TextSubmissionService extends SubmissionService {

    private final Logger log = LoggerFactory.getLogger(TextSubmissionService.class);

    private final TextSubmissionRepository textSubmissionRepository;

    private final TextClusterRepository textClusterRepository;

    private final Optional<TextAssessmentQueueService> textAssessmentQueueService;

    private final SubmissionVersionService submissionVersionService;

    public TextSubmissionService(TextSubmissionRepository textSubmissionRepository, TextClusterRepository textClusterRepository, SubmissionRepository submissionRepository,
            StudentParticipationRepository studentParticipationRepository, ParticipationService participationService, ResultRepository resultRepository, UserService userService,
            Optional<TextAssessmentQueueService> textAssessmentQueueService, AuthorizationCheckService authCheckService, SubmissionVersionService submissionVersionService,
            CourseService courseService, ExamService examService) {
        super(submissionRepository, userService, authCheckService, courseService, resultRepository, examService, studentParticipationRepository, participationService);
        this.textSubmissionRepository = textSubmissionRepository;
        this.textClusterRepository = textClusterRepository;
        this.resultRepository = resultRepository;
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
        if (textExercise.hasCourse()) {
            if (dueDate != null && participation.getInitializationDate().isBefore(dueDate) && dueDate.isBefore(ZonedDateTime.now())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN);
            }
        }

        if (Boolean.TRUE.equals(textSubmission.isExampleSubmission())) {
            textSubmission = save(textSubmission);
        }
        else {
            // NOTE: from now on we always set submitted to true to prevent problems here!
            textSubmission.setSubmitted(true);
            textSubmission = save(textSubmission, participation, textExercise, principal);
        }
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
        textSubmission = textSubmissionRepository.save(textSubmission);

        // versioning of submission
        try {
            if (textExercise.isTeamMode()) {
                submissionVersionService.saveVersionForTeam(textSubmission, principal.getName());
            }
            else {
                submissionVersionService.saveVersionForIndividual(textSubmission, principal.getName());
            }
        }
        catch (Exception ex) {
            log.error("Text submission version could not be saved: " + ex);
        }

        participation.addSubmissions(textSubmission);
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
     * The same as `save()`, but without participation, is used by example submission, which aren't linked to any participation
     *
     * @param textSubmission the submission to notifyCompass
     * @return the textSubmission entity
     */
    public TextSubmission save(TextSubmission textSubmission) {
        textSubmission.setSubmissionDate(ZonedDateTime.now());
        textSubmission.setType(SubmissionType.MANUAL);

        // Rebuild connection between result and submission, if it has been lost, because hibernate needs it
        if (textSubmission.getResult() != null && textSubmission.getResult().getSubmission() == null) {
            textSubmission.getResult().setSubmission(textSubmission);
        }

        textSubmission = textSubmissionRepository.save(textSubmission);

        return textSubmission;
    }

    /**
     * Given an exercise id, find a random text submission for that exercise which still doesn't have any manual result. No manual result means that no user has started an
     * assessment for the corresponding submission yet.
     *
     * @param textExercise the exercise for which we want to retrieve a submission without manual result
     * @param examMode flag to determine if test runs should be removed. This should be set to true for exam exercises
     * @return a textSubmission without any manual result or an empty Optional if no submission without manual result could be found
     */
    public Optional<TextSubmission> getRandomTextSubmissionEligibleForNewAssessment(TextExercise textExercise, boolean examMode) {
        return getRandomTextSubmissionEligibleForNewAssessment(textExercise, false, examMode);
    }

    /**
     * Given an exercise id, find a random text submission for that exercise which still doesn't have any manual result. No manual result means that no user has started an
     * assessment for the corresponding submission yet.
     *
     * @param textExercise the exercise for which we want to retrieve a submission without manual result
     * @param skipAssessmentQueue skip using the assessment queue and do NOT optimize the assessment order (default: false)
     * @param examMode flag to determine if test runs should be removed. This should be set to true for exam exercises
     * @return a textSubmission without any manual result or an empty Optional if no submission without manual result could be found
     */
    public Optional<TextSubmission> getRandomTextSubmissionEligibleForNewAssessment(TextExercise textExercise, boolean skipAssessmentQueue, boolean examMode) {
        if (textExercise.isAutomaticAssessmentEnabled() && textAssessmentQueueService.isPresent() && !skipAssessmentQueue) {
            return textAssessmentQueueService.get().getProposedTextSubmission(textExercise);
        }
        var submissionWithoutResult = super.getRandomSubmissionEligibleForNewAssessment(textExercise, examMode);
        if (submissionWithoutResult.isPresent()) {
            TextSubmission textSubmission = (TextSubmission) submissionWithoutResult.get();
            return Optional.of(textSubmission);
        }
        return Optional.empty();
    }

    /**
     * Return all TextSubmission which are the latest TextSubmission of a Participation and doesn't have a Result so far
     * The corresponding TextBlocks and Participations are retrieved from the database
     * @param exercise Exercise for which all assessed submissions should be retrieved
     * @return List of all TextSubmission which aren't assessed at the Moment, but need assessment in the future.
     *
     */
    public List<TextSubmission> getAllOpenTextSubmissions(TextExercise exercise) {
        final List<TextSubmission> submissions = textSubmissionRepository.findByParticipation_ExerciseIdAndResultIsNullAndSubmittedIsTrue(exercise.getId());

        final Set<Long> clusterIds = submissions.stream().flatMap(submission -> submission.getBlocks().stream()).map(TextBlock::getCluster).filter(Objects::nonNull)
                .map(TextCluster::getId).collect(toSet());

        // To prevent lazy loading many elements later on, we fetch all clusters with text blocks here.
        final Map<Long, TextCluster> textClusterMap = textClusterRepository.findAllByIdsWithEagerTextBlocks(clusterIds).stream()
                .collect(toMap(TextCluster::getId, textCluster -> textCluster));

        // link up clusters with eager blocks
        submissions.stream().flatMap(submission -> submission.getBlocks().stream()).forEach(textBlock -> {
            if (textBlock.getCluster() != null) {
                textBlock.setCluster(textClusterMap.get(textBlock.getCluster().getId()));
            }
        });

        return submissions.stream().filter(tS -> tS.getParticipation().findLatestSubmission().isPresent() && tS == tS.getParticipation().findLatestSubmission().get())
                .collect(toList());
    }

    /**
     * Given an exercise id and a tutor id, it returns all the text submissions where the tutor has a result associated
     *
     * @param exerciseId - the id of the exercise we are looking for
     * @param tutor - the tutor we are interested in
     * @param examMode - flag should be set to ignore the test run submissions
     * @return a list of text Submissions
     */
    public List<TextSubmission> getAllTextSubmissionsAssessedByTutorWithForExercise(Long exerciseId, User tutor, boolean examMode) {
        var submissions = super.getAllSubmissionsAssessedByTutorForExercise(exerciseId, tutor, examMode);
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
        List<StudentParticipation> participations;
        if (examMode) {
            participations = studentParticipationRepository.findAllWithEagerSubmissionsAndEagerResultsAndEagerAssessorByExerciseIdIgnoreTestRuns(exerciseId);
        }
        else {
            participations = studentParticipationRepository.findAllWithEagerSubmissionsAndEagerResultsAndEagerAssessorByExerciseId(exerciseId);
        }

        List<TextSubmission> textSubmissions = new ArrayList<>();

        for (StudentParticipation participation : participations) {
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

    public List<TextSubmission> getTextSubmissionsWithTextBlocksByExerciseId(Long exerciseId) {
        return textSubmissionRepository.findByParticipation_ExerciseIdAndSubmittedIsTrue(exerciseId);
    }

    public TextSubmission getTextSubmissionWithResultAndTextBlocksAndFeedbackByResultId(Long resultId) {
        return textSubmissionRepository.findWithEagerResultAndTextBlocksAndFeedbackByResult_Id(resultId)
                .orElseThrow(() -> new BadRequestAlertException("No text submission found for the given result.", "textSubmission", "textSubmissionNotFound"));
    }

    public List<TextSubmission> getTextSubmissionsWithTextBlocksByExerciseIdAndLanguage(Long exerciseId, Language language) {
        return textSubmissionRepository.findByParticipation_ExerciseIdAndSubmittedIsTrueAndLanguage(exerciseId, language);
    }

    /**
     * Find a text submission of the given exercise that still needs to be assessed and lock it to prevent other tutors from receiving and assessing it.
     *
     * @param textExercise the exercise the submission should belong to
     * @param removeTestRunParticipations flag to determine if test runs should be removed. This should be set to true for exam exercises
     * @return a locked modeling submission that needs an assessment
     */
    public TextSubmission findAndLockTextSubmissionToBeAssessed(TextExercise textExercise, boolean removeTestRunParticipations) {
        TextSubmission textSubmission = getRandomTextSubmissionEligibleForNewAssessment(textExercise, removeTestRunParticipations)
                .orElseThrow(() -> new EntityNotFoundException("Text submission for exercise " + textExercise.getId() + " could not be found"));
        lockSubmission(textSubmission);
        return textSubmission;
    }

    public TextSubmission findOneWithEagerResultFeedbackAndTextBlocks(Long submissionId) {
        return textSubmissionRepository.findByIdWithEagerResultFeedbackAndTextBlocks(submissionId)
                .orElseThrow(() -> new EntityNotFoundException("Text submission with id \"" + submissionId + "\" does not exist"));
    }
}
