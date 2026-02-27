export const BUILD_PHASE_CONDITION = {
    ALWAYS: 'always',
    AFTER_DUE_DATE: 'after due date',
};

export type BuildPhaseCondition = keyof typeof BUILD_PHASE_CONDITION;

export interface BuildPhase {
    name: string;
    script: string;
    condition: BuildPhaseCondition;
    resultPaths: string[];
}

export interface BuildPlanPhases {
    phases: BuildPhase[];
}
