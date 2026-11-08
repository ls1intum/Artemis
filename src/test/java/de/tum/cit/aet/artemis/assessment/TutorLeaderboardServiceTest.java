package de.tum.cit.aet.artemis.assessment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.assessment.dto.tutor.TutorLeaderboardAssessmentsDTO;
import de.tum.cit.aet.artemis.assessment.dto.tutor.TutorLeaderboardComplaintsDTO;
import de.tum.cit.aet.artemis.assessment.repository.ComplaintRepository;
import de.tum.cit.aet.artemis.assessment.repository.ResultRepository;
import de.tum.cit.aet.artemis.assessment.service.TutorLeaderboardService;
import de.tum.cit.aet.artemis.core.domain.CourseRole;
import de.tum.cit.aet.artemis.core.dto.TutorLeaderboardDTO;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.modeling.domain.ModelingExercise;

class TutorLeaderboardServiceTest {

    @ParameterizedTest
    @ValueSource(strings = { "course", "exercise", "exam" })
    void includesAssessingHigherStaffAndZeroPointTutors(String scope) {
        var users = mock(UserRepository.class);
        var results = mock(ResultRepository.class);
        var complaints = mock(ComplaintRepository.class);
        var service = new TutorLeaderboardService(results, complaints, users);
        var course = new Course();
        course.setId(1L);
        var exercise = new ModelingExercise();
        exercise.setId(2L);
        exercise.setCourse(course);
        var tutor = user(1L);
        var editor = user(2L);
        var instructor = user(3L);
        var inactiveInstructor = user(4L);
        var student = user(5L);
        var cancelledEditor = user(6L);
        var negativeInstructor = user(7L);
        when(users.getTutors(course)).thenReturn(Set.of(tutor));
        when(users.findAllByCourseIdAndCourseRolesIn(course.getId(), Set.of(CourseRole.EDITOR, CourseRole.INSTRUCTOR)))
                .thenReturn(Set.of(editor, instructor, inactiveInstructor, cancelledEditor, negativeInstructor));
        var assessments = List.of(assessment(editor, 10.0), assessment(instructor, 20.0), assessment(student, 30.0), assessment(cancelledEditor, 2.0));
        var complaintPoints = List.of(new TutorLeaderboardComplaintsDTO(cancelledEditor.getId(), 1L, 1L, 1.0),
                new TutorLeaderboardComplaintsDTO(negativeInstructor.getId(), 1L, 1L, 1.0));
        List<TutorLeaderboardDTO> leaderboard;
        switch (scope) {
            case "course" -> {
                when(results.findTutorLeaderboardAssessmentByCourseId(Set.of(exercise.getId()))).thenReturn(assessments);
                when(complaints.findTutorLeaderboardComplaintsByCourseId(Set.of(exercise.getId()))).thenReturn(complaintPoints);
                leaderboard = service.getCourseLeaderboard(course, Set.of(exercise.getId()));
            }
            case "exercise" -> {
                when(results.findTutorLeaderboardAssessmentByExerciseId(exercise.getId())).thenReturn(assessments);
                when(complaints.findTutorLeaderboardComplaintsByExerciseId(exercise.getId())).thenReturn(complaintPoints);
                leaderboard = service.getExerciseLeaderboard(exercise);
            }
            case "exam" -> {
                when(results.findTutorLeaderboardAssessmentByExerciseIds(Set.of(exercise.getId()))).thenReturn(assessments);
                when(complaints.findTutorLeaderboardComplaintsByExerciseIds(Set.of(exercise.getId()))).thenReturn(complaintPoints);
                leaderboard = service.getExamLeaderboard(course, Set.of(exercise.getId()));
            }
            default -> throw new IllegalArgumentException(scope);
        }
        assertThat(leaderboard).extracting(TutorLeaderboardDTO::userId).containsExactlyInAnyOrder(tutor.getId(), editor.getId(), instructor.getId(), negativeInstructor.getId());
        assertThat(leaderboard).filteredOn(entry -> entry.userId() == tutor.getId()).extracting(TutorLeaderboardDTO::points).containsExactly(0.0);
        assertThat(leaderboard).filteredOn(entry -> entry.userId() == negativeInstructor.getId()).extracting(TutorLeaderboardDTO::points).containsExactly(-2.0);
    }

    private User user(long id) {
        var user = new User();
        user.setId(id);
        user.setFirstName("Staff");
        user.setLastName(Long.toString(id));
        return user;
    }

    private TutorLeaderboardAssessmentsDTO assessment(User assessor, double points) {
        return new TutorLeaderboardAssessmentsDTO(assessor.getId(), 1L, points, 50.0, 0.0, 0L);
    }
}
