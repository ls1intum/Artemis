package de.tum.cit.aet.artemis.assessment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.test_repository.UserTestRepository;
import de.tum.cit.aet.artemis.assessment.dto.tutor.TutorLeaderboardAssessmentsDTO;
import de.tum.cit.aet.artemis.assessment.repository.ComplaintRepository;
import de.tum.cit.aet.artemis.assessment.service.TutorLeaderboardService;
import de.tum.cit.aet.artemis.assessment.test_repository.ResultTestRepository;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exam.domain.ExerciseGroup;
import de.tum.cit.aet.artemis.modeling.domain.ModelingExercise;

@ExtendWith(MockitoExtension.class)
class TutorLeaderboardServiceTest {

    @Mock
    private ResultTestRepository resultRepository;

    @Mock
    private ComplaintRepository complaintRepository;

    @Mock
    private UserTestRepository userRepository;

    @Mock
    private AuthorizationCheckService authCheckService;

    @InjectMocks
    private TutorLeaderboardService service;

    private final Course course = new Course();

    private final Exam exam = new Exam();

    private final User currentTutor = new User();

    @BeforeEach
    void setup() {
        course.setId(1L);
        exam.setCourse(course);
        currentTutor.setId(10L);
        User otherTutor = new User();
        otherTutor.setId(20L);
        when(userRepository.getTutors(course)).thenReturn(Set.of(currentTutor, otherTutor));
    }

    @ParameterizedTest
    @CsvSource({ "false,false,false", "true,false,false", "false,true,false", "true,true,false", "false,false,true", "true,false,true" })
    void protectsExamAverages(boolean exerciseDashboard, boolean instructor, boolean published) {
        if (published) {
            exam.setPublishResultsDate(ZonedDateTime.now().minusDays(1));
        }
        else {
            when(authCheckService.isAtLeastInstructorInCourse(course.getId())).thenReturn(instructor);
            if (!instructor) {
                when(userRepository.getUser()).thenReturn(currentTutor);
            }
        }
        var assessments = List.of(new TutorLeaderboardAssessmentsDTO(10L, 1L, 10.0, 80.0, 0.0, 0L), new TutorLeaderboardAssessmentsDTO(20L, 1L, 10.0, 70.0, 0.0, 0L));
        var exercise = new ModelingExercise();
        exercise.setId(2L);
        var group = new ExerciseGroup();
        group.setExam(exam);
        exercise.setExerciseGroup(group);
        var exerciseIds = Set.of(exercise.getId());
        if (exerciseDashboard) {
            when(resultRepository.findTutorLeaderboardAssessmentByExerciseId(exercise.getId())).thenReturn(assessments);
        }
        else {
            when(resultRepository.findTutorLeaderboardAssessmentByExerciseIds(exerciseIds)).thenReturn(assessments);
        }

        var entries = exerciseDashboard ? service.getExerciseLeaderboard(exercise) : service.getExamLeaderboard(exam, exerciseIds);

        assertThat(entries).filteredOn(entry -> entry.userId() == 10L).singleElement().satisfies(entry -> assertThat(entry.averageScore()).isEqualTo(80.0));
        assertThat(entries).filteredOn(entry -> entry.userId() == 20L).singleElement()
                .satisfies(entry -> assertThat(entry.averageScore()).isEqualTo(instructor || published ? 70.0 : null));
    }
}
