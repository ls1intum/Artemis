package de.tum.cit.aet.artemis.programming.service.localci;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.core.JsonProcessingException;

import de.tum.cit.aet.artemis.exam.api.ExamApi;
import de.tum.cit.aet.artemis.exam.api.ExamDateApi;
import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exam.domain.ExerciseGroup;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseBuildConfig;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.domain.ProjectType;
import de.tum.cit.aet.artemis.programming.domain.build.BuildPhaseCondition;
import de.tum.cit.aet.artemis.programming.dto.AutomaticAfterDueDatePreviewRequestDTO;
import de.tum.cit.aet.artemis.programming.dto.BuildPhaseDTO;
import de.tum.cit.aet.artemis.programming.dto.BuildPlanPhasesDTO;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseBuildConfigRepository;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingExerciseTestRepository;

@ExtendWith(MockitoExtension.class)
class AutomaticAfterDueDateServiceTest {

    private static final ZonedDateTime BASE_TIME = ZonedDateTime.parse("2050-01-01T12:00:00Z");

    @Mock
    private ProgrammingExerciseTestRepository programmingExerciseRepository;

    @Mock
    private ExamDateApi examDateApi;

    @Mock
    private BuildPhasesTemplateService buildPhasesTemplateService;

    @Mock
    private ProgrammingExerciseBuildConfigRepository programmingExerciseBuildConfigRepository;

    @Mock
    private ExamApi examApi;

    private AutomaticAfterDueDateService service;

    @BeforeEach
    void setUp() {
        service = new AutomaticAfterDueDateService(programmingExerciseRepository, Optional.of(examDateApi), buildPhasesTemplateService, programmingExerciseBuildConfigRepository,
                Optional.of(examApi));
    }

    @Test
    void computeBuildAndTestDateForExistingExercise_courseExercise_withDueDateAndAfterDueDatePhase_returnsDerivedDate() throws JsonProcessingException {
        var dueDate = BASE_TIME.plusDays(1);
        var exercise = createCourseExercise(dueDate, BuildPhaseCondition.AFTER_DUE_DATE);

        var result = service.computeBuildAndTestDate(exercise, null);

        assertThat(result).isEqualTo(dueDate.plusMinutes(15));
    }

    @Test
    void computeBuildAndTestDateForExistingExercise_courseExercise_withoutDueDate_returnsNull() throws JsonProcessingException {
        var exercise = createCourseExercise(null, BuildPhaseCondition.AFTER_DUE_DATE);
        exercise.setBuildAndTestStudentSubmissionsAfterDueDate(BASE_TIME.plusHours(2));

        var result = service.computeBuildAndTestDate(exercise, null);

        assertThat(result).isNull();
    }

    @Test
    void computeBuildAndTestDateForExistingExercise_courseExercise_withoutAfterDueDatePhase_returnsNull() throws JsonProcessingException {
        var dueDate = BASE_TIME.plusDays(1);
        var exercise = createCourseExercise(dueDate, BuildPhaseCondition.ALWAYS);
        exercise.setBuildAndTestStudentSubmissionsAfterDueDate(BASE_TIME.plusHours(2));

        var result = service.computeBuildAndTestDate(exercise, null);

        assertThat(result).isNull();
    }

    @Test
    void computeBuildAndTestDateForExistingExercise_courseExercise_dueDateChanged_returnsDerivedDate() throws JsonProcessingException {
        var originalDueDate = BASE_TIME.plusDays(1);
        var updatedDueDate = originalDueDate.plusHours(3);
        var exercise = createCourseExercise(originalDueDate, BuildPhaseCondition.AFTER_DUE_DATE);

        var firstResult = service.computeBuildAndTestDate(exercise, null);
        assertThat(firstResult).isEqualTo(originalDueDate.plusMinutes(15));

        exercise.setBuildAndTestStudentSubmissionsAfterDueDate(firstResult);
        exercise.setDueDate(updatedDueDate);

        var offset = Duration.between(originalDueDate, firstResult);
        var secondResult = service.computeBuildAndTestDate(exercise, offset);
        assertThat(secondResult).isEqualTo(updatedDueDate.plusMinutes(15));
    }

    @Test
    void computeBuildAndTestDateForExistingExercise_courseExercise_phaseAddedAndRemoved_returnsCorrectDates() throws JsonProcessingException {
        var dueDate = BASE_TIME.plusDays(1);
        var exercise = createCourseExercise(dueDate, BuildPhaseCondition.ALWAYS);

        var firstResult = service.computeBuildAndTestDate(exercise, null);
        assertThat(firstResult).isNull();

        exercise.setBuildConfig(createBuildConfig(BuildPhaseCondition.AFTER_DUE_DATE));
        var secondResult = service.computeBuildAndTestDate(exercise, null);
        assertThat(secondResult).isEqualTo(dueDate.plusMinutes(15));

        exercise.setBuildConfig(createBuildConfig(BuildPhaseCondition.ALWAYS));
        var thirdResult = service.computeBuildAndTestDate(exercise, null);
        assertThat(thirdResult).isNull();
    }

    @Test
    void getOriginalBuildAndTestOffset_courseExercise_returnsOffsetFromDueDate() throws JsonProcessingException {
        var dueDate = BASE_TIME.plusDays(1);
        var exercise = createCourseExercise(dueDate, BuildPhaseCondition.AFTER_DUE_DATE);
        exercise.setBuildAndTestStudentSubmissionsAfterDueDate(dueDate.plusMinutes(45));

        var result = service.getOriginalBuildAndTestOffset(exercise);

        assertThat(result).isEqualTo(Duration.ofMinutes(45));
    }

    @Test
    void getOriginalBuildAndTestOffset_courseExerciseWithoutReferenceDate_returnsNull() throws JsonProcessingException {
        var exercise = createCourseExercise(null, BuildPhaseCondition.AFTER_DUE_DATE);
        exercise.setBuildAndTestStudentSubmissionsAfterDueDate(BASE_TIME.plusHours(1));

        var result = service.getOriginalBuildAndTestOffset(exercise);

        assertThat(result).isNull();
    }

    @Test
    void getOriginalBuildAndTestOffset_examExercise_returnsOffsetFromLatestExamEndWithGrace() throws JsonProcessingException {
        var exerciseId = 13L;
        var latestExamEndDate = BASE_TIME.plusDays(2);
        var exercise = createExamExercise(BASE_TIME.plusDays(1), BuildPhaseCondition.AFTER_DUE_DATE, 60);
        exercise.setId(exerciseId);
        exercise.setBuildAndTestStudentSubmissionsAfterDueDate(latestExamEndDate.plusSeconds(60).plusHours(1));
        when(examApi.findByExerciseId(exerciseId)).thenReturn(Optional.of(exercise.getExam()));
        when(examDateApi.getLatestIndividualExamEndDate(exercise.getExam())).thenReturn(latestExamEndDate);

        var result = service.getOriginalBuildAndTestOffset(exercise);

        assertThat(result).isEqualTo(Duration.ofHours(1));
    }

    @Test
    void computeBuildAndTestDate_usesLatestExamEndWithGrace() throws JsonProcessingException {
        var dueDate = BASE_TIME.plusDays(1);
        var latestExamEndDate = BASE_TIME.plusDays(2);
        var exercise = createExamExercise(dueDate, BuildPhaseCondition.AFTER_DUE_DATE, 180);
        when(examDateApi.getLatestIndividualExamEndDate(exercise.getExerciseGroup().getExam())).thenReturn(latestExamEndDate);
        when(examApi.findByExerciseId(exercise.getId())).thenReturn(Optional.of(exercise.getExam()));

        var result = service.computeBuildAndTestDate(exercise, null);

        assertThat(result).isEqualTo(latestExamEndDate.plusSeconds(180).plusMinutes(15));
    }

    @Test
    void updateAndSaveBuildAndTestDateInProgrammingExercisesOfExam_updatesChangedExercisesOnly() throws JsonProcessingException {
        var examId = 42L;
        var exerciseId = 10L;
        var dueDate = BASE_TIME.plusDays(1);
        var latestExamEndDate = BASE_TIME.plusDays(2);
        var exercise = createExamExercise(dueDate, BuildPhaseCondition.AFTER_DUE_DATE, 120);
        exercise.setId(exerciseId);
        exercise.setBuildAndTestStudentSubmissionsAfterDueDate(dueDate.plusMinutes(15));

        var exam = exercise.getExerciseGroup().getExam();
        exam.setId(examId);
        var exerciseGroup = exercise.getExerciseGroup();
        exerciseGroup.setExercises(Set.of(exercise));
        exam.setExerciseGroups(List.of(exerciseGroup));

        when(examDateApi.getLatestIndividualExamEndDate(exam)).thenReturn(latestExamEndDate);
        when(programmingExerciseRepository.saveAll(anyList())).thenReturn(List.of(exercise));

        Set<Long> updatedIds = service.updateAndSaveBuildAndTestDateInProgrammingExercisesOfExam(exam, null);

        assertThat(updatedIds).containsExactly(exerciseId);
        assertThat(exercise.getBuildAndTestStudentSubmissionsAfterDueDate()).isEqualTo(latestExamEndDate.plusSeconds(120).plusMinutes(15));
        verify(programmingExerciseRepository).saveAll(anyList());
    }

    @Test
    void updateAndSaveBuildAndTestDateInProgrammingExercisesOfExam_doesNotSaveWhenDateUnchanged() throws JsonProcessingException {
        var examId = 43L;
        var dueDate = BASE_TIME.plusDays(1);
        var latestExamEndDate = BASE_TIME.plusDays(2);
        var exercise = createExamExercise(dueDate, BuildPhaseCondition.AFTER_DUE_DATE, 90);
        exercise.setBuildAndTestStudentSubmissionsAfterDueDate(latestExamEndDate.plusSeconds(90).plusMinutes(15));

        var exam = exercise.getExerciseGroup().getExam();
        exam.setId(examId);
        var exerciseGroup = exercise.getExerciseGroup();
        exerciseGroup.setExercises(Set.of(exercise));
        exam.setExerciseGroups(List.of(exerciseGroup));

        when(examDateApi.getLatestIndividualExamEndDate(exam)).thenReturn(latestExamEndDate);

        Set<Long> updatedIds = service.updateAndSaveBuildAndTestDateInProgrammingExercisesOfExam(exam, null);

        assertThat(updatedIds).isEmpty();
        verify(programmingExerciseRepository, never()).saveAll(anyList());
    }

    @Test
    void getAutomaticBuildAndTestDate_existingCourseExercise_dueDateAndAfterDueDatePhase_returnsDerivedDate() throws IOException, JsonProcessingException {
        var exerciseId = 10L;
        var dueDate = BASE_TIME.plusDays(2);
        var exercise = createCourseExercise(BASE_TIME.plusDays(1), BuildPhaseCondition.AFTER_DUE_DATE);

        var previewDate = service.getAutomaticBuildAndTestDate(new AutomaticAfterDueDatePreviewRequestDTO(exerciseId, null, dueDate, null, null, null, null, null), exercise, null);

        assertThat(previewDate).isEqualTo(dueDate.plusMinutes(15));
    }

    @Test
    void getAutomaticBuildAndTestDate_existingCourseExerciseImportedIntoExam_preservesOffsetFromCourseExercise() throws IOException, JsonProcessingException {
        var exerciseId = 10L;
        var targetExamEndDate = BASE_TIME.plusDays(5);
        var targetExam = new Exam();
        targetExam.setId(1L);
        targetExam.setGracePeriod(120);
        var exercise = createCourseExercise(BASE_TIME.plusDays(1), BuildPhaseCondition.AFTER_DUE_DATE);
        exercise.setId(exerciseId);
        exercise.setBuildAndTestStudentSubmissionsAfterDueDate(exercise.getDueDate().plusHours(2));
        when(examDateApi.getLatestIndividualExamEndDate(targetExam)).thenReturn(targetExamEndDate);
        when(examApi.findByExerciseId(exerciseId)).thenReturn(Optional.empty());

        var previewDate = service.getAutomaticBuildAndTestDate(new AutomaticAfterDueDatePreviewRequestDTO(exerciseId, targetExam.getId(), null, null, null, null, null, null),
                exercise, targetExam);

        assertThat(previewDate).isEqualTo(targetExamEndDate.plusSeconds(120).plusHours(2));
    }

    @Test
    void getAutomaticBuildAndTestDate_existingExamExerciseImportedIntoDifferentExam_preservesOffsetFromSourceExam() throws IOException, JsonProcessingException {
        var exerciseId = 11L;
        var sourceExamEndDate = BASE_TIME.plusDays(3);
        var targetExamEndDate = BASE_TIME.plusDays(7);
        var exercise = createExamExercise(BASE_TIME.plusDays(1), BuildPhaseCondition.AFTER_DUE_DATE, 60);
        exercise.setId(exerciseId);
        var sourceExam = exercise.getExam();
        sourceExam.setId(1L);
        exercise.setBuildAndTestStudentSubmissionsAfterDueDate(sourceExamEndDate.plusSeconds(60).plusMinutes(45));
        var targetExam = new Exam();
        targetExam.setId(2L);
        targetExam.setGracePeriod(120);
        when(examDateApi.getLatestIndividualExamEndDate(targetExam)).thenReturn(targetExamEndDate);
        when(examApi.findByExerciseId(exerciseId)).thenReturn(Optional.of(sourceExam));
        when(examDateApi.getLatestIndividualExamEndDate(sourceExam)).thenReturn(sourceExamEndDate);

        var previewDate = service.getAutomaticBuildAndTestDate(new AutomaticAfterDueDatePreviewRequestDTO(exerciseId, targetExam.getId(), null, null, null, null, null, null),
                exercise, targetExam);

        assertThat(previewDate).isEqualTo(targetExamEndDate.plusSeconds(120).plusMinutes(45));
    }

    @Test
    void getAutomaticBuildAndTestDate_existingExamExerciseInSameExam_preservesOffsetFromTargetExam() throws IOException, JsonProcessingException {
        var exerciseId = 12L;
        var examEndDate = BASE_TIME.plusDays(4);
        var exercise = createExamExercise(BASE_TIME.plusDays(1), BuildPhaseCondition.AFTER_DUE_DATE, 90);
        exercise.setId(exerciseId);
        var exam = exercise.getExam();
        exam.setId(1L);
        exercise.setBuildAndTestStudentSubmissionsAfterDueDate(examEndDate.plusSeconds(90).plusMinutes(30));
        when(examDateApi.getLatestIndividualExamEndDate(exam)).thenReturn(examEndDate);
        when(examApi.findByExerciseId(exerciseId)).thenReturn(Optional.of(exam));

        var previewDate = service.getAutomaticBuildAndTestDate(new AutomaticAfterDueDatePreviewRequestDTO(exerciseId, exam.getId(), null, null, null, null, null, null), exercise,
                exam);

        assertThat(previewDate).isEqualTo(examEndDate.plusSeconds(90).plusMinutes(30));
    }

    @Test
    void getAutomaticBuildAndTestDate_newCourseExercise_withoutExplicitPhaseFlag_usesDefaultTemplate() throws IOException {
        var dueDate = BASE_TIME.plusDays(1);
        var defaultPhases = List.of(new BuildPhaseDTO("test", "echo test", BuildPhaseCondition.AFTER_DUE_DATE, false, List.of("build/test-results/*.xml")));
        when(buildPhasesTemplateService.getBuildPlanPhasesFor(ProgrammingLanguage.JAVA, Optional.of(ProjectType.PLAIN_MAVEN), true, false)).thenReturn(defaultPhases);

        var previewDate = service.getAutomaticBuildAndTestDate(
                new AutomaticAfterDueDatePreviewRequestDTO(null, null, dueDate, null, ProgrammingLanguage.JAVA, ProjectType.PLAIN_MAVEN, true, false), null, null);

        assertThat(previewDate).isEqualTo(dueDate.plusMinutes(15));
    }

    @Test
    void getAutomaticBuildAndTestDate_newExamExercise_usesExamEndWithGraceAndOffset() throws IOException {
        var dueDate = BASE_TIME.plusDays(1);
        var latestExamEndDate = BASE_TIME.plusDays(3);
        var exam = new Exam();
        exam.setId(1L);
        exam.setGracePeriod(120);
        when(examDateApi.getLatestIndividualExamEndDate(exam)).thenReturn(latestExamEndDate);
        var templatePhases = List.of(new BuildPhaseDTO("test", "echo test", BuildPhaseCondition.ALWAYS, false, List.of("build/test-results/*.xml")));
        when(buildPhasesTemplateService.getBuildPlanPhasesFor(ProgrammingLanguage.JAVA, Optional.of(ProjectType.PLAIN_MAVEN), false, false)).thenReturn(templatePhases);
        when(buildPhasesTemplateService.applyExamDefaults(anyList()))
                .thenReturn(List.of(new BuildPhaseDTO("test", "echo test", BuildPhaseCondition.AFTER_DUE_DATE, false, List.of("build/test-results/*.xml"))));

        var previewDate = service.getAutomaticBuildAndTestDate(
                new AutomaticAfterDueDatePreviewRequestDTO(null, exam.getId(), dueDate, null, ProgrammingLanguage.JAVA, ProjectType.PLAIN_MAVEN, false, false), null, exam);

        assertThat(previewDate).isEqualTo(latestExamEndDate.plusSeconds(120).plusMinutes(15));
    }

    private static ProgrammingExercise createCourseExercise(ZonedDateTime dueDate, BuildPhaseCondition phaseCondition) throws JsonProcessingException {
        var exercise = new ProgrammingExercise();
        exercise.setDueDate(dueDate);
        exercise.setBuildConfig(createBuildConfig(phaseCondition));
        return exercise;
    }

    private static ProgrammingExercise createExamExercise(ZonedDateTime dueDate, BuildPhaseCondition phaseCondition, int gracePeriod) throws JsonProcessingException {
        var exercise = new ProgrammingExercise();
        exercise.setDueDate(dueDate);
        exercise.setBuildConfig(createBuildConfig(phaseCondition));

        var exam = new Exam();
        exam.setGracePeriod(gracePeriod);
        var exerciseGroup = new ExerciseGroup();
        exerciseGroup.setExam(exam);
        exercise.setExerciseGroup(exerciseGroup);
        return exercise;
    }

    private static ProgrammingExerciseBuildConfig createBuildConfig(BuildPhaseCondition phaseCondition) throws JsonProcessingException {
        var buildConfig = new ProgrammingExerciseBuildConfig();
        var phase = new BuildPhaseDTO("test", "echo test", phaseCondition, false, List.of("build/test-results/*.xml"));
        buildConfig.setBuildPlanConfiguration(new BuildPlanPhasesDTO(List.of(phase), "ghcr.io/example-image").toBuildPlanConfiguration());
        return buildConfig;
    }
}
