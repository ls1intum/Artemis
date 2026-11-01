package de.tum.cit.aet.artemis.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.admin.dto.StudentSubmissionCountDTO;
import de.tum.cit.aet.artemis.admin.dto.UsageSemesterDTO;
import de.tum.cit.aet.artemis.admin.repository.PublicUsageStatisticsRepository;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseType;
import de.tum.cit.aet.artemis.exercise.dto.ExerciseTypeMetricsEntry;

class PublicUsageStatisticsServiceTest {

    private static final Set<Long> THREE_COURSES = Set.of(10L, 11L, 12L);

    private static final Set<Long> EXERCISES = Set.of(100L, 101L);

    private PublicUsageStatisticsRepository repository;

    private PublicUsageStatisticsService service;

    @BeforeEach
    void setUp() {
        repository = mock(PublicUsageStatisticsRepository.class);
        when(repository.findRealCourseIds(any(ZonedDateTime.class), any(ZonedDateTime.class), anyLong())).thenReturn(Set.of());
        service = new PublicUsageStatisticsService(repository);
    }

    private void givenOnlyTheNewestSemesterHasData() {
        when(repository.findRealCourseIds(any(ZonedDateTime.class), any(ZonedDateTime.class), anyLong())).thenReturn(THREE_COURSES).thenReturn(Set.of());
        when(repository.findStudentIds(THREE_COURSES)).thenReturn(Set.of(1L, 3L, 4L, 5L, 6L, 7L, 8L));
        when(repository.findExerciseIdsOfCourses(THREE_COURSES)).thenReturn(EXERCISES);
        when(repository.countLectures(THREE_COURSES)).thenReturn(5L);
        when(repository.countExercisesByType(THREE_COURSES)).thenReturn(Set.of(new ExerciseTypeMetricsEntry(ExerciseType.PROGRAMMING, 3)));
    }

    @Test
    void semestersWithoutEnoughCourses_areNotPublished() {
        var statistics = service.getUsageStatistics();

        assertThat(statistics.semesters()).isEmpty();
        assertThat(statistics.generatedAt()).isNotNull();
    }

    @Test
    void semesterBelowTheCourseThreshold_doesNotRunTheExpensiveQueries() {
        assertThat(THREE_COURSES).hasSize(PublicUsageStatisticsService.MIN_COURSES_PER_SEMESTER);
        when(repository.findRealCourseIds(any(ZonedDateTime.class), any(ZonedDateTime.class), anyLong())).thenReturn(Set.of(1L, 2L));

        service.getUsageStatistics();

        verify(repository, never()).countSubmissionsByStudent(any());
        verify(repository, never()).findStudentIds(any());
    }

    @Test
    void onlySubmissionsOfCountedStudentsAndTeamsArePublished() {
        givenOnlyTheNewestSemesterHasData();
        // 2 is a test user or staff member: not among the students, so neither the person nor the 7 submissions count
        when(repository.countSubmissionsByStudent(EXERCISES)).thenReturn(List.of(new StudentSubmissionCountDTO(1L, 10), new StudentSubmissionCountDTO(2L, 7),
                new StudentSubmissionCountDTO(3L, 20), new StudentSubmissionCountDTO(null, 4)));

        var semester = service.getUsageStatistics().semesters().getFirst();

        assertThat(semester.courses()).isEqualTo(3);
        assertThat(semester.students()).isEqualTo(7);
        assertThat(semester.activeStudents()).isEqualTo(2);
        assertThat(semester.submissions()).isEqualTo(34);
        assertThat(semester.lectures()).isEqualTo(5);
    }

    @Test
    void publishedSemester_hasEveryExerciseType() {
        givenOnlyTheNewestSemesterHasData();
        when(repository.countSubmissionsByStudent(EXERCISES)).thenReturn(List.of());

        UsageSemesterDTO semester = service.getUsageStatistics().semesters().getFirst();

        assertThat(semester.activeStudents()).isZero();
        assertThat(semester.exercisesByType()).containsOnlyKeys("text", "programming", "modeling", "file-upload", "quiz").containsEntry("programming", 3L).containsEntry("quiz",
                0L);
    }

    @Test
    void manyExercises_areQueriedInChunksAndTheCountsAreSummedPerStudent() {
        givenOnlyTheNewestSemesterHasData();
        Set<Long> manyExercises = LongStream.rangeClosed(1, PublicUsageStatisticsService.EXERCISE_IDS_PER_QUERY + 1L).boxed().collect(Collectors.toSet());
        when(repository.findExerciseIdsOfCourses(THREE_COURSES)).thenReturn(manyExercises);
        when(repository.countSubmissionsByStudent(any())).thenReturn(List.of(new StudentSubmissionCountDTO(1L, 3), new StudentSubmissionCountDTO(null, 1)))
                .thenReturn(List.of(new StudentSubmissionCountDTO(1L, 4)));

        var semester = service.getUsageStatistics().semesters().getFirst();

        verify(repository, times(2)).countSubmissionsByStudent(any());
        assertThat(semester.activeStudents()).isEqualTo(1);
        assertThat(semester.submissions()).isEqualTo(8);
    }

    @Test
    void usesTheStudentThresholdForRealCourses() {
        service.getUsageStatistics();

        verify(repository, atLeastOnce()).findRealCourseIds(any(ZonedDateTime.class), any(ZonedDateTime.class), eq((long) PublicUsageStatisticsService.MIN_STUDENTS_PER_COURSE));
    }
}
