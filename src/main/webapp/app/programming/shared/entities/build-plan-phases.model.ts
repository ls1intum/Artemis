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
export const BUILD_PHASE_RESERVED_NAMES = new Set(['main', 'final_force_run_post_action']);

export function parseBuildPlanPhases(json: string | undefined): BuildPlanPhases | undefined {
    if (json == undefined) {
        return undefined;
    }
    let data;
    try {
        data = JSON.parse(json);
    } catch {
        return undefined;
    }
    if (!isBuildPlanPhases(data)) {
        return undefined;
    }
    return data as BuildPlanPhases;
}

function isBuildPlanPhases(value: unknown): value is BuildPlanPhases {
    if (typeof value !== 'object' || value === null) {
        return false;
    }
    const v = value as any;
    return Array.isArray(v.phases) && v.phases.every(isBuildPhase) && (v.dockerImage === undefined || typeof v.dockerImage === 'string');
}

function isBuildPhase(value: unknown): value is BuildPhase {
    if (typeof value !== 'object' || value === null) {
        return false;
    }
    const v = value as any;
    return (
        typeof v.name === 'string' &&
        typeof v.script === 'string' &&
        isBuildPhaseCondition(v.condition) &&
        typeof v.forceRun === 'boolean' &&
        Array.isArray(v.resultPaths) &&
        v.resultPaths.every((p: unknown) => typeof p === 'string')
    );
}

function isBuildPhaseCondition(value: unknown): value is BuildPhaseCondition {
    return typeof value === 'string' && value in BUILD_PHASE_CONDITION;
}
