package de.tum.in.www1.artemis.service;

import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Complaint;
import de.tum.in.www1.artemis.domain.ComplaintResponse;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.Result;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.enumeration.ComplaintType;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.domain.scores.TutorScore;
import de.tum.in.www1.artemis.repository.ComplaintRepository;
import de.tum.in.www1.artemis.repository.ComplaintResponseRepository;
import de.tum.in.www1.artemis.repository.ExerciseRepository;
import de.tum.in.www1.artemis.repository.TutorScoreRepository;

@Service
public class TutorScoreService {

    private final Logger log = LoggerFactory.getLogger(TutorScoreService.class);

    private final TutorScoreRepository tutorScoreRepository;

    private final ComplaintRepository complaintRepository;

    private final ComplaintResponseRepository complaintResponseRepository;

    private final ExerciseRepository exerciseRepository;

    public TutorScoreService(TutorScoreRepository tutorScoreRepository, ComplaintRepository complaintRepository, ComplaintResponseRepository complaintResponseRepository,
            ExerciseRepository exerciseRepository) {
        this.tutorScoreRepository = tutorScoreRepository;
        this.complaintRepository = complaintRepository;
        this.complaintResponseRepository = complaintResponseRepository;
        this.exerciseRepository = exerciseRepository;
    }

    /**
     * Returns all TutorScores for exercise.
     *
     * @param exercise the exercise
     * @return list of tutor score objet for that exercise
     */
    public List<TutorScore> getTutorScoresForExercise(Exercise exercise) {
        return tutorScoreRepository.findAllByExercise(exercise);
    }

    /**
     * Returns all TutorScores for course.
     *
     * @param course course
     * @return list of tutor score objects for that course
     */
    public List<TutorScore> getTutorScoresForCourse(Course course) {
        return tutorScoreRepository.findAllByExerciseIn(course.getExercises());
    }

    /**
     * Delete all TutorScores for exercise.
     *
     * @param exercise exercise
     */
    public void deleteTutorScoresForExercise(Exercise exercise) {
        var scores = getTutorScoresForExercise(exercise);

        for (TutorScore score : scores) {
            tutorScoreRepository.delete(score);
        }
    }

    /**
     * Returns TutorScores for specific tutor and exercise.
     *
     * @param tutor tutor
     * @param exercise exercise
     * @return tutor score object for that tutor and exercise
     */
    public Optional<TutorScore> getTutorScoreForTutorAndExercise(User tutor, Exercise exercise) {
        return tutorScoreRepository.findByTutorAndExercise(tutor, exercise);
    }

    /**
     * Deletes all TutorScores for result deletedResult.
     *
     * @param deletedResult result to be deleted
     */
    public void removeResult(Result deletedResult) {
        if (deletedResult.getParticipation() == null || deletedResult.getParticipation().getId() == null) {
            return;
        }

        if (deletedResult.getParticipation().getClass() != StudentParticipation.class) {
            return;
        }

        var participation = (StudentParticipation) deletedResult.getParticipation();

        if (participation.getExercise() == null) {
            return;
        }

        var exercise = exerciseRepository.findById(participation.getExercise().getId());

        var existingTutorScore = findTutorScoreFromExercise(exercise.get(), deletedResult.getAssessor());

        if (existingTutorScore.isPresent()) {
            TutorScore tutorScore = existingTutorScore.get();

            if (tutorScore.getAssessments() > 0) {
                tutorScore.setAssessments(tutorScore.getAssessments() - 1);
                tutorScore.setAssessmentsPoints(tutorScore.getAssessmentsPoints() - exercise.get().getMaxScore());
            }

            tutorScoreRepository.save(tutorScore);
            log.info("Updated TutorScore: " + tutorScore);
        }
    }

    /**
     * Updates all TutorScores for result updatedResult.
     *
     * @param updatedResult result to be updated
     */
    public void updateResult(Result updatedResult) {
        if (updatedResult.getParticipation() == null || updatedResult.getParticipation().getId() == null
                || updatedResult.getParticipation().getClass() != StudentParticipation.class || updatedResult.getScore() == null) {
            return;
        }

        var participation = (StudentParticipation) updatedResult.getParticipation();

        if (participation.getExercise() == null) {
            return;
        }

        // make all tests but mine pass -> exercise not in db leads to foreign key exception in tests
        var exercise = exerciseRepository.findById(participation.getExercise().getId());
        Double maxScore = 0.0;

        if (exercise.get().getMaxScore() != null) {
            maxScore = exercise.get().getMaxScore();
        }

        var existingTutorScore = findTutorScoreFromExercise(exercise.get(), updatedResult.getAssessor());

        TutorScore tutorScore;

        if (existingTutorScore.isPresent()) {
            tutorScore = existingTutorScore.get();
        }
        else {
            tutorScore = new TutorScore(updatedResult.getAssessor(), exercise.get(), 0, 0);
        }

        tutorScore.setAssessments(tutorScore.getAssessments() + 1);
        tutorScore.setAssessmentsPoints(tutorScore.getAssessmentsPoints() + maxScore);

        tutorScore = tutorScoreRepository.save(tutorScore);
        log.info("Updated TutorScore: " + tutorScore);
    }

    /**
     * Helper method for find the correct TutorScore for User and Exercise.
     */
    private Optional<TutorScore> findTutorScoreFromExercise(Exercise exercise, User assessor) {
        var tutorScores = exercise.getTutorScores();

        if (assessor == null) {
            return Optional.empty();
        }

        for (TutorScore score : tutorScores) {
            if (score.getTutor().getId().equals(assessor.getId())) {
                return Optional.of(score);
            }
        }

        return Optional.empty();
    }

    /**
     * Add Complaint or FeedbackRequest to TutorScores.
     *
     * @param complaint Complaint
     */
    public void addComplaintOrFeedbackRequest(Complaint complaint) {
        var exercise = complaint.getResult().getParticipation().getExercise();
        var optionalTutorScore = findTutorScoreFromExercise(exercise, complaint.getResult().getAssessor());

        TutorScore tutorScore;

        if (optionalTutorScore.isPresent()) {
            tutorScore = optionalTutorScore.get();
        }
        else {
            tutorScore = new TutorScore(complaint.getResult().getAssessor(), exercise, 0, 0);
        }

        // add complaints or feedback requests
        if (complaint.getComplaintType() == ComplaintType.COMPLAINT) {
            tutorScore.setAllComplaints(tutorScore.getAllComplaints() + 1);
            tutorScore.setComplaintsPoints(tutorScore.getComplaintsPoints() + exercise.getMaxScore());
        }

        if (complaint.getComplaintType() == ComplaintType.MORE_FEEDBACK) {
            tutorScore.setAllFeedbackRequests(tutorScore.getAllFeedbackRequests() + 1);
            tutorScore.setFeedbackRequestsPoints(tutorScore.getFeedbackRequestsPoints() + exercise.getMaxScore());
            tutorScore.setNotAnsweredFeedbackRequests(tutorScore.getNotAnsweredFeedbackRequests() + 1);
        }

        tutorScoreRepository.save(tutorScore);
    }

    /**
     * Add ComplaintResponse or AnsweredFeedbackRequest to TutorScores.
     *
     * @param complaintResponse ComplaintResponse
     */
    public void addComplaintResponseOrAnsweredFeedbackRequest(ComplaintResponse complaintResponse) {
        var exercise = complaintResponse.getComplaint().getResult().getParticipation().getExercise();
        var complaint = complaintResponse.getComplaint();
        var optionalTutorScore = findTutorScoreFromExercise(exercise, complaintResponse.getReviewer());

        TutorScore tutorScore;

        if (optionalTutorScore.isPresent()) {
            tutorScore = optionalTutorScore.get();
        }
        else {
            tutorScore = new TutorScore(complaintResponse.getReviewer(), exercise, 0, 0);
        }

        if (complaint.getComplaintType() == ComplaintType.COMPLAINT) {
            tutorScore.setComplaintResponses(tutorScore.getComplaintResponses() + 1);
            tutorScore.setComplaintResponsesPoints(tutorScore.getComplaintResponsesPoints() + exercise.getMaxScore());
        }

        if (complaint.getComplaintType() == ComplaintType.MORE_FEEDBACK) {
            tutorScore.setAnsweredFeedbackRequests(tutorScore.getAnsweredFeedbackRequests() + 1);
            tutorScore.setAnsweredFeedbackRequestsPoints(tutorScore.getAnsweredFeedbackRequestsPoints() + exercise.getMaxScore());
            tutorScore.setNotAnsweredFeedbackRequests(tutorScore.getNotAnsweredFeedbackRequests() - 1);
        }

        tutorScoreRepository.save(tutorScore);
    }

    /**
     * Remove Complaint or FeedbackRequest from TutorScores.
     *
     * @param complaint Complaint
     */
    public void removeComplaintOrFeedbackRequest(Complaint complaint) {
        var exercise = complaint.getResult().getParticipation().getExercise();
        var optionalTutorScore = findTutorScoreFromExercise(exercise, complaint.getResult().getAssessor());

        if (optionalTutorScore.isEmpty()) {
            return;
        }

        TutorScore tutorScore = optionalTutorScore.get();

        if (complaint.getComplaintType() == ComplaintType.COMPLAINT) {
            if (tutorScore.getAllComplaints() > 0) {
                tutorScore.setAllComplaints(tutorScore.getAllComplaints() - 1);
                tutorScore.setComplaintsPoints(tutorScore.getComplaintsPoints() - exercise.getMaxScore());
            }

            if (Boolean.TRUE.equals(complaint.isAccepted())) {
                if (tutorScore.getAcceptedComplaints() > 0) {
                    tutorScore.setAcceptedComplaints(tutorScore.getAcceptedComplaints() - 1);
                }
            }
        }

        // feedback request
        if (complaint.getComplaintType() == ComplaintType.MORE_FEEDBACK) {
            if (tutorScore.getAllFeedbackRequests() > 0) {
                tutorScore.setAllFeedbackRequests(tutorScore.getAllFeedbackRequests() - 1);
                tutorScore.setFeedbackRequestsPoints(tutorScore.getFeedbackRequestsPoints() - exercise.getMaxScore());
            }

            if (!Boolean.TRUE.equals(complaint.isAccepted())) {
                if (tutorScore.getNotAnsweredFeedbackRequests() > 0) {
                    tutorScore.setNotAnsweredFeedbackRequests(tutorScore.getNotAnsweredFeedbackRequests() - 1);
                }
            }
        }

        tutorScoreRepository.save(tutorScore);
    }

    /**
     * Remove ComplaintResponse or AnsweredFeedbackRequest from TutorScores.
     *
     * @param complaintResponse ComplaintResponse
     */
    public void removeComplaintResponseOrAnsweredFeedbackRequest(ComplaintResponse complaintResponse) {
        var exercise = complaintResponse.getComplaint().getResult().getParticipation().getExercise();
        var complaint = complaintResponse.getComplaint();
        var optionalTutorScore = findTutorScoreFromExercise(exercise, complaintResponse.getReviewer());

        if (optionalTutorScore.isEmpty()) {
            return;
        }

        TutorScore tutorScore = optionalTutorScore.get();

        if (complaint.getComplaintType() == ComplaintType.COMPLAINT) {
            if (tutorScore.getComplaintResponses() > 0) {
                tutorScore.setComplaintResponses(tutorScore.getComplaintResponses() - 1);
                tutorScore.setComplaintResponsesPoints(tutorScore.getComplaintResponsesPoints() - exercise.getMaxScore());
            }
        }

        if (complaint.getComplaintType() == ComplaintType.MORE_FEEDBACK) {
            if (tutorScore.getAnsweredFeedbackRequests() > 0) {
                tutorScore.setAnsweredFeedbackRequests(tutorScore.getAnsweredFeedbackRequests() - 1);
                tutorScore.setAnsweredFeedbackRequestsPoints(tutorScore.getAnsweredFeedbackRequestsPoints() - exercise.getMaxScore());
            }
        }

        tutorScoreRepository.save(tutorScore);
    }
}
