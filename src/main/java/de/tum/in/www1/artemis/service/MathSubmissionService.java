package de.tum.in.www1.artemis.service;

import java.time.ZonedDateTime;
import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import de.tum.in.www1.artemis.domain.MathExercise;
import de.tum.in.www1.artemis.domain.MathSubmission;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.enumeration.InitializationState;
import de.tum.in.www1.artemis.domain.enumeration.SubmissionType;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.service.connectors.athena.AthenaSubmissionSelectionService;
import de.tum.in.www1.artemis.service.exam.ExamDateService;
import de.tum.in.www1.artemis.web.rest.errors.AccessForbiddenException;

@Service
public class MathSubmissionService extends SubmissionService {

    private final Logger log = LoggerFactory.getLogger(MathSubmissionService.class);

    private final MathSubmissionRepository mathSubmissionRepository;

    private final SubmissionVersionService submissionVersionService;

    private final ExerciseDateService exerciseDateService;

    public MathSubmissionService(MathSubmissionRepository mathSubmissionRepository, SubmissionRepository submissionRepository,
            StudentParticipationRepository studentParticipationRepository, ParticipationService participationService, ResultRepository resultRepository,
            UserRepository userRepository, AuthorizationCheckService authCheckService, SubmissionVersionService submissionVersionService, FeedbackRepository feedbackRepository,
            ExamDateService examDateService, ExerciseDateService exerciseDateService, CourseRepository courseRepository, ParticipationRepository participationRepository,
            ComplaintRepository complaintRepository, FeedbackService feedbackService, Optional<AthenaSubmissionSelectionService> athenaSubmissionSelectionService) {
        super(submissionRepository, userRepository, authCheckService, resultRepository, studentParticipationRepository, participationService, feedbackRepository, examDateService,
                exerciseDateService, courseRepository, participationRepository, complaintRepository, feedbackService, athenaSubmissionSelectionService);
        this.mathSubmissionRepository = mathSubmissionRepository;
        this.submissionVersionService = submissionVersionService;
        this.exerciseDateService = exerciseDateService;
    }

    /**
     * Handles math submissions sent from the client and saves them in the database.
     *
     * @param mathSubmission the math submission that should be saved
     * @param exercise       the corresponding math exercise
     * @param user           the user who initiated the save/submission
     * @return the saved math submission
     */
    public MathSubmission handleMathSubmission(MathSubmission mathSubmission, MathExercise exercise, User user) {
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
            mathSubmission.setSubmitted(true);
        }
        mathSubmission = save(mathSubmission, participation, exercise, user);
        return mathSubmission;
    }

    /**
     * Saves the given submission. Is used for creating and updating math submissions.
     *
     * @param mathSubmission the submission that should be saved
     * @param participation  the participation the submission belongs to
     * @param mathExercise   the exercise the submission belongs to
     * @param user           the user who initiated the save
     * @return the mathSubmission entity that was saved to the database
     */
    private MathSubmission save(MathSubmission mathSubmission, StudentParticipation participation, MathExercise mathExercise, User user) {
        // update submission properties
        mathSubmission.setSubmissionDate(ZonedDateTime.now());
        mathSubmission.setType(SubmissionType.MANUAL);
        participation.addSubmission(mathSubmission);

        if (participation.getInitializationState() != InitializationState.FINISHED) {
            participation.setInitializationState(InitializationState.FINISHED);
            studentParticipationRepository.save(participation);
        }

        // remove result from submission (in the unlikely case it is passed here), so that students cannot inject a result
        mathSubmission.setResults(new ArrayList<>());
        mathSubmission = mathSubmissionRepository.save(mathSubmission);

        // versioning of submission
        try {
            if (mathExercise.isTeamMode()) {
                submissionVersionService.saveVersionForTeam(mathSubmission, user);
            }
            else if (mathExercise.isExamExercise()) {
                submissionVersionService.saveVersionForIndividual(mathSubmission, user);
            }
        }
        catch (Exception ex) {
            log.error("Math submission version could not be saved", ex);
        }

        return mathSubmission;
    }

    /**
     * Given an exercise id, find a random math submission for that exercise which still doesn't have any manual result. No manual result means that no user has started an
     * assessment for the corresponding submission yet.
     *
     * @param mathExercise        the exercise for which we want to retrieve a submission without manual result
     * @param skipAssessmentQueue skip using the assessment queue and do NOT optimize the assessment order (default: false)
     * @param examMode            flag to determine if test runs should be removed. This should be set to true for exam exercises
     * @param correctionRound     - the correction round we want our submission to have results for
     * @return a mathSubmission without any manual result or an empty Optional if no submission without manual result could be found
     */
    public Optional<MathSubmission> getRandomMathSubmissionEligibleForNewAssessment(MathExercise mathExercise, boolean skipAssessmentQueue, boolean examMode, int correctionRound) {
        return super.getRandomAssessableSubmission(mathExercise, skipAssessmentQueue, examMode, correctionRound,
                mathSubmissionRepository::findByIdWithEagerParticipationExerciseResultAssessor);
    }

    /**
     * Lock a given math submission that still needs to be assessed to prevent other tutors from receiving and assessing it.
     *
     * @param mathSubmission  mathSubmission to be locked
     * @param correctionRound get submission with results in the correction round
     */
    public void lockMathSubmissionToBeAssessed(MathSubmission mathSubmission, int correctionRound) {
        lockSubmission(mathSubmission, correctionRound);
    }
}
