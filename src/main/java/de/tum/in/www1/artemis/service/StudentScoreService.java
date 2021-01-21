package de.tum.in.www1.artemis.service;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.Result;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.domain.scores.StudentScore;
import de.tum.in.www1.artemis.repository.StudentParticipationRepository;
import de.tum.in.www1.artemis.repository.StudentScoreRepository;

@Service
public class StudentScoreService {

    private final Logger log = LoggerFactory.getLogger(StudentScoreService.class);

    private final StudentScoreRepository studentScoreRepository;

    private final StudentParticipationRepository studentParticipationRepository;

    public StudentScoreService(StudentScoreRepository studentScoreRepository, StudentParticipationRepository studentParticipationRepository) {
        this.studentScoreRepository = studentScoreRepository;
        this.studentParticipationRepository = studentParticipationRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void removeAssociatedStudentScores(Exercise exercise) {
        studentScoreRepository.removeAssociatedWithExercise(exercise.getId());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void removeAssociatedStudentScores(Result result) {
        studentScoreRepository.removeStudentScoresAssociatedWithResult(result.getId());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateStudentScores(Result result) {
        if (result.getScore() == null) {
            return;
        }

        // load the corresponding student participation for information about student and exercise
        Optional<StudentParticipation> participationOptional = studentParticipationRepository.findStudentParticipationAssociatedWithResult(result.getId());
        if (participationOptional.isEmpty()) {
            return;
        }
        StudentParticipation studentParticipation = participationOptional.get();
        Exercise exercise = studentParticipation.getExercise();
        if (exercise.isExamExercise()) {
            return;
        }
        if (exercise.isTeamMode()) {
            return;
        }
        User user = studentParticipation.getStudent().get();
        Optional<StudentScore> studentScoreOptional = studentScoreRepository.findStudentScoreByStudentAndExercise(exercise.getId(), user.getId());

        // there exists already a student score for this exercise and student
        if (studentScoreOptional.isPresent()) {
            StudentScore existingStudentScore = studentScoreOptional.get();
            if (existingStudentScore.getLastResult().getId().equals(result.getId())) {
                existingStudentScore.setLastScore(result.getScore());
                existingStudentScore = studentScoreRepository.saveAndFlush(existingStudentScore);

            }
            if (existingStudentScore.getLastRatedResult().getId().equals(result.getId())) {
                existingStudentScore.setLastRatedScore(result.getScore());
                existingStudentScore = studentScoreRepository.saveAndFlush(existingStudentScore);
            }
            if (!(existingStudentScore.getLastResult().getId().equals(result.getId()) || existingStudentScore.getLastRatedResult().getId().equals(result.getId()))) {
                if (existingStudentScore.getLastResult() != null && result.getId() > existingStudentScore.getLastResult().getId()) {
                    existingStudentScore.setLastResult(result);
                    existingStudentScore.setLastScore(result.getScore());
                }
                if (existingStudentScore.getLastRatedResult() != null && result.isRated() != null && result.isRated()
                        && result.getId() > existingStudentScore.getLastRatedResult().getId()) {
                    existingStudentScore.setLastRatedResult(result);
                    existingStudentScore.setLastRatedScore(result.getScore());
                }
                studentScoreRepository.saveAndFlush(existingStudentScore);
            }

        }
        else { // no student score exists yet for exercise and student
            StudentScore newStudentScore = new StudentScore();
            newStudentScore.setExercise(exercise);
            newStudentScore.setUser(user);
            newStudentScore.setLastScore(result.getScore());
            newStudentScore.setLastResult(result);
            if (result.isRated() != null && result.isRated()) {
                newStudentScore.setLastRatedScore(result.getScore());
                newStudentScore.setLastRatedResult(result);
            }
            studentScoreRepository.saveAndFlush(newStudentScore);
        }
    }

    // /**
    // * Updates all StudentScores for result updatedResult.
    // *
    // * @param result result to be updated
    // */
    // @Transactional(propagation = Propagation.REQUIRES_NEW)
    // public void updateResult(Result result) {
    //
    // var participation = (StudentParticipation) result.getParticipation();
    // var student = participation.getStudent();
    // var exercise = exerciseRepository.findById(participation.getExercise().getId());
    //
    // if (student.isEmpty() || exercise.isEmpty()) {
    // return;
    // }
    //
    // Optional<StudentScore> studentScoreConnectedToResult = studentScoreRepository.findByResult(result);
    //
    // if (studentScoreConnectedToResult.isPresent()) {
    //
    // StudentScore studentScore = studentScoreConnectedToResult.get();
    // studentScore.setResult(result);
    // studentScore.setLastScore(result.getScore());
    //
    // studentScore = studentScoreRepository.saveAndFlush(studentScore);
    // log.info("Updated StudentScore: " + studentScore);
    // }
    // else {
    // StudentScore studentScore = new StudentScore(student.get(), exercise.get(), result);
    // studentScore.setLastScore(result.getScore());
    //
    // studentScoreRepository.saveAndFlush(studentScore);
    // log.info("Created StudentScore: " + studentScore);
    // }
    // }

}
