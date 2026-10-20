package de.tum.cit.aet.artemis.hyperion.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import de.tum.cit.aet.artemis.hyperion.dto.AssessmentCriteriaGenerationRequestDTO;
import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationAccountingState;
import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationInputDTO;
import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationStatusDTO;
import de.tum.cit.aet.artemis.hyperion.dto.GenerationMode;
import de.tum.cit.aet.artemis.hyperion.dto.QuizQuestionGenerationLanguage;
import de.tum.cit.aet.artemis.hyperion.dto.QuizQuestionGenerationRequestDTO;
import de.tum.cit.aet.artemis.hyperion.dto.QuizQuestionGenerationType;

class GenerationRequestAndStatusBoundaryTest {

    @ParameterizedTest
    @MethodSource("pointValues")
    void pointValuesRejectNonFiniteOperandsAndOverflowButLeaveRequirednessToSeparateConstraints(Double maximum, Double bonus, boolean finite) {
        var request = new AssessmentCriteriaGenerationRequestDTO("Statement", maximum, bonus, null, null, null);
        assertThat(request.arePointValuesFinite()).isEqualTo(finite);
    }

    private static Stream<Arguments> pointValues() {
        return Stream.of(Arguments.of(null, null, true), Arguments.of(null, 1d, true), Arguments.of(1d, null, true), Arguments.of(10d, 2d, true),
                Arguments.of(Double.MAX_VALUE, 0d, true), Arguments.of(Double.MAX_VALUE, Double.MAX_VALUE, false), Arguments.of(-Double.MAX_VALUE, -Double.MAX_VALUE, false),
                Arguments.of(Double.NaN, 1d, false), Arguments.of(Double.POSITIVE_INFINITY, 1d, false), Arguments.of(Double.NEGATIVE_INFINITY, 1d, false),
                Arguments.of(1d, Double.NaN, false), Arguments.of(1d, Double.POSITIVE_INFINITY, false), Arguments.of(1d, Double.NEGATIVE_INFINITY, false),
                Arguments.of(null, Double.NaN, false), Arguments.of(Double.NaN, null, false));
    }

    @ParameterizedTest
    @MethodSource("topicsAndCompetencies")
    void quizContextAcceptsEitherMeaningfulTopicOrCompetencySelection(String topic, List<Long> competencies, boolean present) {
        var request = new QuizQuestionGenerationRequestDTO(topic, competencies, null, QuizQuestionGenerationLanguage.EN, Set.of(QuizQuestionGenerationType.SINGLE_CHOICE), 1, 50);
        assertThat(request.isTopicOrCompetencyIdsPresent()).isEqualTo(present);
    }

    private static Stream<Arguments> topicsAndCompetencies() {
        return Stream.of(Arguments.of(null, null, false), Arguments.of("", null, false), Arguments.of(" \n\t", List.of(), false), Arguments.of(null, List.of(), false),
                Arguments.of("Recursion", null, true), Arguments.of("Recursion", List.of(), true), Arguments.of(null, List.of(7L), true), Arguments.of(" ", List.of(7L), true),
                Arguments.of("Recursion", List.of(7L), true));
    }

    @ParameterizedTest
    @CsvSource({ "false,false", "false,true", "true,false", "true,true" })
    void statusCompatibilityConstructorOnlyMakesAnOwnedRunningJobCancellable(boolean running, boolean owned) {
        var status = new ExerciseGenerationStatusDTO("job", running, GenerationMode.ADAPT, List.of(), List.of(), true, "baseline", GenerationMode.GENERATE, owned);
        assertThat(status.cancellable()).isEqualTo(running && owned);
        assertThat(status.ownedByCaller()).isEqualTo(owned);
        assertThat(status.accountingState()).isEqualTo(running ? ExerciseGenerationAccountingState.PENDING : ExerciseGenerationAccountingState.INCOMPLETE);
        var input = new ExerciseGenerationInputDTO("Private instructor brief", List.of());
        var withInput = status.withInput(input);
        assertThat(withInput.input()).isEqualTo(owned ? input : null);
        assertThat(status.input()).isNull();
        assertThat(withInput.revertJobId()).isEqualTo("baseline");
        assertThat(withInput.revertMode()).isEqualTo(GenerationMode.GENERATE);
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void shorterStatusConstructorsPreserveLegacyOwnershipAndNeverClaimSealedAccounting(boolean running) {
        var statuses = List.of(new ExerciseGenerationStatusDTO("job", running, GenerationMode.GENERATE, List.of(), List.of(), false),
                new ExerciseGenerationStatusDTO("job", running, GenerationMode.GENERATE, List.of(), List.of(), false, "baseline", GenerationMode.ADAPT),
                new ExerciseGenerationStatusDTO("job", running, GenerationMode.GENERATE, List.of(), List.of(), false, "baseline", GenerationMode.ADAPT, true, running),
                new ExerciseGenerationStatusDTO("job", running, GenerationMode.GENERATE, List.of(), List.of(), false, "baseline", GenerationMode.ADAPT, true, running, "SPEC"));
        assertThat(statuses).allSatisfy(status -> {
            assertThat(status.ownedByCaller()).isTrue();
            assertThat(status.cancellable()).isEqualTo(running);
            assertThat(status.accountingState()).isEqualTo(running ? ExerciseGenerationAccountingState.PENDING : ExerciseGenerationAccountingState.INCOMPLETE);
            assertThat(status.usage()).isNull();
            assertThat(status.artifactsRetained()).isFalse();
            assertThat(status.input()).isNull();
        });
        assertThat(statuses.getLast().specDocument()).isEqualTo("SPEC");
    }
}
