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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.admin.dto.BuildUsageDTO;
import de.tum.cit.aet.artemis.admin.dto.CommunicationUsageDTO;
import de.tum.cit.aet.artemis.admin.dto.StudentSubmissionCountDTO;
import de.tum.cit.aet.artemis.admin.dto.UsageSemesterDTO;
import de.tum.cit.aet.artemis.admin.repository.PublicUsageStatisticsRepository;
import de.tum.cit.aet.artemis.atlas.api.CompetencyUsageStatisticsApi;
import de.tum.cit.aet.artemis.core.service.ProfileService;
import de.tum.cit.aet.artemis.exam.api.ExamUsageStatisticsApi;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseType;
import de.tum.cit.aet.artemis.exercise.dto.ExerciseTypeMetricsEntry;
import de.tum.cit.aet.artemis.iris.api.IrisUsageStatisticsApi;
import de.tum.cit.aet.artemis.tutorialgroup.api.TutorialGroupUsageStatisticsApi;

class PublicUsageStatisticsServiceTest {

    /**
     * The default retention period of build jobs. The newest semester started at most about half a year ago, so its builds still exist.
     */
    private static final int BUILD_JOB_RETENTION_DAYS = 365;

    private static final Set<Long> THREE_COURSES = Set.of(10L, 11L, 12L);

    private static final Set<Long> EXERCISES = Set.of(100L, 101L);

    private PublicUsageStatisticsRepository repository;

    private ProfileService profileService;

    private PublicUsageStatisticsService service;

    @BeforeEach
    void setUp() {
        repository = mock(PublicUsageStatisticsRepository.class);
        when(repository.findRealCourseIds(any(ZonedDateTime.class), any(ZonedDateTime.class), anyLong())).thenReturn(Set.of());
        profileService = mock(ProfileService.class);
        when(profileService.isLocalCIActive()).thenReturn(true);
        service = new PublicUsageStatisticsService(repository, profileService, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), BUILD_JOB_RETENTION_DAYS);
    }

    @Test
    void disabledModules_areOmittedInsteadOfShownAsZero() {
        givenOnlyTheNewestSemesterHasData();
        when(repository.countSubmissionsByStudent(any())).thenReturn(List.of());

        var semester = service.getUsageStatistics().semesters().getFirst();

        assertThat(semester.irisMessages()).isNull();
        assertThat(semester.tutorialGroups()).isNull();
        assertThat(semester.exams()).isNull();
        assertThat(semester.examRegistrations()).isNull();
        assertThat(semester.examParticipations()).isNull();
        assertThat(semester.competencies()).isNull();
    }

    @Test
    void staffAndLectureUnits_comeFromTheRepositoryForTheCoursesOfTheSemester() {
        givenOnlyTheNewestSemesterHasData();
        when(repository.countSubmissionsByStudent(any())).thenReturn(List.of());
        when(repository.countInstructors(THREE_COURSES)).thenReturn(4L);
        when(repository.countTutors(THREE_COURSES)).thenReturn(11L);
        when(repository.countLectureUnits(THREE_COURSES)).thenReturn(230L);

        var semester = service.getUsageStatistics().semesters().getFirst();

        assertThat(semester.instructors()).isEqualTo(4);
        assertThat(semester.tutors()).isEqualTo(11);
        assertThat(semester.lectureUnits()).isEqualTo(230);
    }

    @Test
    void enrollments_areNotUniqueWhileStudentsAre() {
        givenOnlyTheNewestSemesterHasData();
        when(repository.countSubmissionsByStudent(any())).thenReturn(List.of());
        when(repository.countStudentEnrollments(THREE_COURSES)).thenReturn(19L);

        var semester = service.getUsageStatistics().semesters().getFirst();

        assertThat(semester.students()).isEqualTo(7);
        assertThat(semester.enrollments()).isEqualTo(19);
    }

    @Test
    void enabledModules_areCountedForTheCoursesOfTheSemester() {
        givenOnlyTheNewestSemesterHasData();
        when(repository.countSubmissionsByStudent(any())).thenReturn(List.of());
        var irisApi = mock(IrisUsageStatisticsApi.class);
        var tutorialGroupApi = mock(TutorialGroupUsageStatisticsApi.class);
        var examApi = mock(ExamUsageStatisticsApi.class);
        var competencyApi = mock(CompetencyUsageStatisticsApi.class);
        when(irisApi.countMessagesInCourses(THREE_COURSES)).thenReturn(120L);
        when(tutorialGroupApi.countTutorialGroups(THREE_COURSES)).thenReturn(9L);
        when(examApi.countRealExams(THREE_COURSES)).thenReturn(7L);
        when(examApi.countRealExamRegistrations(THREE_COURSES)).thenReturn(640L);
        when(examApi.countRealExamParticipations(THREE_COURSES)).thenReturn(512L);
        when(competencyApi.countCompetencies(THREE_COURSES)).thenReturn(21L);
        service = new PublicUsageStatisticsService(repository, profileService, Optional.of(irisApi), Optional.of(tutorialGroupApi), Optional.of(examApi),
                Optional.of(competencyApi), BUILD_JOB_RETENTION_DAYS);

        var semester = service.getUsageStatistics().semesters().getFirst();

        assertThat(semester.irisMessages()).isEqualTo(120);
        assertThat(semester.tutorialGroups()).isEqualTo(9);
        assertThat(semester.exams()).isEqualTo(7);
        assertThat(semester.examRegistrations()).isEqualTo(640);
        assertThat(semester.examParticipations()).isEqualTo(512);
        assertThat(semester.competencies()).isEqualTo(21);
    }

    @Test
    void eachModuleIsOmittedOnItsOwn_whileTheOtherOneIsStillPublished() {
        givenOnlyTheNewestSemesterHasData();
        when(repository.countSubmissionsByStudent(any())).thenReturn(List.of());
        var examApi = mock(ExamUsageStatisticsApi.class);
        var competencyApi = mock(CompetencyUsageStatisticsApi.class);
        when(examApi.countRealExams(THREE_COURSES)).thenReturn(0L);
        when(competencyApi.countCompetencies(THREE_COURSES)).thenReturn(5L);

        service = new PublicUsageStatisticsService(repository, profileService, Optional.empty(), Optional.empty(), Optional.of(examApi), Optional.empty(),
                BUILD_JOB_RETENTION_DAYS);
        var onlyExams = service.getUsageStatistics().semesters().getFirst();
        // a module that is enabled without data publishes a real zero, not an absent value
        assertThat(onlyExams.exams()).isZero();
        assertThat(onlyExams.examRegistrations()).isZero();
        assertThat(onlyExams.examParticipations()).isZero();
        assertThat(onlyExams.competencies()).isNull();

        givenOnlyTheNewestSemesterHasData();
        service = new PublicUsageStatisticsService(repository, profileService, Optional.empty(), Optional.empty(), Optional.empty(), Optional.of(competencyApi),
                BUILD_JOB_RETENTION_DAYS);
        var onlyCompetencies = service.getUsageStatistics().semesters().getFirst();
        assertThat(onlyCompetencies.exams()).isNull();
        assertThat(onlyCompetencies.examRegistrations()).isNull();
        assertThat(onlyCompetencies.competencies()).isEqualTo(5);
    }

    @Test
    void semesterBelowTheCourseThreshold_doesNotAskTheModulesForNumbers() {
        var examApi = mock(ExamUsageStatisticsApi.class);
        var competencyApi = mock(CompetencyUsageStatisticsApi.class);
        service = new PublicUsageStatisticsService(repository, profileService, Optional.empty(), Optional.empty(), Optional.of(examApi), Optional.of(competencyApi),
                BUILD_JOB_RETENTION_DAYS);

        assertThat(service.getUsageStatistics().semesters()).isEmpty();
        verifyNoInteractions(examApi, competencyApi);
    }

    @Test
    void communicationAndBuildNumbers_comeFromTheCoursesOfTheSemester() {
        givenOnlyTheNewestSemesterHasData();
        when(repository.countSubmissionsByStudent(any())).thenReturn(List.of());
        when(repository.countPosts(THREE_COURSES)).thenReturn(10L);
        when(repository.countAnswerPosts(THREE_COURSES)).thenReturn(20L);
        when(repository.countReactionsOnPosts(THREE_COURSES)).thenReturn(5L);
        when(repository.countReactionsOnAnswerPosts(THREE_COURSES)).thenReturn(7L);
        when(repository.countAcceptedFaqs(THREE_COURSES)).thenReturn(3L);
        when(repository.summarizeFinishedBuildJobs(THREE_COURSES)).thenReturn(new BuildUsageDTO(100, 42.5));

        var semester = service.getUsageStatistics().semesters().getFirst();

        assertThat(semester.communication()).isEqualTo(new CommunicationUsageDTO(10, 20, 12, 3));
        assertThat(semester.builds()).isEqualTo(new BuildUsageDTO(100, 42.5));
    }

    @Test
    void withoutSuccessfulBuilds_theAverageDurationIsAbsent() {
        givenOnlyTheNewestSemesterHasData();
        when(repository.countSubmissionsByStudent(any())).thenReturn(List.of());
        when(repository.summarizeFinishedBuildJobs(THREE_COURSES)).thenReturn(new BuildUsageDTO(4, null));

        assertThat(service.getUsageStatistics().semesters().getFirst().builds().averageDurationSeconds()).isNull();
    }

    @Test
    void semesterThatStartedBeforeBuildJobsAreDeleted_hasNoBuildNumbers() {
        givenOnlyTheNewestSemesterHasData();
        when(repository.countSubmissionsByStudent(any())).thenReturn(List.of());
        when(repository.summarizeFinishedBuildJobs(THREE_COURSES)).thenReturn(new BuildUsageDTO(100, 42.5));
        // a retention period of zero days deletes every build job that is older than now, so no semester has all of its builds
        service = new PublicUsageStatisticsService(repository, profileService, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), 0);

        var semester = service.getUsageStatistics().semesters().getFirst();

        assertThat(semester.builds()).isNull();
        assertThat(semester.communication()).isNotNull();
        verify(repository, never()).summarizeFinishedBuildJobs(any());
    }

    @Test
    void withoutLocalCi_noBuildNumbersArePublishedBecauseOnlyLocalCiRecordsBuildJobs() {
        givenOnlyTheNewestSemesterHasData();
        when(repository.countSubmissionsByStudent(any())).thenReturn(List.of());
        when(repository.summarizeFinishedBuildJobs(THREE_COURSES)).thenReturn(new BuildUsageDTO(100, 42.5));
        when(profileService.isLocalCIActive()).thenReturn(false);

        var semester = service.getUsageStatistics().semesters().getFirst();

        assertThat(semester.builds()).isNull();
        assertThat(semester.communication()).isNotNull();
        verify(repository, never()).summarizeFinishedBuildJobs(any());
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
