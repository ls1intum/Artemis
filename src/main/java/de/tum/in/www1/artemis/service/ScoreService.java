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

    /**
     * Either updates or removes an existing participant score when a result is removed
     *
     * @param resultToBeDeleted result that will be removes
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void removeOrUpdateAssociatedParticipantScore(Result resultToBeDeleted) {
        Optional<ParticipantScore> associatedStudentScoreOptional = participantScoreRepository.findParticipantScoreAssociatedWithResult(resultToBeDeleted.getId());
        if (associatedStudentScoreOptional.isEmpty()) {
            return;
        }
        // There is a participant score connected to the result that will be deleted
        ParticipantScore associatedParticipantScore = associatedStudentScoreOptional.get();

        // There are two possibilities now:
        // A: Another result exists for the exercise and the student / team -> update participant score with the newest one
        // B: No other result exists for the exercise and the student / team -> remove participant score
        if (resultToBeDeleted.equals(associatedParticipantScore.getLastRatedResult())) {
            Optional<Result> newLastRatedResultOptional = getNewLastRatedResultForParticipantScore(associatedParticipantScore);
            if (newLastRatedResultOptional.isPresent()) {
                Result newLastRatedResult = newLastRatedResultOptional.get();
                associatedParticipantScore.setLastRatedResult(newLastRatedResult);
                associatedParticipantScore.setLastRatedScore(newLastRatedResult.getScore());
            }
            else {
                associatedParticipantScore.setLastRatedResult(null);
                associatedParticipantScore.setLastRatedScore(null);
            }
        }

        if (resultToBeDeleted.equals(associatedParticipantScore.getLastResult())) {
            Optional<Result> newLastResultOptional = getNewLastResultForParticipantScore(associatedParticipantScore);
            if (newLastResultOptional.isPresent()) {
                Result newLastResult = newLastResultOptional.get();
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

        // There is a deadlock problem with programming exercises here if we use the participation from the result (reason unknown at the moment)
        // ,therefore we get the participation from the database
        Optional<StudentParticipation> studentParticipationOptional = getStudentParticipationForResult(result);
        if (studentParticipationOptional.isEmpty()) {
            return;
        }
        StudentParticipation studentParticipation = studentParticipationOptional.get();
        Exercise exercise = studentParticipation.getExercise();
        ParticipantScore existingParticipationScoreForExerciseAndParticipant = getExistingParticipationScore(studentParticipation, exercise);
        // there already exists a participant score -> we need to update it
        if (existingParticipationScoreForExerciseAndParticipant != null) {
            updateExistingParticipantScore(existingParticipationScoreForExerciseAndParticipant, result);
        }
        else { // there does not already exists a participant score -> we need to create it
            createNewParticipantScore(result, studentParticipation, exercise);
        }
    }

    /**
     * Gets the student participation for a result from the database
     *
     * @param result result for which to get the student participation
     * @return student participation optional
     */
    private Optional<StudentParticipation> getStudentParticipationForResult(Result result) {
        Optional<Participation> participationOptional = participationRepository.findParticipationAssociatedWithResult(result.getId());
        if (participationOptional.isEmpty()) {
            return Optional.empty();
        }
        Participation participation = participationOptional.get();

        boolean isStudentParticipation = participation.getClass().equals(StudentParticipation.class)
                || participation.getClass().equals(ProgrammingExerciseStudentParticipation.class);
        if (!isStudentParticipation) {
            return Optional.empty();
        }
        else {
            return Optional.of((StudentParticipation) participation);
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

    /**
     * Get the result that can replace the currently set last result for a participant score
     *
     * @param participantScore participant score
     * @return optional of new result
     */
    private Optional<Result> getNewLastResultForParticipantScore(ParticipantScore participantScore) {
        List<Result> resultOrdered;
        if (participantScore.getClass().equals(StudentScore.class)) {
            StudentScore studentScore = (StudentScore) participantScore;
            resultOrdered = exerciseRepository
                    .getResultsOrderedByParticipationIdSubmissionIdResultIdDescForStudent(participantScore.getExercise().getId(), studentScore.getUser().getId()).stream()
                    .filter(r -> !participantScore.getLastResult().equals(r)).collect(Collectors.toList());
        }
        else {
            TeamScore teamScore = (TeamScore) participantScore;
            resultOrdered = exerciseRepository
                    .getResultsOrderedByParticipationIdSubmissionIdResultIdDescForTeam(participantScore.getExercise().getId(), teamScore.getTeam().getId()).stream()
                    .filter(r -> !participantScore.getLastResult().equals(r)).collect(Collectors.toList());
        }
        return resultOrdered.isEmpty() ? Optional.empty() : Optional.of(resultOrdered.get(0));

    }

    /**
     * Get the result that can replace the currently set last rated result for a participant score
     *
     * @param participantScore participant score
     * @return optional of new result
     */
    private Optional<Result> getNewLastRatedResultForParticipantScore(ParticipantScore participantScore) {
        List<Result> ratedResultsOrdered;
        if (participantScore.getClass().equals(StudentScore.class)) {
            StudentScore studentScore = (StudentScore) participantScore;
            ratedResultsOrdered = exerciseRepository
                    .getRatedResultsOrderedByParticipationIdSubmissionIdResultIdDescForStudent(participantScore.getExercise().getId(), studentScore.getUser().getId()).stream()
                    .filter(r -> !participantScore.getLastRatedResult().equals(r)).collect(Collectors.toList());
        }
        else {
            TeamScore teamScore = (TeamScore) participantScore;
            ratedResultsOrdered = exerciseRepository
                    .getRatedResultsOrderedByParticipationIdSubmissionIdResultIdDescForTeam(participantScore.getExercise().getId(), teamScore.getTeam().getId()).stream()
                    .filter(r -> !participantScore.getLastRatedResult().equals(r)).collect(Collectors.toList());
        }
        return ratedResultsOrdered.isEmpty() ? Optional.empty() : Optional.of(ratedResultsOrdered.get(0));

    }

}
