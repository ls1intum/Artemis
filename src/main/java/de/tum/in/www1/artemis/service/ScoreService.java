package de.tum.in.www1.artemis.service;

import static de.tum.in.www1.artemis.service.util.RoundingUtil.roundScoreSpecifiedByCourseSettings;

import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.participation.Participation;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.domain.scores.ParticipantScore;
import de.tum.in.www1.artemis.domain.scores.StudentScore;
import de.tum.in.www1.artemis.domain.scores.TeamScore;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.security.SecurityUtils;

@Service
public class ScoreService {

    private final StudentScoreRepository studentScoreRepository;

    private final ParticipantScoreRepository participantScoreRepository;

    private final TeamScoreRepository teamScoreRepository;

    private final ParticipationRepository participationRepository;

    private final ResultRepository resultRepository;

    private final Logger logger = LoggerFactory.getLogger(ScoreService.class);

    public ScoreService(StudentScoreRepository studentScoreRepository, TeamScoreRepository teamScoreRepository, ParticipationRepository participationRepository,
            ResultRepository resultRepository, ParticipantScoreRepository participantScoreRepository) {
        this.studentScoreRepository = studentScoreRepository;
        this.participationRepository = participationRepository;
        this.participantScoreRepository = participantScoreRepository;
        this.teamScoreRepository = teamScoreRepository;
        this.resultRepository = resultRepository;
    }

    /**
     * Either updates or removes an existing participant score when a result is removed
     * The annotation "@Transactional" is ok because it means that this method does not support run in an outer transactional context, instead the outer transaction is paused
     *
     * @param resultToBeDeleted result that will be removes
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED) // ok (see JavaDoc)
    public void removeOrUpdateAssociatedParticipantScore(Result resultToBeDeleted) {
        // In this method we use custom @Query methods that will fail if no authentication is available, therefore
        // we check this here and set a dummy authentication if none is available (this is the case in a scheduled service or
        // websocket)
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            SecurityUtils.setAuthorizationObject();
        }

        Optional<ParticipantScore> associatedParticipantScoreOptional;
        if (resultToBeDeleted.isRated() != null && resultToBeDeleted.isRated()) {
            associatedParticipantScoreOptional = participantScoreRepository.findParticipantScoreByLastRatedResult(resultToBeDeleted);
        }
        else {
            associatedParticipantScoreOptional = participantScoreRepository.findParticipantScoresByLastResult(resultToBeDeleted);
        }

        if (associatedParticipantScoreOptional.isEmpty()) {
            return;
        }

        // There is a participant score connected to the result that will be deleted
        ParticipantScore associatedParticipantScore = associatedParticipantScoreOptional.get();
        Exercise exercise = associatedParticipantScore.getExercise();
        String originalParticipantScoreStructure = associatedParticipantScore.toString();

        // There are two possibilities now:
        // A: Another result exists for the exercise and the student / team -> update participant score with the newest one
        // B: No other result exists for the exercise and the student / team -> remove participant score
        tryToFindNewLastResult(resultToBeDeleted, associatedParticipantScore, exercise);

        if (associatedParticipantScore.getLastResult() == null && associatedParticipantScore.getLastRatedResult() == null) {
            participantScoreRepository.deleteById(associatedParticipantScore.getId());
            logger.info("Deleted an existing participant score: " + originalParticipantScoreStructure);
        }
        else {
            ParticipantScore updatedParticipantScore = participantScoreRepository.saveAndFlush(associatedParticipantScore);
            logger.debug("Updated an existing participant score. Was: " + originalParticipantScoreStructure + ". Is: " + updatedParticipantScore);
        }
    }

    private void tryToFindNewLastResult(Result resultToBeDeleted, ParticipantScore associatedParticipantScore, Exercise exercise) {
        if (resultToBeDeleted.equals(associatedParticipantScore.getLastRatedResult())) {
            Optional<Result> newLastRatedResultOptional = getNewLastRatedResultForParticipantScore(associatedParticipantScore);
            if (newLastRatedResultOptional.isPresent()) {
                Result newLastRatedResult = newLastRatedResultOptional.get();
                setLastRatedAttributes(associatedParticipantScore, newLastRatedResult, exercise);
            }
            else {
                setLastRatedAttributes(associatedParticipantScore, null, exercise);
            }
        }

        if (resultToBeDeleted.equals(associatedParticipantScore.getLastResult())) {
            Optional<Result> newLastResultOptional = getNewLastResultForParticipantScore(associatedParticipantScore);
            if (newLastResultOptional.isPresent()) {
                Result newLastResult = newLastResultOptional.get();
                setLastAttributes(associatedParticipantScore, newLastResult, exercise);
            }
            else {
                setLastAttributes(associatedParticipantScore, null, exercise);
            }
        }
    }

    /**
     * Either updates an existing participant score or creates a new participant score if a new result comes in
     * The annotation "@Transactional" is ok because it means that this method does not support run in an outer transactional context, instead the outer transaction is paused
     *
     * @param createdOrUpdatedResult newly created or updated result
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED) // ok (see JavaDoc)
    public void updateOrCreateParticipantScore(Result createdOrUpdatedResult) {
        if (createdOrUpdatedResult.getScore() == null || createdOrUpdatedResult.getCompletionDate() == null) {
            return;
        }
        // There is a deadlock problem with programming exercises here if we use the participation from the result (reason unknown at the moment)
        // therefore we get the participation from the database
        Optional<StudentParticipation> studentParticipationOptional = getStudentParticipationForResult(createdOrUpdatedResult);
        if (studentParticipationOptional.isEmpty()) {
            return;
        }
        StudentParticipation studentParticipation = studentParticipationOptional.get();
        // we ignore test runs of exams
        if (studentParticipation.isTestRun()) {
            return;
        }
        Exercise exercise = studentParticipation.getExercise();
        ParticipantScore existingParticipationScoreForExerciseAndParticipant = getExistingParticipationScore(studentParticipation, exercise);
        // there already exists a participant score -> we need to update it
        if (existingParticipationScoreForExerciseAndParticipant != null) {
            updateExistingParticipantScore(existingParticipationScoreForExerciseAndParticipant, createdOrUpdatedResult, exercise);
        }
        else { // there does not already exist a participant score -> we need to create it
            createNewParticipantScore(createdOrUpdatedResult, studentParticipation, exercise);
        }
    }

    /**
     * Gets the student participation for a result from the database
     *
     * @param result result for which to get the student participation
     * @return student participation optional
     */
    private Optional<StudentParticipation> getStudentParticipationForResult(Result result) {
        Optional<Participation> participationOptional = participationRepository.findByResults(result);
        if (participationOptional.isEmpty()) {
            return Optional.empty();
        }
        Participation participation = participationOptional.get();

        if (!(participation instanceof StudentParticipation)) {
            return Optional.empty();
        }
        return Optional.of((StudentParticipation) participation);
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
            Optional<TeamScore> teamScoreOptional = teamScoreRepository.findTeamScoreByExerciseAndTeam(exercise, team);
            if (teamScoreOptional.isPresent()) {
                existingParticipationScoreForExerciseAndParticipant = teamScoreOptional.get();
            }
        }
        else {
            User user = studentParticipation.getStudent().get();
            Optional<StudentScore> studentScoreOptional = studentScoreRepository.findStudentScoreByExerciseAndUser(exercise, user);
            if (studentScoreOptional.isPresent()) {
                existingParticipationScoreForExerciseAndParticipant = studentScoreOptional.get();
            }
        }
        return existingParticipationScoreForExerciseAndParticipant;
    }

    /**
     * Create a new Participant Score
     *
     * @param newResult            result containing the information about the score achieved
     * @param studentParticipation participation containing the information about the participant
     * @param exercise             exercise for which to create participant score
     */
    private void createNewParticipantScore(Result newResult, StudentParticipation studentParticipation, Exercise exercise) {
        if (exercise.isTeamMode()) {
            createNewTeamScore(newResult, studentParticipation, exercise);
        }
        else {
            createNewStudentScore(newResult, studentParticipation, exercise);
        }
    }

    private void createNewStudentScore(Result newResult, StudentParticipation studentParticipation, Exercise exercise) {
        StudentScore newStudentScore = new StudentScore();
        newStudentScore.setExercise(exercise);
        newStudentScore.setUser(studentParticipation.getStudent().get());
        setLastAttributes(newStudentScore, newResult, exercise);
        if (newResult.isRated() != null && newResult.isRated()) {
            setLastRatedAttributes(newStudentScore, newResult, exercise);
        }
        StudentScore studentScore = studentScoreRepository.saveAndFlush(newStudentScore);
        logger.info("Saved a new student score: " + studentScore);
    }

    private void createNewTeamScore(Result newResult, StudentParticipation studentParticipation, Exercise exercise) {
        TeamScore newTeamScore = new TeamScore();
        newTeamScore.setExercise(exercise);
        newTeamScore.setTeam(studentParticipation.getTeam().get());
        setLastAttributes(newTeamScore, newResult, exercise);
        if (newResult.isRated() != null && newResult.isRated()) {
            setLastRatedAttributes(newTeamScore, newResult, exercise);
        }
        TeamScore teamScore = teamScoreRepository.saveAndFlush(newTeamScore);
        logger.info("Saved a new team score: " + teamScore);
    }

    /**
     * Update an existing participant score when a new or updated result comes in
     *
     * @param participantScore            existing participant score that refers to the same exercise and participant as the result
     * @param exercise                    the exercise to which the participant score belong
     * @param updatedOrNewlyCreatedResult updated or new result
     */
    private void updateExistingParticipantScore(ParticipantScore participantScore, Result updatedOrNewlyCreatedResult, Exercise exercise) {
        String originalParticipantScoreStructure = participantScore.toString();

        // update the last result and last score if either it has not been set previously or new result is either the old one (=) or newer (>)
        if (participantScore.getLastResult() == null || updatedOrNewlyCreatedResult.getId() >= participantScore.getLastResult().getId()) {
            setLastAttributes(participantScore, updatedOrNewlyCreatedResult, exercise);
        }
        // update the last rated result and last rated score if either it has not been set previously or new rated result is either the old one (=) or newer (>)
        if (updatedOrNewlyCreatedResult.isRated() != null && updatedOrNewlyCreatedResult.isRated()
                && (participantScore.getLastRatedResult() == null || updatedOrNewlyCreatedResult.getId() >= participantScore.getLastRatedResult().getId())) {
            setLastRatedAttributes(participantScore, updatedOrNewlyCreatedResult, exercise);
        }
        // Edge Case: if the result is now unrated but is equal to the current last rated result we have to set these to null (result was switched from rated to unrated)
        if ((updatedOrNewlyCreatedResult.isRated() == null || !updatedOrNewlyCreatedResult.isRated())
                && updatedOrNewlyCreatedResult.equals(participantScore.getLastRatedResult())) {
            setLastRatedAttributes(participantScore, null, exercise);
        }
        participantScoreRepository.saveAndFlush(participantScore);
        logger.debug("Updated an existing participant score. Was: " + originalParticipantScoreStructure + ". Is: " + participantScore);
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
            resultOrdered = resultRepository
                    .getResultsOrderedByParticipationIdLegalSubmissionIdResultIdDescForStudent(participantScore.getExercise().getId(), studentScore.getUser().getId()).stream()
                    .filter(r -> !participantScore.getLastResult().equals(r)).toList();
        }
        else {
            TeamScore teamScore = (TeamScore) participantScore;
            resultOrdered = resultRepository
                    .getResultsOrderedByParticipationIdLegalSubmissionIdResultIdDescForTeam(participantScore.getExercise().getId(), teamScore.getTeam().getId()).stream()
                    .filter(r -> !participantScore.getLastResult().equals(r)).toList();
        }
        // the new last result (result with the highest id of submission with the highest id) will be at the beginning of the list
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
            ratedResultsOrdered = resultRepository
                    .getRatedResultsOrderedByParticipationIdLegalSubmissionIdResultIdDescForStudent(participantScore.getExercise().getId(), studentScore.getUser().getId()).stream()
                    .filter(r -> !participantScore.getLastRatedResult().equals(r)).toList();
        }
        else {
            TeamScore teamScore = (TeamScore) participantScore;
            ratedResultsOrdered = resultRepository
                    .getRatedResultsOrderedByParticipationIdLegalSubmissionIdResultIdDescForTeam(participantScore.getExercise().getId(), teamScore.getTeam().getId()).stream()
                    .filter(r -> !participantScore.getLastRatedResult().equals(r)).toList();
        }
        // the new last rated result (rated result with the highest id of submission with the highest id) will be at the beginning of the list
        return ratedResultsOrdered.isEmpty() ? Optional.empty() : Optional.of(ratedResultsOrdered.get(0));

    }

    private void setLastAttributes(ParticipantScore associatedParticipantScore, Result newLastResult, Exercise exercise) {
        associatedParticipantScore.setLastResult(newLastResult);
        if (newLastResult == null) {
            associatedParticipantScore.setLastScore(null);
            associatedParticipantScore.setLastPoints(null);
        }
        else {
            associatedParticipantScore.setLastScore(newLastResult.getScore());
            associatedParticipantScore.setLastPoints(
                    roundScoreSpecifiedByCourseSettings(newLastResult.getScore() * 0.01 * exercise.getMaxPoints(), exercise.getCourseViaExerciseGroupOrCourseMember()));

        }
    }

    private void setLastRatedAttributes(ParticipantScore associatedParticipantScore, Result newLastRatedResult, Exercise exercise) {
        associatedParticipantScore.setLastRatedResult(newLastRatedResult);
        if (newLastRatedResult == null) {
            associatedParticipantScore.setLastRatedScore(null);
            associatedParticipantScore.setLastRatedPoints(null);
        }
        else {
            associatedParticipantScore.setLastRatedScore(newLastRatedResult.getScore());
            associatedParticipantScore.setLastRatedPoints(
                    roundScoreSpecifiedByCourseSettings(newLastRatedResult.getScore() * 0.01 * exercise.getMaxPoints(), exercise.getCourseViaExerciseGroupOrCourseMember()));
        }
    }

}
