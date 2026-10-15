package de.tum.cit.aet.artemis.exercise.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.ZonedDateTime;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.core.exception.AccessForbiddenException;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.Submission;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;

/** Shared eligibility checks for selecting and starting manual assessments. */
@Profile(PROFILE_CORE)
@Lazy
@Service
public class SubmissionAssessmentEligibilityService {

    private final UserRepository userRepository;

    private final AuthorizationCheckService authCheckService;

    public SubmissionAssessmentEligibilityService(UserRepository userRepository, AuthorizationCheckService authCheckService) {
        this.userRepository = userRepository;
        this.authCheckService = authCheckService;
    }

    /**
     * Checks submission readiness, team ownership and distinct assessors across correction rounds.
     *
     * @param submission      the submission to assess
     * @param exercise        its exercise, including exam information
     * @param correctionRound the round to start
     * @return whether the current user may start this correction
     */
    public boolean canStartAssessment(Submission submission, Exercise exercise, int correctionRound) {
        var participation = submission.getParticipation();
        var user = userRepository.getUser();
        if (exercise.isTeamMode() && !authCheckService.isAtLeastInstructorForExercise(exercise)
                && (!(participation instanceof StudentParticipation student) || student.getTeam().filter(team -> team.isOwner(user)).isEmpty())) {
            return false;
        }
        boolean testRun = participation instanceof StudentParticipation student && student.isTestRun();
        if (!testRun && (!Boolean.TRUE.equals(submission.isSubmitted())
                || (!exercise.isExamExercise() && ExerciseDateService.getDueDate(participation).filter(date -> date.isAfter(ZonedDateTime.now())).isPresent()))) {
            return false;
        }
        var previousResult = correctionRound > 0 ? submission.getResultForCorrectionRound(correctionRound - 1) : null;
        return correctionRound == 0 || (previousResult != null && previousResult.getCompletionDate() != null && previousResult.getAssessor() != null
                && !previousResult.getAssessor().getId().equals(user.getId()));
    }

    /**
     * Rejects an ineligible new assessment before any result is created or copied.
     *
     * @param submission      the submission to assess
     * @param correctionRound the round to start
     */
    public void checkCanStartAssessmentElseThrow(Submission submission, int correctionRound) {
        if (!canStartAssessment(submission, submission.getParticipation().getExercise(), correctionRound)) {
            throw new AccessForbiddenException("This submission is not eligible for a new assessment by the current user.");
        }
    }
}
