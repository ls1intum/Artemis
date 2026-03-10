/**
 * Supported execution conditions for a build phase.
 * Note: Matches BuildPhaseCondition.java
 */
export const BUILD_PHASE_CONDITION = {
    ALWAYS: 'always',
    AFTER_DUE_DATE: 'after due date',
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
