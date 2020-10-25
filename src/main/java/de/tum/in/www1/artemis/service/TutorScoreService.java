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
import de.tum.in.www1.artemis.repository.ResultRepository;
import de.tum.in.www1.artemis.repository.TutorScoreRepository;

@Service
public class TutorScoreService {

    private final Logger log = LoggerFactory.getLogger(TutorScoreService.class);

    private final TutorScoreRepository tutorScoreRepository;

    private final ComplaintRepository complaintRepository;

    private final ComplaintResponseRepository complaintResponseRepository;

    private final ResultRepository resultRepository;

    private final ExerciseRepository exerciseRepository;

    public TutorScoreService(TutorScoreRepository tutorScoreRepository, ComplaintRepository complaintRepository, ComplaintResponseRepository complaintResponseRepository,
            ResultRepository resultRepository, ExerciseRepository exerciseRepository) {
        this.tutorScoreRepository = tutorScoreRepository;
        this.complaintRepository = complaintRepository;
        this.complaintResponseRepository = complaintResponseRepository;
        this.resultRepository = resultRepository;
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
     * Add empty Complaint or FeedbackRequest to TutorScore.
     *
     * @param tutorScore tutor score
     * @param complaint complaint
     * @param exercise exercise
     */
    public void addUnansweredComplaintOrFeedbackRequest(TutorScore tutorScore, Complaint complaint, Exercise exercise) {
        if (complaint.getComplaintType() == ComplaintType.COMPLAINT) {
            tutorScore.setAllComplaints(tutorScore.getAllComplaints() + 1);

            if (exercise.getMaxScore() != null) {
                tutorScore.setComplaintsPoints(tutorScore.getComplaintsPoints() + exercise.getMaxScore());
            }
        }
        else if (complaint.getComplaintType() == ComplaintType.MORE_FEEDBACK) {
            tutorScore.setAllFeedbackRequests(tutorScore.getAllFeedbackRequests() + 1);
            tutorScore.setNotAnsweredFeedbackRequests(tutorScore.getNotAnsweredFeedbackRequests() + 1);

            if (exercise.getMaxScore() != null) {
                tutorScore.setFeedbackRequestsPoints(tutorScore.getFeedbackRequestsPoints() + exercise.getMaxScore());
            }
        }

        tutorScoreRepository.save(tutorScore);
    }

    /**
     * Remove one unanswered FeedbackRequest from TutorScore.
     *
     * @param tutorScore tutor score
     */
    public void removeNotAnsweredFeedbackRequest(TutorScore tutorScore) {
        tutorScore.setNotAnsweredFeedbackRequests(tutorScore.getNotAnsweredFeedbackRequests() - 1);

        tutorScoreRepository.save(tutorScore);
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

            tutorScore = removeComplaintsAndFeedbackRequests(tutorScore, deletedResult, exercise.get());

            tutorScoreRepository.save(tutorScore);
            log.info("Updated existing TutorScore: " + tutorScore);
        }
    }

    /**
     * Updates all TutorScores for result updatedResult.
     *
     * @param updatedResult result to be updated
     */
    public void updateResult(Result updatedResult) {
        if (updatedResult.getParticipation() == null || updatedResult.getParticipation().getId() == null
                || updatedResult.getParticipation().getClass() != StudentParticipation.class) {
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

        if (existingTutorScore.isPresent()) {
            TutorScore tutorScore = existingTutorScore.get();

            tutorScore.setAssessments(tutorScore.getAssessments() + 1);
            tutorScore.setAssessmentsPoints(tutorScore.getAssessmentsPoints() + maxScore);

            tutorScore = addComplaintsAndFeedbackRequests(tutorScore, updatedResult, exercise.get());

            tutorScoreRepository.save(tutorScore);
            log.info("Updated existing TutorScore: " + tutorScore);
        }
        else {
            TutorScore newScore = new TutorScore(updatedResult.getAssessor(), exercise.get(), 1, maxScore);

            newScore = addComplaintsAndFeedbackRequests(newScore, updatedResult, exercise.get());

            tutorScoreRepository.save(newScore);
            log.info("Added new TutorScore: " + newScore);
        }
    }

    private Optional<TutorScore> findTutorScoreFromExercise(Exercise exercise, User assessor) {
        var tutorScores = exercise.getTutorScores();

        for (TutorScore score : tutorScores) {
            if (score.getTutor().getId() == assessor.getId()) {
                return Optional.of(score);
            }
        }

        return Optional.empty();
    }

    /**
     * Helper method for updating complaints and feedback requests in tutor scores.
     */
    private TutorScore addComplaintsAndFeedbackRequests(TutorScore tutorScore, Result result, Exercise exercise) {
        // add complaints and feedback requests
        if (Boolean.TRUE.equals(result.hasComplaint())) {
            var complaint = result.getComplaint();

            if (complaint != null) {
                // complaint
                if (complaint.getComplaintType() == ComplaintType.COMPLAINT) {
                    tutorScore.setAllComplaints(tutorScore.getAllComplaints() + 1);
                    tutorScore.setComplaintsPoints(tutorScore.getComplaintsPoints() + exercise.getMaxScore());

                    if (Boolean.TRUE.equals(complaint.isAccepted())) {
                        tutorScore.setAcceptedComplaints(tutorScore.getAcceptedComplaints() + 1);
                    }

                    // complaint response
                    ComplaintResponse complaintResponse = complaint.getComplaintResponse();

                    if (complaintResponse != null) {
                        var fromComplaintResponse = findTutorScoreFromExercise(exercise, complaintResponse.getReviewer());

                        TutorScore fromComplaint;

                        if (fromComplaintResponse.isPresent()) {
                            fromComplaint = fromComplaintResponse.get();
                        }
                        else {
                            fromComplaint = new TutorScore(complaintResponse.getReviewer(), exercise, 0, 0);
                        }

                        fromComplaint.setComplaintResponses(fromComplaint.getComplaintResponses() + 1);
                        fromComplaint.setComplaintResponsesPoints(fromComplaint.getComplaintResponsesPoints() + exercise.getMaxScore());
                        tutorScoreRepository.save(fromComplaint);
                    }
                }

                // feedback request
                if (complaint.getComplaintType() == ComplaintType.MORE_FEEDBACK) {
                    tutorScore.setAllFeedbackRequests(tutorScore.getAllFeedbackRequests() + 1);
                    tutorScore.setFeedbackRequestsPoints(tutorScore.getFeedbackRequestsPoints() + exercise.getMaxScore());

                    // answered feedback request
                    ComplaintResponse complaintResponse = complaint.getComplaintResponse();

                    if (complaintResponse != null) {
                        var fromComplaintResponse = findTutorScoreFromExercise(exercise, complaintResponse.getReviewer());

                        TutorScore fromComplaint;

                        if (fromComplaintResponse.isPresent()) {
                            fromComplaint = fromComplaintResponse.get();
                        }
                        else {
                            fromComplaint = new TutorScore(complaintResponse.getReviewer(), exercise, 0, 0);
                        }

                        fromComplaint.setAnsweredFeedbackRequests(fromComplaint.getAnsweredFeedbackRequests() + 1);
                        fromComplaint.setAnsweredFeedbackRequestsPoints(fromComplaint.getAnsweredFeedbackRequestsPoints() + exercise.getMaxScore());

                        tutorScoreRepository.save(fromComplaint);
                    }
                    else {
                        tutorScore.setNotAnsweredFeedbackRequests(tutorScore.getNotAnsweredFeedbackRequests() + 1);
                    }
                }
            }
        }

        return tutorScore;
    }

    /**
     * Helper method for removing complaints and feedback requests from tutor scores.
     */
    private TutorScore removeComplaintsAndFeedbackRequests(TutorScore tutorScore, Result deletedResult, Exercise exercise) {
        // handle complaints and feedback requests
        if (Boolean.TRUE.equals(deletedResult.hasComplaint())) {
            var complaint = deletedResult.getComplaint();

            if (complaint != null) {
                // complaint
                if (complaint.getComplaintType() == ComplaintType.COMPLAINT) {
                    if (tutorScore.getAllComplaints() > 0) {
                        tutorScore.setAllComplaints(tutorScore.getAllComplaints() - 1);
                        tutorScore.setComplaintsPoints(tutorScore.getComplaintsPoints() - exercise.getMaxScore());
                    }

                    if (Boolean.TRUE.equals(complaint.isAccepted())) {
                        tutorScore.setAcceptedComplaints(tutorScore.getAcceptedComplaints() - 1);
                    }

                    // complaint response
                    ComplaintResponse complaintResponse = complaint.getComplaintResponse();

                    if (complaintResponse != null) {
                        var fromComplaintResponse = findTutorScoreFromExercise(exercise, complaintResponse.getReviewer());

                        TutorScore fromComplaint;

                        if (fromComplaintResponse.isPresent()) {
                            fromComplaint = fromComplaintResponse.get();
                        }
                        else {
                            fromComplaint = new TutorScore(complaintResponse.getReviewer(), exercise, 0, 0);
                        }

                        if (fromComplaint.getComplaintResponses() > 0) {
                            fromComplaint.setComplaintResponses(fromComplaint.getComplaintResponses() - 1);
                            fromComplaint.setComplaintResponsesPoints(fromComplaint.getComplaintResponsesPoints() - exercise.getMaxScore());
                        }

                        tutorScoreRepository.save(fromComplaint);
                    }
                }

                // feedback request
                if (complaint.getComplaintType() == ComplaintType.MORE_FEEDBACK) {
                    if (tutorScore.getAllFeedbackRequests() > 0) {
                        tutorScore.setAllFeedbackRequests(tutorScore.getAllFeedbackRequests() - 1);
                        tutorScore.setFeedbackRequestsPoints(tutorScore.getFeedbackRequestsPoints() - exercise.getMaxScore());
                    }

                    // answered feedback request
                    ComplaintResponse complaintResponse = complaint.getComplaintResponse();

                    if (complaintResponse != null) {
                        var fromComplaintResponse = findTutorScoreFromExercise(exercise, complaintResponse.getReviewer());

                        TutorScore fromComplaint;

                        if (fromComplaintResponse.isPresent()) {
                            fromComplaint = fromComplaintResponse.get();
                        }
                        else {
                            fromComplaint = new TutorScore(complaintResponse.getReviewer(), exercise, 0, 0);
                        }

                        if (fromComplaint.getAnsweredFeedbackRequests() > 0) {
                            fromComplaint.setAnsweredFeedbackRequests(fromComplaint.getAnsweredFeedbackRequests() - 1);
                            fromComplaint.setAnsweredFeedbackRequestsPoints(fromComplaint.getAnsweredFeedbackRequestsPoints() - exercise.getMaxScore());
                        }

                        tutorScoreRepository.save(fromComplaint);
                    }
                    else {
                        if (tutorScore.getNotAnsweredFeedbackRequests() > 0) {
                            tutorScore.setNotAnsweredFeedbackRequests(tutorScore.getNotAnsweredFeedbackRequests() - 1);
                        }
                    }
                }
            }
        }

        return tutorScore;
    }
}
