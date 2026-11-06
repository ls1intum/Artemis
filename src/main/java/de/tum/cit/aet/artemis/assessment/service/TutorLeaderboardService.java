package de.tum.cit.aet.artemis.assessment.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.jspecify.annotations.NonNull;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.assessment.dto.tutor.TutorLeaderboardAnsweredMoreFeedbackRequestsDTO;
import de.tum.cit.aet.artemis.assessment.dto.tutor.TutorLeaderboardAssessmentsDTO;
import de.tum.cit.aet.artemis.assessment.dto.tutor.TutorLeaderboardComplaintResponsesDTO;
import de.tum.cit.aet.artemis.assessment.dto.tutor.TutorLeaderboardComplaintsDTO;
import de.tum.cit.aet.artemis.assessment.dto.tutor.TutorLeaderboardMoreFeedbackRequestsDTO;
import de.tum.cit.aet.artemis.assessment.repository.ComplaintRepository;
import de.tum.cit.aet.artemis.assessment.repository.ResultRepository;
import de.tum.cit.aet.artemis.core.domain.CourseRole;
import de.tum.cit.aet.artemis.core.dto.TutorLeaderboardDTO;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;

@Profile(PROFILE_CORE)
@Lazy
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
     *
     * @param exerciseIdsOfCourse - the ids of the exercises which belong to the course
     * @param course              - course for which leaderboard is created
     * @return list of tutor leaderboard objects
     */
    public List<TutorLeaderboardDTO> getCourseLeaderboard(Course course, Set<Long> exerciseIdsOfCourse) {
        List<TutorLeaderboardAssessmentsDTO> tutorLeaderboardAssessmentDTOS = List.of();
        List<TutorLeaderboardComplaintsDTO> tutorLeaderboardComplaints = List.of();
        List<TutorLeaderboardComplaintResponsesDTO> tutorLeaderboardComplaintResponses = List.of();
        List<TutorLeaderboardMoreFeedbackRequestsDTO> tutorLeaderboardMoreFeedbackRequests = List.of();
        List<TutorLeaderboardAnsweredMoreFeedbackRequestsDTO> tutorLeaderboardAnsweredMoreFeedbackRequests = List.of();
        // only invoke the query for non empty exercise sets to avoid performance issues
        if (!exerciseIdsOfCourse.isEmpty()) {
            tutorLeaderboardAssessmentDTOS = resultRepository.findTutorLeaderboardAssessmentByCourseId(exerciseIdsOfCourse);
            tutorLeaderboardComplaints = complaintRepository.findTutorLeaderboardComplaintsByCourseId(exerciseIdsOfCourse);
            tutorLeaderboardComplaintResponses = complaintRepository.findTutorLeaderboardComplaintResponsesByCourseId(exerciseIdsOfCourse);
            tutorLeaderboardMoreFeedbackRequests = complaintRepository.findTutorLeaderboardMoreFeedbackRequestsByCourseId(exerciseIdsOfCourse);
            tutorLeaderboardAnsweredMoreFeedbackRequests = complaintRepository.findTutorLeaderboardAnsweredMoreFeedbackRequestsByCourseId(exerciseIdsOfCourse);
        }

        return aggregateTutorLeaderboardData(course, tutorLeaderboardAssessmentDTOS, tutorLeaderboardComplaints, tutorLeaderboardMoreFeedbackRequests,
                tutorLeaderboardComplaintResponses, tutorLeaderboardAnsweredMoreFeedbackRequests, false);
    }

    /**
     * Returns tutor leaderboards for the specified course.
     *
     * @param course      - course for which leaderboard is fetched
     * @param exerciseIds - the exercise ids for which the leaderboard will be fetched
     * @return list of tutor leaderboard objects
     */
    public List<TutorLeaderboardDTO> getExamLeaderboard(Course course, Collection<Long> exerciseIds) {
        var tutorLeaderboardAssessments = resultRepository.findTutorLeaderboardAssessmentByExerciseIds(exerciseIds);
        var tutorLeaderboardComplaints = complaintRepository.findTutorLeaderboardComplaintsByExerciseIds(exerciseIds);
        var tutorLeaderboardComplaintResponses = complaintRepository.findTutorLeaderboardComplaintResponsesByExerciseIds(exerciseIds);
        return aggregateTutorLeaderboardData(course, tutorLeaderboardAssessments, tutorLeaderboardComplaints, new ArrayList<>(), tutorLeaderboardComplaintResponses,
                new ArrayList<>(), true);
    }

    /**
     * Returns tutor leaderboards for the specified exercise.
     *
     * @param exercise exercise for which leaderboard is created
     * @return list of tutor leaderboard objects
     */
    public List<TutorLeaderboardDTO> getExerciseLeaderboard(Exercise exercise) {
        var tutorLeaderboardAssessments = resultRepository.findTutorLeaderboardAssessmentByExerciseId(exercise.getId());
        var tutorLeaderboardComplaints = complaintRepository.findTutorLeaderboardComplaintsByExerciseId(exercise.getId());
        var tutorLeaderboardMoreFeedbackRequests = complaintRepository.findTutorLeaderboardMoreFeedbackRequestsByExerciseId(exercise.getId());
        var tutorLeaderboardComplaintResponses = complaintRepository.findTutorLeaderboardComplaintResponsesByExerciseId(exercise.getId());
        var tutorLeaderboardAnsweredMoreFeedbackRequests = complaintRepository.findTutorLeaderboardAnsweredMoreFeedbackRequestsByExerciseId(exercise.getId());
        return aggregateTutorLeaderboardData(exercise.getCourseViaExerciseGroupOrCourseMemberElseThrow(), tutorLeaderboardAssessments, tutorLeaderboardComplaints,
                tutorLeaderboardMoreFeedbackRequests, tutorLeaderboardComplaintResponses, tutorLeaderboardAnsweredMoreFeedbackRequests, exercise.isExamExercise());
    }

    @NonNull
    private List<TutorLeaderboardDTO> aggregateTutorLeaderboardData(Course course, List<TutorLeaderboardAssessmentsDTO> assessments, List<TutorLeaderboardComplaintsDTO> complaints,
            List<TutorLeaderboardMoreFeedbackRequestsDTO> feedbackRequests, List<TutorLeaderboardComplaintResponsesDTO> complaintResponses,
            List<TutorLeaderboardAnsweredMoreFeedbackRequestsDTO> answeredFeedbackRequests, boolean isExam) {

        var tutors = userRepository.getTutors(course);
        var staff = new HashSet<>(tutors);
        staff.addAll(userRepository.findAllByCourseIdAndCourseRolesIn(course.getId(), Set.of(CourseRole.EDITOR, CourseRole.INSTRUCTOR)));

        var assessmentsMap = assessments.stream().collect(Collectors.toMap(TutorLeaderboardAssessmentsDTO::userId, value -> value));
        var complaintsMap = complaints.stream().collect(Collectors.toMap(TutorLeaderboardComplaintsDTO::userId, value -> value));
        var feedbackRequestsMap = feedbackRequests.stream().collect(Collectors.toMap(TutorLeaderboardMoreFeedbackRequestsDTO::userId, value -> value));
        var complaintResponsesMap = complaintResponses.stream().collect(Collectors.toMap(TutorLeaderboardComplaintResponsesDTO::userId, value -> value));
        var answeredFeedbackRequestsMap = answeredFeedbackRequests.stream().collect(Collectors.toMap(TutorLeaderboardAnsweredMoreFeedbackRequestsDTO::userId, value -> value));

        List<TutorLeaderboardDTO> tutorLeaderBoardEntries = new ArrayList<>();

        for (User tutor : staff) {
            long numberOfAssessments = 0L;
            long numberOfAcceptedComplaints = 0L;
            long numberOfTutorComplaints = 0L;
            long numberOfNotAnsweredMoreFeedbackRequests = 0L;
            long numberOfComplaintResponses = 0L;
            long numberOfAnsweredMoreFeedbackRequests = 0L;
            long numberOfTutorMoreFeedbackRequests = 0L;
            double points = 0.0;

            var assessmentsOfTutor = assessmentsMap.getOrDefault(tutor.getId(), new TutorLeaderboardAssessmentsDTO());
            numberOfAssessments += assessmentsOfTutor.assessments();
            points += assessmentsOfTutor.points();

            var complaintsAboutTutor = complaintsMap.getOrDefault(tutor.getId(), new TutorLeaderboardComplaintsDTO());
            numberOfTutorComplaints += complaintsAboutTutor.allComplaints();
            numberOfAcceptedComplaints += complaintsAboutTutor.acceptedComplaints();
            // accepted complaints count 2x negatively
            points -= 2.0 * complaintsAboutTutor.points();

            var complaintResponsesOfTutor = complaintResponsesMap.getOrDefault(tutor.getId(), new TutorLeaderboardComplaintResponsesDTO());
            numberOfComplaintResponses += complaintResponsesOfTutor.complaintResponses();
            // resolved complaints count 2x
            points += 2.0 * complaintResponsesOfTutor.points();

            if (!isExam) {
                var feedbackRequestsAboutTutor = feedbackRequestsMap.getOrDefault(tutor.getId(), new TutorLeaderboardMoreFeedbackRequestsDTO());
                numberOfNotAnsweredMoreFeedbackRequests += feedbackRequestsAboutTutor.notAnsweredRequests();
                numberOfTutorMoreFeedbackRequests += feedbackRequestsAboutTutor.allRequests();
                // not answered requests count only 1x negatively
                points -= feedbackRequestsAboutTutor.points();

                var answeredFeedbackRequestsOfTutor = answeredFeedbackRequestsMap.getOrDefault(tutor.getId(), new TutorLeaderboardAnsweredMoreFeedbackRequestsDTO());
                numberOfAnsweredMoreFeedbackRequests += answeredFeedbackRequestsOfTutor.answeredRequests();
                // answered requests doesn't count, because it only means that the tutor repaired the negative points
            }

            // Keep every tutor, including those who have not assessed yet. Higher staff only appear when
            // they have recorded activity in this leaderboard's exercise, course or exam scope. Net zero points
            // (for example points cancelled out by penalties) must not hide that activity.
            boolean hasActivity = numberOfAssessments > 0 || numberOfComplaintResponses > 0 || numberOfTutorComplaints > 0 || numberOfTutorMoreFeedbackRequests > 0
                    || numberOfAnsweredMoreFeedbackRequests > 0;
            if (!tutors.contains(tutor) && !hasActivity) {
                continue;
            }

            var leaderboardEntry = new TutorLeaderboardDTO(tutor.getId(), tutor.getName(), numberOfAssessments, numberOfAcceptedComplaints, numberOfTutorComplaints,
                    numberOfNotAnsweredMoreFeedbackRequests, numberOfComplaintResponses, numberOfAnsweredMoreFeedbackRequests, numberOfTutorMoreFeedbackRequests, points,
                    assessmentsOfTutor.averageScore(), assessmentsOfTutor.averageRating(), assessmentsOfTutor.numberOfRatings());
            tutorLeaderBoardEntries.add(leaderboardEntry);
        }
        return tutorLeaderBoardEntries;
    }
}
