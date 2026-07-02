package de.tum.cit.aet.artemis.math.service;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Optional;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.assessment.repository.ComplaintRepository;
import de.tum.cit.aet.artemis.assessment.repository.FeedbackRepository;
import de.tum.cit.aet.artemis.assessment.repository.ResultRepository;
import de.tum.cit.aet.artemis.assessment.service.FeedbackService;
import de.tum.cit.aet.artemis.athena.api.AthenaApi;
import de.tum.cit.aet.artemis.core.exception.AccessForbiddenException;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.course.repository.CourseRepository;
import de.tum.cit.aet.artemis.exam.api.ExamDateApi;
import de.tum.cit.aet.artemis.exercise.domain.InitializationState;
import de.tum.cit.aet.artemis.exercise.domain.SubmissionType;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.repository.ParticipationRepository;
import de.tum.cit.aet.artemis.exercise.repository.StudentParticipationRepository;
import de.tum.cit.aet.artemis.exercise.repository.SubmissionRepository;
import de.tum.cit.aet.artemis.exercise.service.ExerciseDateService;
import de.tum.cit.aet.artemis.exercise.service.ParticipationService;
import de.tum.cit.aet.artemis.exercise.service.SubmissionService;
import de.tum.cit.aet.artemis.math.config.MathEnabled;
import de.tum.cit.aet.artemis.math.domain.MathExercise;
import de.tum.cit.aet.artemis.math.domain.MathSubmission;
import de.tum.cit.aet.artemis.math.repository.MathSubmissionRepository;

/**
 * Service for handling student math submissions. Mirrors the non-programming submission lifecycle used by
 * {@code TextSubmissionService} / {@code ModelingSubmissionService}: it enforces the course due date and applies the
 * standard metadata/state changes (submission date, manual type, finished participation, no client-injected results).
 */
@Conditional(MathEnabled.class)
@Lazy
@Service
public class MathSubmissionService extends SubmissionService {

    private final MathSubmissionRepository mathSubmissionRepository;

    private final ExerciseDateService exerciseDateService;

    public MathSubmissionService(MathSubmissionRepository mathSubmissionRepository, SubmissionRepository submissionRepository,
            StudentParticipationRepository studentParticipationRepository, ParticipationService participationService, ResultRepository resultRepository,
            UserRepository userRepository, AuthorizationCheckService authCheckService, FeedbackRepository feedbackRepository, Optional<ExamDateApi> examDateApi,
            ExerciseDateService exerciseDateService, CourseRepository courseRepository, ParticipationRepository participationRepository, ComplaintRepository complaintRepository,
            FeedbackService feedbackService, Optional<AthenaApi> athenaApi) {
        super(submissionRepository, userRepository, authCheckService, resultRepository, studentParticipationRepository, participationService, feedbackRepository, examDateApi,
                exerciseDateService, courseRepository, participationRepository, complaintRepository, feedbackService, athenaApi);
        this.mathSubmissionRepository = mathSubmissionRepository;
        this.exerciseDateService = exerciseDateService;
    }

    /**
     * Handles a math submission sent from the client and saves it in the database, enforcing the course due date.
     *
     * @param mathSubmission the math submission that should be saved (its {@code submitted} flag is preserved: draft saves stay unsubmitted)
     * @param exercise       the corresponding math exercise
     * @param user           the user who initiated the save/submission
     * @return the saved math submission
     */
    public MathSubmission handleMathSubmission(MathSubmission mathSubmission, MathExercise exercise, User user) {
        final Optional<StudentParticipation> optionalParticipation = participationService.findOneByExerciseAndStudentLoginWithEagerSubmissionsAnyState(exercise, user.getLogin());
        if (optionalParticipation.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.FAILED_DEPENDENCY, "No participation found for " + user.getLogin() + " in exercise " + exercise.getId());
        }
        final StudentParticipation participation = optionalParticipation.get();
        final var dueDate = ExerciseDateService.getDueDate(participation);
        // Don't allow submissions after the due date (unless the participation was started after the due date)
        if (dueDate.isPresent() && exerciseDateService.isAfterDueDate(participation) && participation.getInitializationDate().isBefore(dueDate.get())) {
            throw new AccessForbiddenException();
        }

        return save(mathSubmission, participation);
    }

    /**
     * Saves the given submission, applying the standard non-programming submission metadata and participation state.
     *
     * @param mathSubmission the submission that should be saved
     * @param participation  the participation the submission belongs to
     * @return the saved math submission
     */
    private MathSubmission save(MathSubmission mathSubmission, StudentParticipation participation) {
        mathSubmission.setSubmissionDate(ZonedDateTime.now());
        mathSubmission.setType(SubmissionType.MANUAL);
        participation.addSubmission(mathSubmission);

        if (participation.getInitializationState() != InitializationState.FINISHED) {
            participation.setInitializationState(InitializationState.FINISHED);
            studentParticipationRepository.save(participation);
        }
        // remove any result the client may have injected so that students cannot self-assess
        mathSubmission.setResults(new ArrayList<>());

        return mathSubmissionRepository.save(mathSubmission);
    }
}
