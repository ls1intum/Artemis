package de.tum.cit.aet.artemis.text.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import de.tum.cit.aet.artemis.assessment.repository.ComplaintRepository;
import de.tum.cit.aet.artemis.assessment.repository.FeedbackRepository;
import de.tum.cit.aet.artemis.assessment.repository.ResultRepository;
import de.tum.cit.aet.artemis.assessment.service.FeedbackService;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.repository.CourseRepository;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.core.service.connectors.athena.AthenaSubmissionSelectionService;
import de.tum.cit.aet.artemis.exam.service.ExamDateService;
import de.tum.cit.aet.artemis.exercise.domain.InitializationState;
import de.tum.cit.aet.artemis.exercise.domain.SubmissionType;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.repository.ParticipationRepository;
import de.tum.cit.aet.artemis.exercise.repository.StudentParticipationRepository;
import de.tum.cit.aet.artemis.exercise.repository.SubmissionRepository;
import de.tum.cit.aet.artemis.exercise.service.ExerciseDateService;
import de.tum.cit.aet.artemis.service.ParticipationService;
import de.tum.cit.aet.artemis.service.SubmissionService;
import de.tum.cit.aet.artemis.service.SubmissionVersionService;
import de.tum.cit.aet.artemis.text.domain.TextExercise;
import de.tum.cit.aet.artemis.text.domain.TextSubmission;
import de.tum.cit.aet.artemis.text.repository.TextSubmissionRepository;
import de.tum.cit.aet.artemis.core.exception.AccessForbiddenException;

@Profile(PROFILE_CORE)
@Service
public class TextSubmissionService extends SubmissionService {

    private static final Logger log = LoggerFactory.getLogger(TextSubmissionService.class);

    private final TextSubmissionRepository textSubmissionRepository;

    private final SubmissionVersionService submissionVersionService;

    private final ExerciseDateService exerciseDateService;

    public TextSubmissionService(TextSubmissionRepository textSubmissionRepository, SubmissionRepository submissionRepository,
            StudentParticipationRepository studentParticipationRepository, ParticipationService participationService, ResultRepository resultRepository,
            UserRepository userRepository, AuthorizationCheckService authCheckService, SubmissionVersionService submissionVersionService, FeedbackRepository feedbackRepository,
            ExamDateService examDateService, ExerciseDateService exerciseDateService, CourseRepository courseRepository, ParticipationRepository participationRepository,
            ComplaintRepository complaintRepository, FeedbackService feedbackService, Optional<AthenaSubmissionSelectionService> athenaSubmissionSelectionService) {
        super(submissionRepository, userRepository, authCheckService, resultRepository, studentParticipationRepository, participationService, feedbackRepository, examDateService,
                exerciseDateService, courseRepository, participationRepository, complaintRepository, feedbackService, athenaSubmissionSelectionService);
        this.textSubmissionRepository = textSubmissionRepository;
        this.submissionVersionService = submissionVersionService;
        this.exerciseDateService = exerciseDateService;
    }

    /**
     * Handles text submissions sent from the client and saves them in the database.
     *
     * @param textSubmission the text submission that should be saved
     * @param exercise       the corresponding text exercise
     * @param user           the user who initiated the save/submission
     * @return the saved text submission
     */
    public TextSubmission handleTextSubmission(TextSubmission textSubmission, TextExercise exercise, User user) {
        // Don't allow submissions after the due date (except if the exercise was started after the due date)
        final var optionalParticipation = participationService.findOneByExerciseAndStudentLoginWithEagerSubmissionsAnyState(exercise, user.getLogin());
        if (optionalParticipation.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.FAILED_DEPENDENCY, "No participation found for " + user.getLogin() + " in exercise " + exercise.getId());
        }
        final var participation = optionalParticipation.get();
        final var dueDate = ExerciseDateService.getDueDate(participation);
        // Important: for exam exercises, we should NOT check the exercise due date, we only check if for course exercises
        if (dueDate.isPresent() && exerciseDateService.isAfterDueDate(participation) && participation.getInitializationDate().isBefore(dueDate.get())) {
            throw new AccessForbiddenException();
        }

        // NOTE: from now on we always set submitted to true to prevent problems here! Except for late submissions of course exercises to prevent issues in auto-save
        if (exercise.isExamExercise() || exerciseDateService.isBeforeDueDate(participation)) {
            textSubmission.setSubmitted(true);
        }

        // if athena results are present than create new submission on submit
        if (!textSubmission.getResults().isEmpty()) {
            log.debug("Creating a new submission due to Athena results for user: {}", user.getLogin());
            textSubmission.setId(null);
        }

        textSubmission = save(textSubmission, participation, exercise, user);
        return textSubmission;
    }

    /**
     * Saves the given submission. Is used for creating and updating text submissions.
     *
     * @param textSubmission the submission that should be saved
     * @param participation  the participation the submission belongs to
     * @param textExercise   the exercise the submission belongs to
     * @param user           the user who initiated the save
     * @return the textSubmission entity that was saved to the database
     */
    private TextSubmission save(TextSubmission textSubmission, StudentParticipation participation, TextExercise textExercise, User user) {
        // update submission properties
        textSubmission.setSubmissionDate(ZonedDateTime.now());
        textSubmission.setType(SubmissionType.MANUAL);
        participation.addSubmission(textSubmission);

        if (participation.getInitializationState() != InitializationState.FINISHED) {
            participation.setInitializationState(InitializationState.FINISHED);
            studentParticipationRepository.save(participation);
        }
        // remove result from submission (in the unlikely case it is passed here), so that students cannot inject a result
        textSubmission.setResults(new ArrayList<>());
        textSubmission = textSubmissionRepository.save(textSubmission);

        // versioning of submission
        try {
            if (textExercise.isTeamMode()) {
                submissionVersionService.saveVersionForTeam(textSubmission, user);
            }
            else if (textExercise.isExamExercise()) {
                submissionVersionService.saveVersionForIndividual(textSubmission, user);
            }
        }
        catch (Exception ex) {
            log.error("Text submission version could not be saved", ex);
        }

        return textSubmission;
    }

    /**
     * Given an exercise id, find a random text submission for that exercise which still doesn't have any manual result. No manual result means that no user has started an
     * assessment for the corresponding submission yet.
     *
     * @param textExercise        the exercise for which we want to retrieve a submission without manual result
     * @param skipAssessmentQueue skip using the assessment queue and do NOT optimize the assessment order (default: false)
     * @param examMode            flag to determine if test runs should be removed. This should be set to true for exam exercises
     * @param correctionRound     - the correction round we want our submission to have results for
     * @return a textSubmission without any manual result or an empty Optional if no submission without manual result could be found
     */
    public Optional<TextSubmission> getRandomTextSubmissionEligibleForNewAssessment(TextExercise textExercise, boolean skipAssessmentQueue, boolean examMode, int correctionRound) {
        return super.getRandomAssessableSubmission(textExercise, skipAssessmentQueue, examMode, correctionRound,
                textSubmissionRepository::findWithEagerParticipationExerciseResultAssessorById);
    }

    /**
     * Lock a given text submission that still needs to be assessed to prevent other tutors from receiving and assessing it.
     *
     * @param textSubmission  textSubmission to be locked
     * @param correctionRound get submission with results in the correction round
     */
    public void lockTextSubmissionToBeAssessed(TextSubmission textSubmission, int correctionRound) {
        lockSubmission(textSubmission, correctionRound);
    }

    public TextSubmission findOneWithEagerResultFeedbackAndTextBlocks(Long submissionId) {
        return textSubmissionRepository.findWithEagerResultsAndFeedbackAndTextBlocksById(submissionId).orElseThrow();
    }
}
