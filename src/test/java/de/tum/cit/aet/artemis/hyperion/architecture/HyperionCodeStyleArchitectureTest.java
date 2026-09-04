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

    // Threshold 10: the inner enums of ExerciseGenerationEventDTO (Type, Phase, CompletionStatus, TerminationReason) and ChecklistActionRequestDTO (ActionType), plus the
    // dto-package enums QuizQuestionGenerationLanguage, QuizQuestionGenerationType, GenerationMode, ExerciseGenerationAccountingState and ExerciseGenerationArtifactCompleteness.
    @Override
    protected int dtoNameEndingThreshold() {
        return 10;
    }
}
