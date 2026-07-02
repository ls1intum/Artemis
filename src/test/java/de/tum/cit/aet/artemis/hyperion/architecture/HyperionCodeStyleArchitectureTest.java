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

    // Threshold is 5 (inner enums of event DTOs and standalone enums in the dto package):
    // 1. ChecklistActionRequestDTO contains inner enum ActionType
    // 2. QuizQuestionGenerationLanguage enum in dto package
    // 3. QuizQuestionGenerationType enum in dto package
    // 4. ExerciseGenerationEventDTO contains inner enum Type
    // 5. ExerciseGenerationEventDTO contains inner enum CompletionStatus
    @Override
    protected int dtoNameEndingThreshold() {
        return 5;
    }
}
