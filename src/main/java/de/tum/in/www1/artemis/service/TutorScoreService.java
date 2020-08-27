package de.tum.in.www1.artemis.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import de.tum.in.www1.artemis.domain.Complaint;
import de.tum.in.www1.artemis.domain.ComplaintResponse;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.Feedback;
import de.tum.in.www1.artemis.domain.Result;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.domain.scores.TutorScore;
import de.tum.in.www1.artemis.repository.ComplaintRepository;
import de.tum.in.www1.artemis.repository.ComplaintResponseRepository;
import de.tum.in.www1.artemis.repository.StudentParticipationRepository;
import de.tum.in.www1.artemis.repository.TutorScoreRepository;

@Service
public class TutorScoreService {

    private final TutorScoreRepository tutorScoreRepository;

    private final StudentParticipationRepository studentParticipationRepository;

    private final ComplaintRepository complaintRepository;

    private final ComplaintResponseRepository complaintResponseRepository;

    public TutorScoreService(TutorScoreRepository tutorScoreRepository, StudentParticipationRepository studentParticipationRepository, ComplaintRepository complaintRepository, ComplaintResponseRepository complaintResponseRepository) {
        this.tutorScoreRepository = tutorScoreRepository;
        this.studentParticipationRepository = studentParticipationRepository;
        this.complaintRepository = complaintRepository;
        this.complaintResponseRepository = complaintResponseRepository;
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
     * Returns all TutorScores for course.
     *
     * @param tutor tutor user
     * @param exercise exercise
     * @return list of tutor score objects for that course
     */
    public Optional<TutorScore> getTutorScoreForTutorAndExercise(User tutor, Exercise exercise) {
        return tutorScoreRepository.findByTutorAndExercise(tutor, exercise);
    }

    /**
     * Deletes all TutorScores for result deletedResult.
     *
     * @param deletedResult result to be deleted
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void removeResult(Result deletedResult) {
        // TODO: change the entry that is based on this result: find it based on the exercise id and subtract the max points from assessmentPoints, reduce assessments by one
        // in case, there has been a complaint, complaint response or feedback request, adjust those values as well
        StudentParticipation participation = studentParticipationRepository.findById(deletedResult.getParticipation().getId()).get();
        Exercise exercise = participation.getExercise();

        var existingTutorScore = tutorScoreRepository.findByTutorAndExercise(deletedResult.getAssessor(), exercise);

        if (existingTutorScore.isPresent()) {
            TutorScore tutorScore = existingTutorScore.get();

            tutorScore.setAssessments(tutorScore.getAssessments() - 1);
            tutorScore.setAssessmentsPoints(tutorScore.getAssessmentsPoints() - exercise.getMaxScore());

            // handle complaint
            if (deletedResult.hasComplaint()) {
                Complaint complaint = complaintRepository.findByResult_Id(deletedResult.getId()).get();

                if (complaint.isAccepted()) {
                    tutorScore.setAcceptedComplaints(tutorScore.getAcceptedComplaints() - 1);
                }

                tutorScore.setAllComplaints(tutorScore.getAllComplaints() - 1);
                tutorScore.setComplaintsPoints(tutorScore.getComplaintsPoints() - exercise.getMaxScore());

                // complaint response
                Optional<ComplaintResponse> complaintResponse = complaintResponseRepository.findByComplaint_Id(complaint.getId());

                if (complaintResponse.isPresent()) {
                    var fromComplaintResponse = tutorScoreRepository.findByTutorAndExercise(complaintResponse.get().getReviewer(), exercise).get();

                    fromComplaintResponse.setComplaintResponses(fromComplaintResponse.getComplaintResponses() - 1);
                    fromComplaintResponse.setComplaintResponsesPoints(fromComplaintResponse.getComplaintResponsesPoints() - exercise.getMaxScore());

                    tutorScoreRepository.save(fromComplaintResponse);
                }
            }

            tutorScoreRepository.save(tutorScore);
        }
    }

    /**
     * Updates all TutorScores for result updatedResult.
     *
     * @param updatedResult result to be updated
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateResult(Result updatedResult) {
        StudentParticipation participation = studentParticipationRepository.findById(updatedResult.getParticipation().getId()).get();
        Exercise exercise = participation.getExercise();

        /*var existingTutorScore = tutorScoreRepository.findByTutorAndExercise(updatedResult.getAssessor(), exercise);

        if (existingTutorScore.isPresent()) {
            // same assessor do nothing
        } else {
            // changed assessor
            TutorScore tutorScore = existingTutorScore.get();

            tutorScore.setAssessmentsPoints(tutorScore.getAssessmentsPoints() + exercise.getMaxScore());

            tutorScoreRepository.save(tutorScore);
        }*/
    }

    /**
     * Adds new TutorScores for result newResult.
     *
     * @param newResult result to be added
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public void addNewResult(Result newResult) {
        StudentParticipation participation = studentParticipationRepository.findById(newResult.getParticipation().getId()).get();
        Exercise exercise = participation.getExercise();

        // TODO: probably not working either
        // Optional<TutorScore> existingScore = getTutorScoreForTutorAndExercise(newResult.getAssessor(), exercise);
        var existingScore = new ArrayList<TutorScore>();

        if (existingScore.size() > 0) {
            TutorScore oldScore = existingScore.get(0);

            oldScore.setAssessments(oldScore.getAssessments() + 1);
            oldScore.setAssessmentsPoints(oldScore.getAssessmentsPoints() + exercise.getMaxScore());

            tutorScoreRepository.save(oldScore);
        } else {
            TutorScore newScore = new TutorScore(newResult.getAssessor(), exercise, 1, exercise.getMaxScore());

            tutorScoreRepository.save(newScore);
        }
    }

    // TODO: also handle complaints, feedback requests and complaint responses
}
