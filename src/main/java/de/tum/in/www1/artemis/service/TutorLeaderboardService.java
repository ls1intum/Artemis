package de.tum.in.www1.artemis.service;

import java.util.ArrayList;
import java.util.List;

import javax.validation.constraints.NotNull;

import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.exam.Exam;
import de.tum.in.www1.artemis.domain.leaderboard.tutor.*;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.web.rest.dto.TutorLeaderboardDTO;

@Service
public class TutorLeaderboardService {

    private final TutorLeaderboardAssessmentRepository tutorLeaderboardAssessmentRepository;

    private final TutorLeaderboardComplaintsRepository tutorLeaderboardComplaintsRepository;

    private final TutorLeaderboardMoreFeedbackRequestsRepository tutorLeaderboardMoreFeedbackRequestsRepository;

    private final TutorLeaderboardComplaintResponsesRepository tutorLeaderboardComplaintResponsesRepository;

    private final TutorLeaderboardAnsweredMoreFeedbackRequestsRepository tutorLeaderboardAnsweredMoreFeedbackRequestsRepository;

    private final UserRepository userRepository;

    public TutorLeaderboardService(TutorLeaderboardAssessmentRepository tutorLeaderboardAssessmentRepository,
            TutorLeaderboardComplaintsRepository tutorLeaderboardComplaintsRepository,
            TutorLeaderboardMoreFeedbackRequestsRepository tutorLeaderboardMoreFeedbackRequestsRepository,
            TutorLeaderboardComplaintResponsesRepository tutorLeaderboardComplaintResponsesRepository,
            TutorLeaderboardAnsweredMoreFeedbackRequestsRepository tutorLeaderboardAnsweredMoreFeedbackRequestsRepository, UserRepository userRepository) {
        this.tutorLeaderboardAssessmentRepository = tutorLeaderboardAssessmentRepository;
        this.tutorLeaderboardComplaintsRepository = tutorLeaderboardComplaintsRepository;
        this.tutorLeaderboardMoreFeedbackRequestsRepository = tutorLeaderboardMoreFeedbackRequestsRepository;
        this.tutorLeaderboardComplaintResponsesRepository = tutorLeaderboardComplaintResponsesRepository;
        this.tutorLeaderboardAnsweredMoreFeedbackRequestsRepository = tutorLeaderboardAnsweredMoreFeedbackRequestsRepository;
        this.userRepository = userRepository;
    }

    /**
     * Returns tutor leaderboards for the specified course.
     *
     * @param course course for which leaderboard is created
     * @return list of tutor leaderboard objects
     */
    public List<TutorLeaderboardDTO> getCourseLeaderboard(Course course) {

        List<User> tutors = userRepository.getTutors(course);

        List<TutorLeaderboardAssessment> tutorLeaderboardAssessments = tutorLeaderboardAssessmentRepository.findTutorLeaderboardAssessmentByCourseId(course.getId());
        List<TutorLeaderboardComplaints> tutorLeaderboardComplaints = tutorLeaderboardComplaintsRepository.findTutorLeaderboardComplaintsByCourseId(course.getId());
        List<TutorLeaderboardMoreFeedbackRequests> tutorLeaderboardMoreFeedbackRequests = tutorLeaderboardMoreFeedbackRequestsRepository
                .findTutorLeaderboardMoreFeedbackRequestsByCourseId(course.getId());
        List<TutorLeaderboardComplaintResponses> tutorLeaderboardComplaintResponses = tutorLeaderboardComplaintResponsesRepository
                .findTutorLeaderboardComplaintResponsesByCourseId(course.getId());
        List<TutorLeaderboardAnsweredMoreFeedbackRequests> tutorLeaderboardAnsweredMoreFeedbackRequests = tutorLeaderboardAnsweredMoreFeedbackRequestsRepository
                .findTutorLeaderboardAnsweredMoreFeedbackRequestsByCourseId(course.getId());

        return aggregateTutorLeaderboardData(tutors, tutorLeaderboardAssessments, tutorLeaderboardComplaints, tutorLeaderboardMoreFeedbackRequests,
                tutorLeaderboardComplaintResponses, tutorLeaderboardAnsweredMoreFeedbackRequests);
    }

    /**
     * Returns tutor leaderboards for the specified course.
     *
     * @param course - course for which leaderboard is fetched
     * @param exam   - the exam for which the leaderboard will be fetched TODO
     * @return list of tutor leaderboard objects
     */
    public List<TutorLeaderboardDTO> getExamLeaderboard(Course course, Exam exam) {

        List<User> tutors = userRepository.getTutors(course);
        // TODO: get the exam leaderboard. We do not want to use the existing s. We do not yet support the exam leaderboard
        // TODO: remove as soon as the above calls work
        List<TutorLeaderboardAssessment> tutorLeaderboardAssessments = new ArrayList<>();
        List<TutorLeaderboardComplaints> tutorLeaderboardComplaints = new ArrayList<>();
        List<TutorLeaderboardComplaintResponses> tutorLeaderboardComplaintResponses = new ArrayList<>();

        return aggregateTutorLeaderboardData(tutors, tutorLeaderboardAssessments, tutorLeaderboardComplaints, new ArrayList<>(), tutorLeaderboardComplaintResponses,
                new ArrayList<>());
    }

    /**
     * Returns tutor leaderboards for the specified exercise.
     *
     * @param exercise exercise for which leaderboard is created
     * @return list of tutor leaderboard objects
     */
    public List<TutorLeaderboardDTO> getExerciseLeaderboard(Exercise exercise) {

        List<User> tutors = userRepository.getTutors(exercise.getCourseViaExerciseGroupOrCourseMember());
        String groupName = exercise.getCourseViaExerciseGroupOrCourseMember().getTeachingAssistantGroupName();
        List<TutorLeaderboardAssessment> tutorLeaderboardAssessments = tutorLeaderboardAssessmentRepository.findTutorLeaderboardAssessmentByExerciseId(groupName, exercise.getId());
        List<TutorLeaderboardComplaints> tutorLeaderboardComplaints = tutorLeaderboardComplaintsRepository.findTutorLeaderboardComplaintsByExerciseId(groupName, exercise.getId());
        List<TutorLeaderboardMoreFeedbackRequests> tutorLeaderboardMoreFeedbackRequests = tutorLeaderboardMoreFeedbackRequestsRepository
                .findTutorLeaderboardMoreFeedbackRequestsByExerciseId(groupName, exercise.getId());
        List<TutorLeaderboardComplaintResponses> tutorLeaderboardComplaintResponses = tutorLeaderboardComplaintResponsesRepository
                .findTutorLeaderboardComplaintResponsesByExerciseId(groupName, exercise.getId());
        List<TutorLeaderboardAnsweredMoreFeedbackRequests> tutorLeaderboardAnsweredMoreFeedbackRequests = tutorLeaderboardAnsweredMoreFeedbackRequestsRepository
                .findTutorLeaderboardAnsweredMoreFeedbackRequestsByExerciseId(groupName, exercise.getId());

        return aggregateTutorLeaderboardData(tutors, tutorLeaderboardAssessments, tutorLeaderboardComplaints, tutorLeaderboardMoreFeedbackRequests,
                tutorLeaderboardComplaintResponses, tutorLeaderboardAnsweredMoreFeedbackRequests);
    }

    @NotNull
    private List<TutorLeaderboardDTO> aggregateTutorLeaderboardData(List<User> tutors, List<TutorLeaderboardAssessment> tutorLeaderboardAssessments,
            List<TutorLeaderboardComplaints> tutorLeaderboardComplaints, List<TutorLeaderboardMoreFeedbackRequests> tutorLeaderboardMoreFeedbackRequests,
            List<TutorLeaderboardComplaintResponses> tutorLeaderboardComplaintResponses,
            List<TutorLeaderboardAnsweredMoreFeedbackRequests> tutorLeaderboardAnsweredMoreFeedbackRequests) {

        List<TutorLeaderboardDTO> tutorLeaderBoardEntries = new ArrayList<>();
        for (User tutor : tutors) {

            long numberOfAssessments = 0L;
            long numberOfAcceptedComplaints = 0L;
            long numberOfTutorComplaints = 0L;
            long numberOfNotAnsweredMoreFeedbackRequests = 0L;
            long numberOfComplaintResponses = 0L;
            long numberOfAnsweredMoreFeedbackRequests = 0L;
            long numberOfTutorMoreFeedbackRequests = 0L;
            long points = 0L;

            for (var assessment : tutorLeaderboardAssessments) {
                if (tutor.equals(assessment.getUser())) {
                    numberOfAssessments += assessment.getAssessments();
                    points += assessment.getPoints();
                }
            }

            for (TutorLeaderboardComplaints complaints : tutorLeaderboardComplaints) {
                if (tutor.getId().equals(complaints.getUserId())) {
                    numberOfTutorComplaints += complaints.getAllComplaints();
                    numberOfAcceptedComplaints += complaints.getAcceptedComplaints();
                    // accepted complaints count 2x negatively
                    points -= 2 * complaints.getPoints();

                }
            }

            for (TutorLeaderboardMoreFeedbackRequests moreFeedbackRequests : tutorLeaderboardMoreFeedbackRequests) {
                if (tutor.getId().equals(moreFeedbackRequests.getUserId())) {
                    numberOfNotAnsweredMoreFeedbackRequests += moreFeedbackRequests.getNotAnsweredRequests();
                    numberOfTutorMoreFeedbackRequests += moreFeedbackRequests.getAllRequests();
                    // not answered requests count only 1x negatively
                    points -= moreFeedbackRequests.getPoints();
                }
            }

            for (TutorLeaderboardComplaintResponses complaintResponses : tutorLeaderboardComplaintResponses) {
                if (tutor.getId().equals(complaintResponses.getUserId())) {
                    numberOfComplaintResponses += complaintResponses.getComplaintResponses();
                    // resolved complaints count 2x
                    points += 2 * complaintResponses.getPoints();
                }
            }

            for (TutorLeaderboardAnsweredMoreFeedbackRequests moreFeedbackRequests : tutorLeaderboardAnsweredMoreFeedbackRequests) {
                if (tutor.getId().equals(moreFeedbackRequests.getUserId())) {
                    numberOfAnsweredMoreFeedbackRequests += moreFeedbackRequests.getAnsweredRequests();
                    // answered requests doesn't count, because it only means that the tutor repaired the negative points
                }
            }

            tutorLeaderBoardEntries.add(new TutorLeaderboardDTO(tutor.getId(), tutor.getName(), numberOfAssessments, numberOfAcceptedComplaints, numberOfTutorComplaints,
                    numberOfNotAnsweredMoreFeedbackRequests, numberOfComplaintResponses, numberOfAnsweredMoreFeedbackRequests, numberOfTutorMoreFeedbackRequests, points));
        }
        return tutorLeaderBoardEntries;
    }
}
