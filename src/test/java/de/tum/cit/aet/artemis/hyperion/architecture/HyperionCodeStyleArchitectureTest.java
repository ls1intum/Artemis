package de.tum.cit.aet.artemis.hyperion.architecture;

import de.tum.cit.aet.artemis.shared.architecture.module.AbstractModuleCodeStyleTest;

class HyperionCodeStyleArchitectureTest extends AbstractModuleCodeStyleTest {

    @Override
    public String getModulePackage() {
        return ARTEMIS_PACKAGE + ".hyperion";
    }

    @Override
    protected int dtoAsAnnotatedRecordThreshold() {
        return 0;
    }

    // Threshold is 8:
    // 1. ExerciseGenerationEventDTO contains inner enum Type
    // 2. ExerciseGenerationEventDTO contains inner enum CompletionStatus
    // 3. ExerciseGenerationEventDTO contains inner enum TerminationReason
    // 4. ChecklistActionRequestDTO contains inner enum ActionType
    // 5. QuizQuestionGenerationLanguage enum in dto package
    // 6. QuizQuestionGenerationType enum in dto package
    // 7. GenerationMode enum in dto package
    // 8. ExerciseGenerationAccountingState enum in dto package
    @Override
    protected int dtoNameEndingThreshold() {
        return 8;
    }
}
