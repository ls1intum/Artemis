package de.tum.cit.aet.artemis.exercise.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.ArrayList;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseType;
import de.tum.cit.aet.artemis.exercise.domain.Submission;
import de.tum.cit.aet.artemis.exercise.domain.participation.Participation;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;

/**
 * A service to handle participation filtering.
 */
@Lazy
@Service
@Profile(PROFILE_CORE)
public class ParticipationFilterService {

    private final SubmissionFilterService submissionFilterService;

    public ParticipationFilterService(SubmissionFilterService submissionFilterService) {
        this.submissionFilterService = submissionFilterService;
    }

    /**
     * Finds all participations of a student in the given exercise. For non-programming exercises, there should only be
     * at most one participation. For programming exercises, an additional practice participation can exist.
     *
     * @param participationsAcrossAllExercises all participations of the student in all exercises of the course
     * @param exercise                         the exercise for which we want the participations
     * @return the participations in the exercise
     */
    public Set<StudentParticipation> findStudentParticipationsInExercise(Set<StudentParticipation> participationsAcrossAllExercises, Exercise exercise) {
        // only consider participations in the given exercise
        if (participationsAcrossAllExercises == null || participationsAcrossAllExercises.isEmpty()) {
            return Set.of();
        }
        var participationsInExercise = participationsAcrossAllExercises.stream().filter(p -> Objects.equals(p.getExercise(), exercise)).collect(Collectors.toSet());

        if (participationsInExercise.isEmpty()) {
            return Set.of();
        }

        if (ExerciseType.PROGRAMMING.equals(exercise.getExerciseType()) || ExerciseType.QUIZ.equals(exercise.getExerciseType())) {
            return findStudentParticipationsForMultipleParticipationExercises(participationsInExercise);
        }
        else {
            return Set.of(findStudentParticipationForSingleParticipationExercises(participationsInExercise));
        }
    }

    /**
     * Filters the student participation for display in the student dashboard.
     * <p>
     * Ensures that sensitive information (e.g. the assessing tutor) is removed.
     * Additionally, this only keeps the latest submission and its most recent visible result.
     * For example, in case the exercise uses a manual assessment and the assessment publication date has not yet passed,
     * only the last automatic result is kept since the newer one should not yet be visible to the student.
     *
     * @param participation the participation that contains the exercise and should be filtered
     * @param isStudent     used to determine if further filtering is needed.
     */
    public void filterParticipationForCourseDashboard(StudentParticipation participation, boolean isStudent) {
        Optional<Submission> optionalSubmission = submissionFilterService.getLatestSubmissionWithResult(participation.getSubmissions(), false);

        Set<Result> results = Set.of();

        if (optionalSubmission.isPresent()) {
            Submission submission = optionalSubmission.get();
            Result latestResult = submission.getLatestResult();
            if (latestResult != null) {
                results = Set.of(latestResult);
                if (isStudent) {
                    latestResult.filterSensitiveInformation();
                }
            }
            submission.setResults(new ArrayList<>(results));
        }

        // add submission to participation or set it to empty set if no submission is available
        participation.setSubmissions(optionalSubmission.map(Set::of).orElse(Set.of()));

        // remove inner exercise from participation
        participation.setExercise(null);
    }

    /**
     * Validates and returns the student participations for exercises that allow multiple participations (programming and quiz),
     * which may include at most one graded and one practice participation.
     *
     * @param participations the set of participations in the exercise to validate
     * @return the valid set of participations (empty, only graded, only practice, or both)
     * @throws IllegalArgumentException if there are multiple graded or multiple practice participations
     */
    private Set<StudentParticipation> findStudentParticipationsForMultipleParticipationExercises(Set<StudentParticipation> participations) {
        var gradedParticipations = participations.stream().filter(p -> !p.isPracticeMode()).collect(Collectors.toSet());
        if (gradedParticipations.size() > 1) {
            throw new IllegalArgumentException("There cannot be more than one graded participation per student for programming or quiz exercises");
        }
        var practiceParticipations = participations.stream().filter(Participation::isPracticeMode).collect(Collectors.toSet());
        if (practiceParticipations.size() > 1) {
            throw new IllegalArgumentException("There cannot be more than one practice participation per student for programming or quiz exercises");
        }

        return Stream.concat(gradedParticipations.stream(), practiceParticipations.stream()).collect(Collectors.toSet());
    }

    /**
     * Validates and returns the student participations for exercises that allow only a single participation (non-programming and non-quiz).
     *
     * @param participations the set of participations in the exercise to validate
     * @return the valid set of participations (empty or singleton set)
     * @throws IllegalArgumentException if there are multiple participations
     */
    private StudentParticipation findStudentParticipationForSingleParticipationExercises(Set<StudentParticipation> participations) {
        if (participations.size() > 1) {
            throw new IllegalArgumentException("Only one participation per student is allowed for exercises other than programming or quiz.");
        }
        return participations.iterator().next();
    }
}
