package de.tum.cit.aet.artemis.exercise.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.util.JsonObjectMapper;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseMode;
import de.tum.cit.aet.artemis.exercise.domain.IncludedInOverallScore;
import de.tum.cit.aet.artemis.exercise.dto.ExerciseImportSettingsDTO;
import de.tum.cit.aet.artemis.exercise.dto.ExerciseMetadataDTO;
import de.tum.cit.aet.artemis.fileupload.domain.FileUploadExercise;
import de.tum.cit.aet.artemis.fileupload.dto.UpdateFileUploadExerciseDTO;
import de.tum.cit.aet.artemis.modeling.domain.ModelingExercise;
import de.tum.cit.aet.artemis.modeling.dto.ImportModelingExerciseDTO;
import de.tum.cit.aet.artemis.modeling.dto.UpdateModelingExerciseDTO;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.dto.UpdateProgrammingExerciseDTO;
import de.tum.cit.aet.artemis.text.domain.TextExercise;
import de.tum.cit.aet.artemis.text.dto.ImportTextExerciseDTO;
import de.tum.cit.aet.artemis.text.dto.UpdateTextExerciseDTO;

class ExerciseInputMappingTest {

    @ParameterizedTest
    @MethodSource("updateInputs")
    void updatesClearNullableMetadataAndPreserveOmittedAssessmentFlags(Class<? extends ExerciseMetadataDTO> type, Exercise exercise) {
        var mapper = JsonObjectMapper.get();
        exercise.setAllowComplaintsForAutomaticAssessments(true);
        exercise.setPresentationScoreEnabled(true);
        exercise.setSecondCorrectionEnabled(true);
        exercise.setIncludedInOverallScore(IncludedInOverallScore.INCLUDED_COMPLETELY);
        var dto = mapper.readValue("{\"channelName\":\"exercise\",\"maxPoints\":10,\"bonusPoints\":2}", type);
        dto.applyMetadataTo(exercise);
        dto.applyAssessmentFlagsTo(exercise);
        assertThat(exercise.getChannelName()).isEqualTo("exercise");
        assertThat(exercise.getMaxPoints()).isEqualTo(10);
        assertThat(exercise.getBonusPoints()).isEqualTo(2);
        assertThat(exercise.getIncludedInOverallScore()).isNull();
        assertThat(exercise.getAllowComplaintsForAutomaticAssessments()).isTrue();
        assertThat(exercise.getPresentationScoreEnabled()).isTrue();
        assertThat(exercise.getSecondCorrectionEnabled()).isTrue();
    }

    @ParameterizedTest
    @MethodSource("importInputs")
    void importsRetainOmittedDefaultsAndAlwaysUseManualAssessment(Class<? extends ExerciseImportSettingsDTO> type, Exercise exercise) {
        var dto = JsonObjectMapper.get().readValue("{\"id\":12,\"title\":\"Imported\",\"presentationScoreEnabled\":true}", type);
        dto.applyImportSettingsTo(exercise);
        assertThat(exercise.getId()).isEqualTo(12L);
        assertThat(exercise.getTitle()).isEqualTo("Imported");
        assertThat(exercise.getMode()).isEqualTo(ExerciseMode.INDIVIDUAL);
        assertThat(exercise.getIncludedInOverallScore()).isEqualTo(IncludedInOverallScore.INCLUDED_COMPLETELY);
        assertThat(exercise.getAssessmentType()).isEqualTo(AssessmentType.MANUAL);
        assertThat(exercise.getPresentationScoreEnabled()).isTrue();
    }

    @Test
    void transientCompetencyLinksCarryOwnerIdAndWeightWithoutTrustingAiProvenance() {
        var dto = JsonObjectMapper.get().readValue("{\"competencyLinks\":[{\"competency\":{\"id\":123},\"weight\":0.5,\"generatedByAi\":true}]}", ImportTextExerciseDTO.class);
        var exercise = new TextExercise();
        assertThat(CompetencyExerciseLinkService.toTransientLinks(dto, exercise, "textExercise")).singleElement().satisfies(link -> {
            assertThat(link.getExercise()).isSameAs(exercise);
            assertThat(link.getCompetency().getId()).isEqualTo(123L);
            assertThat(link.getWeight()).isEqualTo(0.5);
            assertThat(link.isGeneratedByAi()).isFalse();
        });
        var invalid = JsonObjectMapper.get().readValue("{\"competencyLinks\":[{\"weight\":0.5}]}", ImportTextExerciseDTO.class);
        assertThatThrownBy(() -> CompetencyExerciseLinkService.toTransientLinks(invalid, exercise, "textExercise")).isInstanceOf(BadRequestAlertException.class)
                .hasMessage("Each competency link must include a competency.");
    }

    private static Stream<Arguments> updateInputs() {
        return Stream.of(Arguments.of(UpdateTextExerciseDTO.class, new TextExercise()), Arguments.of(UpdateModelingExerciseDTO.class, new ModelingExercise()),
                Arguments.of(UpdateProgrammingExerciseDTO.class, new ProgrammingExercise()), Arguments.of(UpdateFileUploadExerciseDTO.class, new FileUploadExercise()));
    }

    private static Stream<Arguments> importInputs() {
        return Stream.of(Arguments.of(ImportTextExerciseDTO.class, new TextExercise()), Arguments.of(ImportModelingExerciseDTO.class, new ModelingExercise()));
    }
}
