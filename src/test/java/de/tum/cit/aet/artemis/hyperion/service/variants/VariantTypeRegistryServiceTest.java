package de.tum.cit.aet.artemis.hyperion.service.variants;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseType;
import de.tum.cit.aet.artemis.hyperion.dto.VariantGenerationRequestDTO;
import de.tum.cit.aet.artemis.hyperion.dto.VariantNarrativeStyle;
import de.tum.cit.aet.artemis.hyperion.dto.VariantPlacementDTO;

/**
 * Unit tests for the small pure pieces of the variants module.
 * No Spring context — the registry is plain construction + {@code init()}.
 */
class VariantTypeRegistryServiceTest {

    private VariantTypeAdapters bundleFor(ExerciseType type) {
        return bundleFor(type, true);
    }

    private VariantTypeAdapters bundleFor(ExerciseType type, boolean supportsExercise) {
        VariantTypeAdapters bundle = mock(VariantTypeAdapters.class);
        when(bundle.supportedExerciseType()).thenReturn(type);
        lenient().when(bundle.supportsExercise(any())).thenReturn(supportsExercise);
        return bundle;
    }

    private Exercise exerciseOfType(ExerciseType type) {
        Exercise exercise = mock(Exercise.class);
        lenient().when(exercise.getExerciseType()).thenReturn(type);
        return exercise;
    }

    @Test
    void shouldResolveTheMatchingBundle() {
        VariantTypeAdapters programming = bundleFor(ExerciseType.PROGRAMMING);
        VariantTypeAdapters quiz = bundleFor(ExerciseType.QUIZ);
        VariantTypeRegistryService registry = new VariantTypeRegistryService(List.of(programming, quiz));
        registry.init();

        assertThat(registry.isSupported(ExerciseType.PROGRAMMING)).isTrue();
        assertThat(registry.isSupported(ExerciseType.QUIZ)).isTrue();
        assertThat(registry.isSupported(ExerciseType.TEXT)).isFalse();
        assertThat(registry.resolve(ExerciseType.PROGRAMMING)).isSameAs(programming);
        assertThat(registry.resolve(ExerciseType.QUIZ)).isSameAs(quiz);
    }

    @Test
    void shouldLetTheBundleRejectAnIndividualExerciseOfASupportedType() {
        // A quiz with drag-and-drop questions: the type has a bundle, but this exercise is out of scope.
        VariantTypeRegistryService registry = new VariantTypeRegistryService(List.of(bundleFor(ExerciseType.PROGRAMMING), bundleFor(ExerciseType.QUIZ, false)));
        registry.init();

        assertThat(registry.isSupported(ExerciseType.QUIZ)).isTrue();
        assertThat(registry.isSupported(exerciseOfType(ExerciseType.QUIZ))).isFalse();
        assertThat(registry.isSupported(exerciseOfType(ExerciseType.PROGRAMMING))).isTrue();
    }

    @Test
    void shouldNotSupportExercisesOfATypeWithoutABundle() {
        VariantTypeRegistryService registry = new VariantTypeRegistryService(List.of(bundleFor(ExerciseType.PROGRAMMING)));
        registry.init();

        assertThat(registry.isSupported(exerciseOfType(ExerciseType.TEXT))).isFalse();
    }

    @Test
    void shouldRejectUnsupportedTypes() {
        VariantTypeRegistryService registry = new VariantTypeRegistryService(List.of(bundleFor(ExerciseType.PROGRAMMING)));
        registry.init();

        assertThatThrownBy(() -> registry.resolve(ExerciseType.TEXT)).isInstanceOf(BadRequestAlertException.class)
                .extracting(exception -> ((BadRequestAlertException) exception).getErrorKey()).isEqualTo("unsupportedType");
    }

    @Test
    void shouldFailFastOnDuplicateBundlesForOneType() {
        VariantTypeRegistryService registry = new VariantTypeRegistryService(List.of(bundleFor(ExerciseType.QUIZ), bundleFor(ExerciseType.QUIZ)));

        assertThatThrownBy(registry::init).isInstanceOf(IllegalStateException.class).hasMessageContaining("Duplicate");
    }

    @Test
    void narrativeStyleAloneShouldCountAsAnIntent() {
        var placement = new VariantPlacementDTO(VariantPlacementDTO.PlacementType.STANDALONE, null, null);

        assertThat(new VariantGenerationRequestDTO(null, null, VariantNarrativeStyle.CREATIVE, null, placement).hasAnyIntent()).isTrue();
        assertThat(new VariantGenerationRequestDTO(null, null, null, null, placement).hasAnyIntent()).isFalse();
    }

    @Test
    void changePlanShouldRoundTripThroughJson() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        ChangePlan plan = new ChangePlan("Cargo Bay Manager", "## Tasks\n1. [task][Implement load](testLoad)", List.of("rename BankAccount to CargoBay"),
                List.of("test count stays identical"));

        String json = objectMapper.writeValueAsString(plan);
        ChangePlan roundTripped = objectMapper.readValue(json, ChangePlan.class);

        assertThat(roundTripped).isEqualTo(plan);
    }
}
