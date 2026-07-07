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

    // Threshold is 7:
    // 1. ExerciseGenerationEventDTO contains inner enum Type
    // 2. ExerciseGenerationEventDTO contains inner enum CompletionStatus
    // 3. ChecklistActionRequestDTO contains inner enum ActionType
    // 4. QuizQuestionGenerationLanguage enum in dto package
    // 5. QuizQuestionGenerationType enum in dto package
    // 6. GenerationMode enum in dto package
    // 7. ExerciseGenerationDtoTest test class in the dto test package (same pattern as other modules' co-located DTO tests)
    @Override
    protected int dtoNameEndingThreshold() {
        return 7;
    }
}
