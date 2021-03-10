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

    private final TutorLeaderboardAssessmentViewRepository tutorLeaderboardAssessmentViewRepository;

    private final TutorLeaderboardComplaintsViewRepository tutorLeaderboardComplaintsViewRepository;

    private final TutorLeaderboardMoreFeedbackRequestsViewRepository tutorLeaderboardMoreFeedbackRequestsViewRepository;

    private final TutorLeaderboardComplaintResponsesViewRepository tutorLeaderboardComplaintResponsesViewRepository;

    private final TutorLeaderboardAnsweredMoreFeedbackRequestsViewRepository tutorLeaderboardAnsweredMoreFeedbackRequestsViewRepository;

    private final UserRepository userRepository;

    public TutorLeaderboardService(TutorLeaderboardAssessmentViewRepository tutorLeaderboardAssessmentViewRepository,
            TutorLeaderboardComplaintsViewRepository tutorLeaderboardComplaintsViewRepository,
            TutorLeaderboardMoreFeedbackRequestsViewRepository tutorLeaderboardMoreFeedbackRequestsViewRepository,
            TutorLeaderboardComplaintResponsesViewRepository tutorLeaderboardComplaintResponsesViewRepository,
            TutorLeaderboardAnsweredMoreFeedbackRequestsViewRepository tutorLeaderboardAnsweredMoreFeedbackRequestsViewRepository, UserRepository userRepository) {
        this.tutorLeaderboardAssessmentViewRepository = tutorLeaderboardAssessmentViewRepository;
        this.tutorLeaderboardComplaintsViewRepository = tutorLeaderboardComplaintsViewRepository;
        this.tutorLeaderboardMoreFeedbackRequestsViewRepository = tutorLeaderboardMoreFeedbackRequestsViewRepository;
        this.tutorLeaderboardComplaintResponsesViewRepository = tutorLeaderboardComplaintResponsesViewRepository;
        this.tutorLeaderboardAnsweredMoreFeedbackRequestsViewRepository = tutorLeaderboardAnsweredMoreFeedbackRequestsViewRepository;
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

        List<TutorLeaderboardAssessmentView> tutorLeaderboardAssessments = tutorLeaderboardAssessmentViewRepository.findAllByCourseId(course.getId());
        List<TutorLeaderboardComplaintsView> tutorLeaderboardComplaints = tutorLeaderboardComplaintsViewRepository.findAllByCourseId(course.getId());
        List<TutorLeaderboardMoreFeedbackRequestsView> tutorLeaderboardMoreFeedbackRequests = tutorLeaderboardMoreFeedbackRequestsViewRepository.findAllByCourseId(course.getId());
        List<TutorLeaderboardComplaintResponsesView> tutorLeaderboardComplaintResponses = tutorLeaderboardComplaintResponsesViewRepository.findAllByCourseId(course.getId());
        List<TutorLeaderboardAnsweredMoreFeedbackRequestsView> tutorLeaderboardAnsweredMoreFeedbackRequests = tutorLeaderboardAnsweredMoreFeedbackRequestsViewRepository
                .findAllByCourseId(course.getId());

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
        // TODO: get the exam leaderboard. We do not want to use the existing views. We do not yet support the exam leaderboard
        // TODO: remove as soon as the above calls work
        List<TutorLeaderboardAssessmentView> tutorLeaderboardAssessments = new ArrayList<>();
        List<TutorLeaderboardComplaintsView> tutorLeaderboardComplaints = new ArrayList<>();
        List<TutorLeaderboardComplaintResponsesView> tutorLeaderboardComplaintResponses = new ArrayList<>();

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

        List<TutorLeaderboardAssessmentView> tutorLeaderboardAssessments = tutorLeaderboardAssessmentViewRepository.findAllByLeaderboardId_ExerciseId(exercise.getId());
        List<TutorLeaderboardComplaintsView> tutorLeaderboardComplaints = tutorLeaderboardComplaintsViewRepository.findAllByLeaderboardId_ExerciseId(exercise.getId());
        List<TutorLeaderboardMoreFeedbackRequestsView> tutorLeaderboardMoreFeedbackRequests = tutorLeaderboardMoreFeedbackRequestsViewRepository
                .findAllByLeaderboardId_ExerciseId(exercise.getId());
        List<TutorLeaderboardComplaintResponsesView> tutorLeaderboardComplaintResponses = tutorLeaderboardComplaintResponsesViewRepository
                .findAllByLeaderboardId_ExerciseId(exercise.getId());
        List<TutorLeaderboardAnsweredMoreFeedbackRequestsView> tutorLeaderboardAnsweredMoreFeedbackRequests = tutorLeaderboardAnsweredMoreFeedbackRequestsViewRepository
                .findAllByLeaderboardId_ExerciseId(exercise.getId());

        return aggregateTutorLeaderboardData(tutors, tutorLeaderboardAssessments, tutorLeaderboardComplaints, tutorLeaderboardMoreFeedbackRequests,
                tutorLeaderboardComplaintResponses, tutorLeaderboardAnsweredMoreFeedbackRequests);
    }

    @NotNull
    private List<TutorLeaderboardDTO> aggregateTutorLeaderboardData(List<User> tutors, List<TutorLeaderboardAssessmentView> tutorLeaderboardAssessments,
            List<TutorLeaderboardComplaintsView> tutorLeaderboardComplaints, List<TutorLeaderboardMoreFeedbackRequestsView> tutorLeaderboardMoreFeedbackRequests,
            List<TutorLeaderboardComplaintResponsesView> tutorLeaderboardComplaintResponses,
            List<TutorLeaderboardAnsweredMoreFeedbackRequestsView> tutorLeaderboardAnsweredMoreFeedbackRequests) {

        List<TutorLeaderboardDTO> tutorLeaderBoardEntries = new ArrayList<>();
        for (User tutor : tutors) {

            long numberOfAssessments = 0L;
            long numberOfAcceptedComplaints = 0L;
            long numberOfTutorComplaints = 0L;
            long numberOfNotAnsweredMoreFeedbackRequests = 0L;
            long numberOfComplaintResponses = 0L;
            long numberOfAnsweredMoreFeedbackRequests = 0L;
            long numberOfTutorMoreFeedbackRequests = 0L;
            Long points = 0L;

            for (TutorLeaderboardAssessmentView assessmentsView : tutorLeaderboardAssessments) {
                if (tutor.getId().equals(assessmentsView.getUserId())) {
                    numberOfAssessments += assessmentsView.getAssessments();
                    if (assessmentsView.getPoints() != null) {   // this can happen when max points is null, then we could simply count the assessments
                        points += assessmentsView.getPoints();
                    }
                    else {
                        points += assessmentsView.getAssessments();
                    }
                }
            }

            for (TutorLeaderboardComplaintsView complaintsView : tutorLeaderboardComplaints) {
                if (tutor.getId().equals(complaintsView.getUserId())) {
                    numberOfTutorComplaints += complaintsView.getAllComplaints();
                    numberOfAcceptedComplaints += complaintsView.getAcceptedComplaints();
                    // accepted complaints count 2x negatively
                    if (complaintsView.getPoints() != null) {   // this can happen when max points is null, then we could simply count the accepted complaints
                        points -= 2 * complaintsView.getPoints();
                    }
                    else {
                        points -= 2 * complaintsView.getAcceptedComplaints();
                    }
                }
            }

            for (TutorLeaderboardMoreFeedbackRequestsView moreFeedbackRequestsView : tutorLeaderboardMoreFeedbackRequests) {
                if (tutor.getId().equals(moreFeedbackRequestsView.getUserId())) {
                    numberOfNotAnsweredMoreFeedbackRequests += moreFeedbackRequestsView.getNotAnsweredRequests();
                    numberOfTutorMoreFeedbackRequests += moreFeedbackRequestsView.getAllRequests();
                    // not answered requests count only 1x negatively
                    if (moreFeedbackRequestsView.getPoints() != null) {   // this can happen when max points is null, then we could simply count the not answered
                                                                          // requests
                        points -= moreFeedbackRequestsView.getPoints();
                    }
                    else {
                        points -= moreFeedbackRequestsView.getNotAnsweredRequests();
                    }
                }
            }

            for (TutorLeaderboardComplaintResponsesView complaintResponsesView : tutorLeaderboardComplaintResponses) {
                if (tutor.getId().equals(complaintResponsesView.getUserId())) {
                    numberOfComplaintResponses += complaintResponsesView.getComplaintResponses();
                    // resolved complaints count 2x
                    if (complaintResponsesView.getPoints() != null) {   // this can happen when max points is null, then we could simply count the complaint responses
                        points += 2 * complaintResponsesView.getPoints();
                    }
                    else {
                        points += 2 * complaintResponsesView.getComplaintResponses();
                    }
                }
            }

            for (TutorLeaderboardAnsweredMoreFeedbackRequestsView moreFeedbackRequestsView : tutorLeaderboardAnsweredMoreFeedbackRequests) {
                if (tutor.getId().equals(moreFeedbackRequestsView.getUserId())) {
                    numberOfAnsweredMoreFeedbackRequests += moreFeedbackRequestsView.getAnsweredRequests();
                    // answered requests doesn't count, because it only means that the tutor repaired the negative points
                }
            }

            tutorLeaderBoardEntries.add(new TutorLeaderboardDTO(tutor.getId(), tutor.getName(), numberOfAssessments, numberOfAcceptedComplaints, numberOfTutorComplaints,
                    numberOfNotAnsweredMoreFeedbackRequests, numberOfComplaintResponses, numberOfAnsweredMoreFeedbackRequests, numberOfTutorMoreFeedbackRequests, points));
        }
        return tutorLeaderBoardEntries;
    }
}
