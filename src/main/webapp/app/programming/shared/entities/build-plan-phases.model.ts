/**
 * Supported execution conditions for a build phase.
 * Note: Matches BuildPhaseCondition.java
 */
export const BUILD_PHASE_CONDITION = {
    ALWAYS: 'artemisApp.programmingExercise.buildPhasesEditor.conditions.always',
    AFTER_DUE_DATE: 'artemisApp.programmingExercise.buildPhasesEditor.conditions.afterDueDate',
};

export type BuildPhaseCondition = keyof typeof BUILD_PHASE_CONDITION;

/**
 * Editable build phase configuration shown in the custom build plan editor.
 * Note: Matches BuildPhaseDTO.java
 */
export interface BuildPhase {
    name: string;
    script: string;
    condition: BuildPhaseCondition;
    forceRun: boolean;
    resultPaths: string[];
}

/**
 * Complete serialized build plan configuration stored on the exercise.
 * Note: Matches BuildPlanPhasesDTO.java
 */
export interface BuildPlanPhases {
    phases: BuildPhase[];
    dockerImage?: string;
}

export const BUILD_PHASE_NAME_PATTERN = RegExp('^[A-Za-z_][A-Za-z0-9_]*$');
export const BUILD_PHASE_RESERVED_NAMES = new Set(['main', 'final_aeolus_post_action']);
