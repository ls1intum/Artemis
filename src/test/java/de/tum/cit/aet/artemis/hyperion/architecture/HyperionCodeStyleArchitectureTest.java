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

    // Threshold is 6:
    // 1. HyperionCodeGenerationEventDTO contains inner enum Type
    // 2. HyperionCodeGenerationEventDTO contains inner enum CompletionStatus
    // 3. HyperionCodeGenerationEventDTO contains inner enum CompletionReason
    // 4. ChecklistActionRequestDTO contains inner enum ActionType
    // 5. QuizQuestionGenerationLanguage enum in dto package
    // 6. QuizQuestionGenerationType enum in dto package
    @Override
    protected int dtoNameEndingThreshold() {
        return 6;
    }
}
