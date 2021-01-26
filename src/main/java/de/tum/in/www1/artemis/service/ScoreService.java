package de.tum.in.www1.artemis.service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.Result;
import de.tum.in.www1.artemis.domain.Team;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.participation.Participation;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseStudentParticipation;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.domain.scores.ParticipantScore;
import de.tum.in.www1.artemis.domain.scores.StudentScore;
import de.tum.in.www1.artemis.domain.scores.TeamScore;
import de.tum.in.www1.artemis.repository.*;

@Service
public class ScoreService {

    private final StudentScoreRepository studentScoreRepository;

    private final ParticipantScoreRepository participantScoreRepository;

    private final TeamScoreRepository teamScoreRepository;

    private final ParticipationRepository participationRepository;

    private final ExerciseRepository exerciseRepository;

    public ScoreService(StudentScoreRepository studentScoreRepository, TeamScoreRepository teamScoreRepository, ParticipationRepository participationRepository,
            ExerciseRepository exerciseRepository, ParticipantScoreRepository participantScoreRepository) {
        this.studentScoreRepository = studentScoreRepository;
        this.participationRepository = participationRepository;
        this.exerciseRepository = exerciseRepository;
        this.participantScoreRepository = participantScoreRepository;
        this.teamScoreRepository = teamScoreRepository;
    }

    /**
     * Remove all participant scores associated with an exercise
     *
     * @param exercise exercise for which to remove the associated participant scores
     */
    public void removeAssociatedWithExercise(Exercise exercise) {
        participantScoreRepository.removeAssociatedWithExercise(exercise.getId());
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void removeOrUpdateAssociatedParticipantScore(Result resultToBeDeleted) {
        Optional<ParticipantScore> associatedStudentScoreOptional = participantScoreRepository.findParticipantScoreAssociatedWithResult(resultToBeDeleted.getId());
        if (associatedStudentScoreOptional.isEmpty()) {
            return;
        }
        ParticipantScore associatedParticipantScore = associatedStudentScoreOptional.get();

        List<Result> resultOrdered;
        List<Result> ratedResultOrdered;

        if (associatedParticipantScore.getClass().equals(StudentScore.class)) {
            StudentScore studentScore = (StudentScore) associatedParticipantScore;

            resultOrdered = exerciseRepository
                    .getResultsOrderedByParticipationIdSubmissionIdResultIdDescForStudent(associatedParticipantScore.getExercise().getId(), studentScore.getUser().getId()).stream()
                    .filter(r -> !resultToBeDeleted.equals(r)).collect(Collectors.toList());

            ratedResultOrdered = exerciseRepository
                    .getRatedResultsOrderedByParticipationIdSubmissionIdResultIdDescForStudent(associatedParticipantScore.getExercise().getId(), studentScore.getUser().getId())
                    .stream().filter(r -> !resultToBeDeleted.equals(r)).collect(Collectors.toList());
        }
        else {
            TeamScore teamScore = (TeamScore) associatedParticipantScore;

            resultOrdered = exerciseRepository
                    .getResultsOrderedByParticipationIdSubmissionIdResultIdDescForTeam(associatedParticipantScore.getExercise().getId(), teamScore.getTeam().getId()).stream()
                    .filter(r -> !resultToBeDeleted.equals(r)).collect(Collectors.toList());

            ratedResultOrdered = exerciseRepository
                    .getRatedResultsOrderedByParticipationIdSubmissionIdResultIdDescForTeam(associatedParticipantScore.getExercise().getId(), teamScore.getTeam().getId()).stream()
                    .filter(r -> !resultToBeDeleted.equals(r)).collect(Collectors.toList());
        }

        if (resultToBeDeleted.equals(associatedParticipantScore.getLastRatedResult())) {
            if (!ratedResultOrdered.isEmpty()) {
                Result newLastRatedResult = ratedResultOrdered.get(0);
                associatedParticipantScore.setLastRatedResult(newLastRatedResult);
                associatedParticipantScore.setLastRatedScore(newLastRatedResult.getScore());
            }
            else {
                associatedParticipantScore.setLastRatedResult(null);
                associatedParticipantScore.setLastRatedScore(null);
            }
        }

        if (resultToBeDeleted.equals(associatedParticipantScore.getLastResult())) {
            if (!resultOrdered.isEmpty()) {
                Result newLastResult = resultOrdered.get(0);
                associatedParticipantScore.setLastResult(newLastResult);
                associatedParticipantScore.setLastScore(newLastResult.getScore());
            }
            else {
                associatedParticipantScore.setLastResult(null);
                associatedParticipantScore.setLastScore(null);
            }
        }
        if (associatedParticipantScore.getLastResult() == null && associatedParticipantScore.getLastRatedResult() == null) {
            participantScoreRepository.deleteById(associatedParticipantScore.getId());
        }
        else {
            participantScoreRepository.saveAndFlush(associatedParticipantScore);
        }
    }

    /**
     * Either updates an existing participant score or creates a new participant score if a new result comes in
     *
     * @param result newly created or updated result
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void updateOrCreateParticipantScore(Result result) {
        if (result.getScore() == null || result.getCompletionDate() == null || result.getParticipation() == null) {
            return;
        }

        Optional<Participation> participationOptional = participationRepository.findParticipationAssociatedWithResult(result.getId());
        if (participationOptional.isEmpty()) {
            return;
        }
        Participation participation = participationOptional.get();

        boolean isInstanceOfStudentParticipation = participation.getClass().equals(StudentParticipation.class)
                || participation.getClass().equals(ProgrammingExerciseStudentParticipation.class);
        if (!isInstanceOfStudentParticipation) {
            return;
        }
        StudentParticipation studentParticipation = (StudentParticipation) participation;
        Exercise exercise = studentParticipation.getExercise();

        ParticipantScore existingParticipationScoreForExerciseAndParticipant = getExistingParticipationScore(studentParticipation, exercise);
        if (existingParticipationScoreForExerciseAndParticipant != null) {
            updateExistingParticipantScore(existingParticipationScoreForExerciseAndParticipant, result);
        }
        else {
            createNewParticipantScore(result, studentParticipation, exercise);
        }
    }

    /**
     * Gets the existing participation score for an exercise and a participant or null if none can be found
     *
     * @param studentParticipation participation containing the information about the participant
     * @param exercise             exercise for which to find the participation score of the participant
     * @return existing participation score or null if none can be found
     */
    private ParticipantScore getExistingParticipationScore(StudentParticipation studentParticipation, Exercise exercise) {
        ParticipantScore existingParticipationScoreForExerciseAndParticipant = null;
        if (exercise.isTeamMode()) {
            Team team = studentParticipation.getTeam().get();
            Optional<TeamScore> teamScoreOptional = teamScoreRepository.findTeamScoreByTeamAndExercise(exercise.getId(), team.getId());
            if (teamScoreOptional.isPresent()) {
                existingParticipationScoreForExerciseAndParticipant = teamScoreOptional.get();
            }
        }
        else {
            User user = studentParticipation.getStudent().get();
            Optional<StudentScore> studentScoreOptional = studentScoreRepository.findStudentScoreByStudentAndExercise(exercise.getId(), user.getId());
            if (studentScoreOptional.isPresent()) {
                existingParticipationScoreForExerciseAndParticipant = studentScoreOptional.get();
            }
        }
        return existingParticipationScoreForExerciseAndParticipant;
    }

    /**
     * Create a new Participant Score
     *
     * @param result               result containing the information about the score achieved
     * @param studentParticipation participation containing the information about the participant
     * @param exercise             exercise for which to create participant score
     */
    private void createNewParticipantScore(Result result, StudentParticipation studentParticipation, Exercise exercise) {
        if (exercise.isTeamMode()) {
            TeamScore newTeamScore = new TeamScore();
            newTeamScore.setExercise(exercise);
            newTeamScore.setTeam(studentParticipation.getTeam().get());
            newTeamScore.setLastScore(result.getScore());
            newTeamScore.setLastResult(result);
            if (result.isRated() != null && result.isRated()) {
                newTeamScore.setLastRatedScore(result.getScore());
                newTeamScore.setLastRatedResult(result);
            }
            teamScoreRepository.saveAndFlush(newTeamScore);
        }
        else {
            StudentScore newStudentScore = new StudentScore();
            newStudentScore.setExercise(exercise);
            newStudentScore.setUser(studentParticipation.getStudent().get());
            newStudentScore.setLastScore(result.getScore());
            newStudentScore.setLastResult(result);
            if (result.isRated() != null && result.isRated()) {
                newStudentScore.setLastRatedScore(result.getScore());
                newStudentScore.setLastRatedResult(result);
            }
            studentScoreRepository.saveAndFlush(newStudentScore);
        }
    }

    /**
     * Update an existing participant score when a new or updated result comes in
     *
     * @param participantScore existing participant score that refers to the same exercise and participant as the result
     * @param result           updated or new result
     */
    private void updateExistingParticipantScore(ParticipantScore participantScore, Result result) {
        ParticipantScore ps = participantScore;
        // update the last result and last score if either it has not been set previously or new result is either the old one (=) or newer (>)
        if (ps.getLastResult() == null || result.getId() >= ps.getLastResult().getId()) {
            ps.setLastResult(result);
            ps.setLastScore(result.getScore());
            ps = participantScoreRepository.saveAndFlush(ps);
        }
        // update the last rated result and last rated score if either it has not been set previously or new rated result is either the old one (=) or newer (>)
        if ((result.isRated() != null && result.isRated()) && (ps.getLastRatedResult() == null || result.getId() >= ps.getLastRatedResult().getId())) {
            ps.setLastRatedResult(result);
            ps.setLastRatedScore(result.getScore());
            ps = participantScoreRepository.saveAndFlush(ps);
        }

        // Edge Case: if the result is now unrated but is equal to the current last rated result we have to set these to null (result was switched from rated to unrated)
        if ((result.isRated() == null || !result.isRated()) && result.equals(ps.getLastRatedResult())) {
            ps.setLastRatedResult(null);
            ps.setLastRatedScore(null);
            participantScoreRepository.saveAndFlush(ps);
        }
    }

}
