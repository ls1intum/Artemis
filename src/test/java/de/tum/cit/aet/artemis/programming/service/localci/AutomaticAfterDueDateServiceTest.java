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
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;

@ExtendWith(MockitoExtension.class)
class AutomaticAfterDueDateServiceTest {

    @Mock
    private ProgrammingExerciseRepository programmingExerciseRepository;

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
                examApi);
    }

    @Test
    void computeBuildAndTestDateForExistingExercise_courseExercise_withDueDateAndAfterDueDatePhase_returnsDerivedDate() throws JsonProcessingException {
        var dueDate = ZonedDateTime.now().plusDays(1);
        var exercise = createCourseExercise(dueDate, BuildPhaseCondition.AFTER_DUE_DATE);

        var result = service.computeBuildAndTestDate(exercise, null);

        assertThat(result).isEqualTo(dueDate.plusMinutes(15));
    }

    @Test
    void computeBuildAndTestDateForExistingExercise_courseExercise_withoutDueDate_returnsNull() throws JsonProcessingException {
        var exercise = createCourseExercise(null, BuildPhaseCondition.AFTER_DUE_DATE);
        exercise.setBuildAndTestStudentSubmissionsAfterDueDate(ZonedDateTime.now().plusHours(2));

        var result = service.computeBuildAndTestDate(exercise, null);

        assertThat(result).isNull();
    }

    @Test
    void computeBuildAndTestDateForExistingExercise_courseExercise_withoutAfterDueDatePhase_returnsNull() throws JsonProcessingException {
        var dueDate = ZonedDateTime.now().plusDays(1);
        var exercise = createCourseExercise(dueDate, BuildPhaseCondition.ALWAYS);
        exercise.setBuildAndTestStudentSubmissionsAfterDueDate(ZonedDateTime.now().plusHours(2));

        var result = service.computeBuildAndTestDate(exercise, null);

        assertThat(result).isNull();
    }

    @Test
    void computeBuildAndTestDateForExistingExercise_courseExercise_dueDateChanged_returnsDerivedDate() throws JsonProcessingException {
        var originalDueDate = ZonedDateTime.now().plusDays(1);
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
        var dueDate = ZonedDateTime.now().plusDays(1);
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
    void computeBuildAndTestDate_usesLatestExamEndWithGrace() throws JsonProcessingException {
        var dueDate = ZonedDateTime.now().plusDays(1);
        var latestExamEndDate = ZonedDateTime.now().plusDays(2);
        var exercise = createExamExercise(dueDate, BuildPhaseCondition.AFTER_DUE_DATE, 180);
        when(examDateApi.getLatestIndividualExamEndDate(exercise.getExerciseGroup().getExam())).thenReturn(latestExamEndDate);
        when(examApi.findByExerciseIdElseThrow(exercise.getId())).thenReturn(exercise.getExam());

        var result = service.computeBuildAndTestDate(exercise, null);

        assertThat(result).isEqualTo(latestExamEndDate.plusSeconds(180).plusMinutes(15));
    }

    @Test
    void updateAndSaveBuildAndTestDateInProgrammingExercisesOfExam_updatesChangedExercisesOnly() throws JsonProcessingException {
        var examId = 42L;
        var exerciseId = 10L;
        var dueDate = ZonedDateTime.now().plusDays(1);
        var latestExamEndDate = ZonedDateTime.now().plusDays(2);
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
        var exerciseId = 11L;
        var dueDate = ZonedDateTime.now().plusDays(1);
        var latestExamEndDate = ZonedDateTime.now().plusDays(2);
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
        var dueDate = ZonedDateTime.now().plusDays(2);
        var exercise = createCourseExercise(ZonedDateTime.now().plusDays(1), BuildPhaseCondition.AFTER_DUE_DATE);

        var previewDate = service.getAutomaticBuildAndTestDate(new AutomaticAfterDueDatePreviewRequestDTO(exerciseId, null, dueDate, null, null, null, null, null), exercise, null);

        assertThat(previewDate).isEqualTo(dueDate.plusMinutes(15));
    }

    @Test
    void getAutomaticBuildAndTestDate_newCourseExercise_withoutExplicitPhaseFlag_usesDefaultTemplate() throws IOException {
        var dueDate = ZonedDateTime.now().plusDays(1);
        var defaultPhases = List.of(new BuildPhaseDTO("test", "echo test", BuildPhaseCondition.AFTER_DUE_DATE, false, List.of("build/test-results/*.xml")));
        when(buildPhasesTemplateService.getBuildPlanPhasesFor(ProgrammingLanguage.JAVA, Optional.of(ProjectType.PLAIN_MAVEN), true, false)).thenReturn(defaultPhases);

        var previewDate = service.getAutomaticBuildAndTestDate(
                new AutomaticAfterDueDatePreviewRequestDTO(null, null, dueDate, null, ProgrammingLanguage.JAVA, ProjectType.PLAIN_MAVEN, true, false), null, null);

        assertThat(previewDate).isEqualTo(dueDate.plusMinutes(15));
    }

    @Test
    void getAutomaticBuildAndTestDate_newExamExercise_usesExamEndWithGraceAndOffset() throws IOException {
        var dueDate = ZonedDateTime.now().plusDays(1);
        var latestExamEndDate = ZonedDateTime.now().plusDays(3);
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
