package de.tum.in.www1.artemis.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import javax.validation.constraints.NotNull;

import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.exam.Exam;
import de.tum.in.www1.artemis.domain.leaderboard.tutor.*;
import de.tum.in.www1.artemis.repository.ComplaintRepository;
import de.tum.in.www1.artemis.repository.ResultRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.web.rest.dto.TutorLeaderboardDTO;

@Service
public class TutorLeaderboardService {

    private final ResultRepository resultRepository;

    private final ComplaintRepository complaintRepository;

    private final UserRepository userRepository;

    public TutorLeaderboardService(ResultRepository resultRepository, ComplaintRepository complaintRepository, UserRepository userRepository) {
        this.resultRepository = resultRepository;
        this.complaintRepository = complaintRepository;
        this.userRepository = userRepository;
    }

    /**
     * Returns tutor leaderboards for the specified course.
     * @param exerciseIdsOfCourse - the ids of the exercises which belong to the course
     * @param course              - course for which leaderboard is created
     * @return list of tutor leaderboard objects
     */
    public List<TutorLeaderboardDTO> getCourseLeaderboard(Course course, Set<Long> exerciseIdsOfCourse) {
        var tutors = userRepository.getTutors(course);
        var tutorLeaderboardAssessments = resultRepository.findTutorLeaderboardAssessmentByCourseId(exerciseIdsOfCourse);
        var tutorLeaderboardComplaints = complaintRepository.findTutorLeaderboardComplaintsByCourseId(course.getId());
        var tutorLeaderboardComplaintResponses = complaintRepository.findTutorLeaderboardComplaintResponsesByCourseId(course.getId());
        var tutorLeaderboardMoreFeedbackRequests = complaintRepository.findTutorLeaderboardMoreFeedbackRequestsByCourseId(course.getId());
        var tutorLeaderboardAnsweredMoreFeedbackRequests = complaintRepository.findTutorLeaderboardAnsweredMoreFeedbackRequestsByCourseId(course.getId());
        return aggregateTutorLeaderboardData(tutors, tutorLeaderboardAssessments, tutorLeaderboardComplaints, tutorLeaderboardMoreFeedbackRequests,
                tutorLeaderboardComplaintResponses, tutorLeaderboardAnsweredMoreFeedbackRequests, false);
    }

    /**
     * Returns tutor leaderboards for the specified course.
     *
     * @param course - course for which leaderboard is fetched
     * @param exam   - the exam for which the leaderboard will be fetched
     * @return list of tutor leaderboard objects
     */
    public List<TutorLeaderboardDTO> getExamLeaderboard(Course course, Exam exam) {
        var tutors = userRepository.getTutors(course);
        var tutorLeaderboardAssessments = resultRepository.findTutorLeaderboardAssessmentByExamId(exam.getId());
        var tutorLeaderboardComplaints = complaintRepository.findTutorLeaderboardComplaintsByExamId(exam.getId());
        var tutorLeaderboardComplaintResponses = complaintRepository.findTutorLeaderboardComplaintResponsesByExamId(exam.getId());
        return aggregateTutorLeaderboardData(tutors, tutorLeaderboardAssessments, tutorLeaderboardComplaints, new ArrayList<>(), tutorLeaderboardComplaintResponses,
                new ArrayList<>(), true);
    }

    /**
     * Returns tutor leaderboards for the specified exercise.
     *
     * @param exercise exercise for which leaderboard is created
     * @return list of tutor leaderboard objects
     */
    public List<TutorLeaderboardDTO> getExerciseLeaderboard(Exercise exercise) {
        var tutors = userRepository.getTutors(exercise.getCourseViaExerciseGroupOrCourseMember());
        var tutorLeaderboardAssessments = resultRepository.findTutorLeaderboardAssessmentByExerciseId(exercise.getId());
        var tutorLeaderboardComplaints = complaintRepository.findTutorLeaderboardComplaintsByExerciseId(exercise.getId());
        var tutorLeaderboardMoreFeedbackRequests = complaintRepository.findTutorLeaderboardMoreFeedbackRequestsByExerciseId(exercise.getId());
        var tutorLeaderboardComplaintResponses = complaintRepository.findTutorLeaderboardComplaintResponsesByExerciseId(exercise.getId());
        var tutorLeaderboardAnsweredMoreFeedbackRequests = complaintRepository.findTutorLeaderboardAnsweredMoreFeedbackRequestsByExerciseId(exercise.getId());
        return aggregateTutorLeaderboardData(tutors, tutorLeaderboardAssessments, tutorLeaderboardComplaints, tutorLeaderboardMoreFeedbackRequests,
                tutorLeaderboardComplaintResponses, tutorLeaderboardAnsweredMoreFeedbackRequests, exercise.isExamExercise());
    }

    @NotNull
    private List<TutorLeaderboardDTO> aggregateTutorLeaderboardData(List<User> tutors, List<TutorLeaderboardAssessments> assessments, List<TutorLeaderboardComplaints> complaints,
            List<TutorLeaderboardMoreFeedbackRequests> feedbackRequests, List<TutorLeaderboardComplaintResponses> complaintResponses,
            List<TutorLeaderboardAnsweredMoreFeedbackRequests> answeredFeedbackRequests, boolean isExam) {

        var assessmentsMap = assessments.stream().collect(Collectors.toMap(TutorLeaderboardAssessments::getKey, value -> value));
        var complaintsMap = complaints.stream().collect(Collectors.toMap(TutorLeaderboardComplaints::getKey, value -> value));
        var feedbackRequestsMap = feedbackRequests.stream().collect(Collectors.toMap(TutorLeaderboardMoreFeedbackRequests::getKey, value -> value));
        var complaintResponsesMap = complaintResponses.stream().collect(Collectors.toMap(TutorLeaderboardComplaintResponses::getKey, value -> value));
        var answeredFeedbackRequestsMap = answeredFeedbackRequests.stream().collect(Collectors.toMap(TutorLeaderboardAnsweredMoreFeedbackRequests::getKey, value -> value));

        List<TutorLeaderboardDTO> tutorLeaderBoardEntries = new ArrayList<>();

        for (User tutor : tutors) {
            long numberOfAssessments = 0L;
            long numberOfAcceptedComplaints = 0L;
            long numberOfTutorComplaints = 0L;
            long numberOfNotAnsweredMoreFeedbackRequests = 0L;
            long numberOfComplaintResponses = 0L;
            long numberOfAnsweredMoreFeedbackRequests = 0L;
            long numberOfTutorMoreFeedbackRequests = 0L;
            double points = 0.0;

            var assessmentsOfTutor = assessmentsMap.getOrDefault(tutor.getId(), new TutorLeaderboardAssessments());
            numberOfAssessments += assessmentsOfTutor.getAssessments();
            points += assessmentsOfTutor.getPoints();

            var complaintsAboutTutor = complaintsMap.getOrDefault(tutor.getId(), new TutorLeaderboardComplaints());
            numberOfTutorComplaints += complaintsAboutTutor.getAllComplaints();
            numberOfAcceptedComplaints += complaintsAboutTutor.getAcceptedComplaints();
            // accepted complaints count 2x negatively
            points -= 2.0 * complaintsAboutTutor.getPoints();

            var complaintResponsesOfTutor = complaintResponsesMap.getOrDefault(tutor.getId(), new TutorLeaderboardComplaintResponses());
            numberOfComplaintResponses += complaintResponsesOfTutor.getComplaintResponses();
            // resolved complaints count 2x
            points += 2.0 * complaintResponsesOfTutor.getPoints();

            if (!isExam) {
                var feedbackRequestsAboutTutor = feedbackRequestsMap.getOrDefault(tutor.getId(), new TutorLeaderboardMoreFeedbackRequests());
                numberOfNotAnsweredMoreFeedbackRequests += feedbackRequestsAboutTutor.getNotAnsweredRequests();
                numberOfTutorMoreFeedbackRequests += feedbackRequestsAboutTutor.getAllRequests();
                // not answered requests count only 1x negatively
                points -= feedbackRequestsAboutTutor.getPoints();

                var answeredFeedbackRequestsOfTutor = answeredFeedbackRequestsMap.getOrDefault(tutor.getId(), new TutorLeaderboardAnsweredMoreFeedbackRequests());
                numberOfAnsweredMoreFeedbackRequests += answeredFeedbackRequestsOfTutor.getAnsweredRequests();
                // answered requests doesn't count, because it only means that the tutor repaired the negative points
            }

            var leaderboardEntry = new TutorLeaderboardDTO(tutor.getId(), tutor.getName(), numberOfAssessments, numberOfAcceptedComplaints, numberOfTutorComplaints,
                    numberOfNotAnsweredMoreFeedbackRequests, numberOfComplaintResponses, numberOfAnsweredMoreFeedbackRequests, numberOfTutorMoreFeedbackRequests, points,
                    assessmentsOfTutor.getAverageScore(), assessmentsOfTutor.getAverageRating(), assessmentsOfTutor.getNumberOfRatings());
            tutorLeaderBoardEntries.add(leaderboardEntry);
        }
        return tutorLeaderBoardEntries;
    }
}
