package de.tum.in.www1.artemis.service.listeners;

import javax.persistence.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationAdapter;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import de.tum.in.www1.artemis.domain.Result;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.domain.scores.StudentScore;
import de.tum.in.www1.artemis.repository.StudentParticipationRepository;
import de.tum.in.www1.artemis.repository.StudentScoresRepository;
import de.tum.in.www1.artemis.service.StudentScoresService;
import de.tum.in.www1.artemis.service.TutorScoresService;

@Component
public class ResultListener {

    private final Logger log = LoggerFactory.getLogger(ResultListener.class);

    // Note: this solution is not ideal, but everything else does not work, because of dependency injection problems with EntityListeners
    private static StudentScoresService studentScoresService;

    private static TutorScoresService tutorScoresService;

    private static StudentScoresRepository studentScoresRepository;

    private static StudentParticipationRepository studentParticipationRepository;

    @Autowired
    // we use lazy injection here, because a EntityListener needs an empty constructor
    public void setResultService(StudentScoresService studentScoresService) {
        ResultListener.studentScoresService = studentScoresService;
    }

    @Autowired
    // we use lazy injection here, because a EntityListener needs an empty constructor
    public void setTutorScoresService(TutorScoresService tutorScoresService) {
        ResultListener.tutorScoresService = tutorScoresService;
    }

    @Autowired
    // we use lazy injection here, because a EntityListener needs an empty constructor
    public void setTutorScoresService(StudentScoresRepository studentScoresRepository) {
        ResultListener.studentScoresRepository = studentScoresRepository;
    }

    @Autowired
    // we use lazy injection here, because a EntityListener needs an empty constructor
    public void setTutorScoresService(StudentParticipationRepository studentParticipationRepository) {
        ResultListener.studentParticipationRepository = studentParticipationRepository;
    }

    /**
     * After result gets deleted, delete all StudentScores/TutorScores with this result.
     *
     * @param deletedResult deleted result
     */
    @PostRemove
    public void postRemove(Result deletedResult) {

        wrapInSuccessfulTransactionSynchronization(() -> {
            log.info("Result " + deletedResult + " was deleted");
            // remove from Student Scores and Tutor Scores
            studentScoresService.removeResult(deletedResult);
            tutorScoresService.removeResult(deletedResult);
        });
    }

    /**
     * After result gets updated, update all StudentScores/TutorScores with this result.
     *
     * @param updatedResult updated result
     */
    @PostUpdate
    public void postUpdate(Result updatedResult) {

        wrapInSuccessfulTransactionSynchronization(() -> {
            log.info("Result " + updatedResult + " was updated");
            // update existing student score
            studentScoresService.updateResult(updatedResult);

            if (updatedResult.getAssessor() != null) {
                // update existing tutor scores
                tutorScoresService.updateResult(updatedResult);
            }
        });

    }

    /**
     * After result gets created, add/update StudentScores/TutorScores with this result.
     *
     * @param newResult newly created result
     */
    @PostPersist
    public void postPersist(Result newResult) {

        wrapInSuccessfulTransactionSynchronization(() -> {
            // add to student scores (or update existing one)
            studentScoresService.addNewResult(newResult);

            /*StudentParticipation participation = studentParticipationRepository.findById(newResult.getParticipation().getId()).get();
            StudentScore newScore = new StudentScore(participation.getStudent().get().getId(), participation.getExercise().getId(), newResult.getId(), 0);

            if (newResult.getScore() != null) {
                newScore.setScore(newResult.getScore());
            }

            studentScoresRepository.save(newScore);*/

            /*if (newResult.getAssessor() != null) {
                // add to tutor scores (or update existing one)
                tutorScoresService.addNewResult(newResult);
            }*/
        });
    }

    /**
     * Wraps the taks into a transaction synchronization to execute the code only after the transaction has been successfully committed
     * This avoid concurrent modification exceptions in Hibernate
     * @param task lamba code that will be executed after the transaction was completed successfully
     */
    private void wrapInSuccessfulTransactionSynchronization(Runnable task) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronizationAdapter() {

            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_COMMITTED) {
                    task.run();
                }
            }
        });
    }
}
