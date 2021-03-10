package de.tum.in.www1.artemis.service;

import java.util.ArrayList;
import java.util.List;

import javax.validation.constraints.NotNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.exam.Exam;
import de.tum.in.www1.artemis.domain.leaderboard.tutor.*;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.web.rest.ProgrammingExerciseGradingResource;
import de.tum.in.www1.artemis.web.rest.dto.TutorLeaderboardDTO;

@Service
public class TutorLeaderboardService {

    private final ResultRepository resultRepository;

    private final ComplaintRepository complaintRepository;

    private final UserRepository userRepository;

    private final Logger log = LoggerFactory.getLogger(ProgrammingExerciseGradingResource.class);

    public TutorLeaderboardService(ResultRepository resultRepository, ComplaintRepository complaintRepository, UserRepository userRepository) {
        this.resultRepository = resultRepository;
        this.complaintRepository = complaintRepository;
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
        String groupName = course.getTeachingAssistantGroupName();

        long start = System.currentTimeMillis();
        List<TutorLeaderboardAssessment> tutorLeaderboardAssessments = resultRepository.findTutorLeaderboardAssessmentByCourseId(course.getId());
        long end = System.currentTimeMillis();
        log.info("Finished >>resultRepository.findTutorLeaderboardAssessmentByCourseId<< call for course " + course.getId() + " in " + (end - start) + "ms");

        start = System.currentTimeMillis();
        List<TutorLeaderboardComplaints> tutorLeaderboardComplaints = complaintRepository.findTutorLeaderboardComplaintsByCourseId(groupName, course.getId());
        end = System.currentTimeMillis();
        log.info("Finished >>complaintRepository.findTutorLeaderboardComplaintsByCourseId<< call for course " + course.getId() + " in " + (end - start) + "ms");

        start = System.currentTimeMillis();
        List<TutorLeaderboardMoreFeedbackRequests> tutorLeaderboardMoreFeedbackRequests = complaintRepository.findTutorLeaderboardMoreFeedbackRequestsByCourseId(groupName,
                course.getId());
        end = System.currentTimeMillis();
        log.info("Finished >>complaintRepository.findTutorLeaderboardMoreFeedbackRequestsByCourseId<< call for course " + course.getId() + " in " + (end - start) + "ms");

        start = System.currentTimeMillis();
        List<TutorLeaderboardComplaintResponses> tutorLeaderboardComplaintResponses = complaintRepository.findTutorLeaderboardComplaintResponsesByCourseId(groupName,
                course.getId());
        end = System.currentTimeMillis();
        log.info("Finished >>complaintRepository.findTutorLeaderboardComplaintResponsesByCourseId<< call for course " + course.getId() + " in " + (end - start) + "ms");

        start = System.currentTimeMillis();
        List<TutorLeaderboardAnsweredMoreFeedbackRequests> tutorLeaderboardAnsweredMoreFeedbackRequests = complaintRepository
                .findTutorLeaderboardAnsweredMoreFeedbackRequestsByCourseId(groupName, course.getId());
        end = System.currentTimeMillis();
        log.info("Finished >>complaintRepository.findTutorLeaderboardAnsweredMoreFeedbackRequestsByCourseId<< call for course " + course.getId() + " in " + (end - start) + "ms");

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
        long start = System.currentTimeMillis();
        List<TutorLeaderboardAssessment> tutorLeaderboardAssessments = resultRepository.findTutorLeaderboardAssessmentByExerciseId(groupName, exercise.getId());
        long end = System.currentTimeMillis();
        log.info("Finished >>resultRepository.findTutorLeaderboardAssessmentByExerciseId<< call for exercise " + exercise.getId() + " in " + (end - start) + "ms");
        start = System.currentTimeMillis();
        List<TutorLeaderboardComplaints> tutorLeaderboardComplaints = complaintRepository.findTutorLeaderboardComplaintsByExerciseId(groupName, exercise.getId());
        end = System.currentTimeMillis();
        log.info("Finished >>complaintRepository.findTutorLeaderboardComplaintsByExerciseId<< call for exercise " + exercise.getId() + " in " + (end - start) + "ms");
        start = System.currentTimeMillis();
        List<TutorLeaderboardMoreFeedbackRequests> tutorLeaderboardMoreFeedbackRequests = complaintRepository.findTutorLeaderboardMoreFeedbackRequestsByExerciseId(groupName,
                exercise.getId());
        end = System.currentTimeMillis();
        log.info("Finished >>complaintRepository.findTutorLeaderboardMoreFeedbackRequestsByExerciseId<< call for exercise " + exercise.getId() + " in " + (end - start) + "ms");
        start = System.currentTimeMillis();
        List<TutorLeaderboardComplaintResponses> tutorLeaderboardComplaintResponses = complaintRepository.findTutorLeaderboardComplaintResponsesByExerciseId(groupName,
                exercise.getId());
        end = System.currentTimeMillis();
        log.info("Finished >>complaintRepository.findTutorLeaderboardComplaintResponsesByExerciseId<< call for exercise " + exercise.getId() + " in " + (end - start) + "ms");
        start = System.currentTimeMillis();
        List<TutorLeaderboardAnsweredMoreFeedbackRequests> tutorLeaderboardAnsweredMoreFeedbackRequests = complaintRepository
                .findTutorLeaderboardAnsweredMoreFeedbackRequestsByExerciseId(groupName, exercise.getId());
        end = System.currentTimeMillis();
        log.info("Finished >>complaintRepository.findTutorLeaderboardAnsweredMoreFeedbackRequestsByExerciseId<< call for exercise " + exercise.getId() + " in " + (end - start)
                + "ms");

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
                if (tutor.getId().equals(assessment.getUserId())) {
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
