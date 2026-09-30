import { cloneWith } from 'app/foundation/util/deep-clone.util';
import { parseJson } from 'app/foundation/util/json.util';

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
 * The repository types that can be checked out into a build container.
 * Note: Matches RepositoryType.java. The client's own RepositoryType (code-editor.model.ts) additionally knows
 * ASSIGNMENT, which the server does not accept here, so the types are mirrored instead of reused.
 */
export const BUILD_CONTAINER_REPOSITORY_TYPE = {
    TEMPLATE: 'TEMPLATE',
    SOLUTION: 'SOLUTION',
    TESTS: 'TESTS',
    AUXILIARY: 'AUXILIARY',
    USER: 'USER',
} as const;

export type BuildContainerRepositoryType = keyof typeof BUILD_CONTAINER_REPOSITORY_TYPE;

/**
 * A repository that is checked out into a build container. Where a repository is checked out stays configured per
 * exercise; a container only selects which of the exercise's repositories are provisioned into it. A repository is
 * identified by its type alone, so AUXILIARY selects every auxiliary repository of the exercise at once.
 * Note: Matches BuildContainerRepositoryDTO.java
 */
export interface BuildContainerRepository {
    type: BuildContainerRepositoryType;
}

/**
 * The Docker flags a build container sets for its own job. A field the container leaves unset takes the value
 * configured on the exercise; the environment variables are added to the exercise's, and a variable of the same name
 * replaces the exercise's value.
 * Note: Matches BuildContainerDockerFlagsDTO.java
 */
export interface BuildContainerDockerFlags {
    network?: string;
    env?: { [key: string]: string };
    cpuCount?: number;
    memory?: number;
    memorySwap?: number;
}

/**
 * A named, independently executable container of a build plan. It runs its own Docker image, checks out only the
 * repositories it lists, and executes its phases inside that image.
 * Note: Matches BuildContainerDTO.java
 */
export interface BuildContainer {
    name: string;
    dockerImage?: string;
    repositories?: BuildContainerRepository[];
    phases: BuildPhase[];
    /** the timeout of this container's build job in seconds; undefined means the timeout configured on the exercise */
    timeoutSeconds?: number;
    /** the Docker flags this container overrides; undefined means the container runs with the flags of the exercise */
    dockerFlags?: BuildContainerDockerFlags;
}

/**
 * Complete serialized build plan configuration stored on the exercise. A configuration written before multi-container
 * support carries a flat list of phases and one Docker image instead of containers.
 * Note: Matches BuildPlanPhasesDTO.java
 */
export interface BuildPlanPhases {
    phases?: BuildPhase[];
    dockerImage?: string;
    containers?: BuildContainer[];
}

export const BUILD_PHASE_NAME_PATTERN = RegExp('^[A-Za-z_][A-Za-z0-9_]*$');
export const BUILD_PHASE_RESERVED_NAMES = new Set(['main', 'final_force_run_post_action']);

export const BUILD_CONTAINER_NAME_PATTERN = RegExp('^[A-Za-z_][A-Za-z0-9_]*$');
export const DEFAULT_BUILD_CONTAINER_NAME = 'default';

// the bounds the server applies in ProgrammingExerciseValidationService#validateDockerFlagValues, mirrored here so an
// invalid value is caught inline instead of only by the save request
export const MIN_DOCKER_CPU_COUNT = 1;
export const MIN_DOCKER_MEMORY_MB = 6;
export const MIN_DOCKER_MEMORY_SWAP_MB = 0;

/** an unset resource limit is valid (the exercise's value applies); a set one has to be a whole number of at least the minimum */
export function isDockerResourceLimitValid(value: number | undefined, minimum: number): boolean {
    return value == undefined || (Number.isInteger(value) && value >= minimum);
}

export function areBuildContainerDockerFlagsValid(dockerFlags: BuildContainerDockerFlags | undefined): boolean {
    return (
        isDockerResourceLimitValid(dockerFlags?.cpuCount, MIN_DOCKER_CPU_COUNT) &&
        isDockerResourceLimitValid(dockerFlags?.memory, MIN_DOCKER_MEMORY_MB) &&
        isDockerResourceLimitValid(dockerFlags?.memorySwap, MIN_DOCKER_MEMORY_SWAP_MB)
    );
}

/**
 * Returns the Docker flags of a container with only the fields that are set, or undefined if none is: a container
 * without an override of its own carries no flags, so it runs with the flags of the exercise.
 */
export function normalizedBuildContainerDockerFlags(dockerFlags: BuildContainerDockerFlags | null | undefined): BuildContainerDockerFlags | undefined {
    if (dockerFlags == undefined) {
        return undefined;
    }
    const normalized: BuildContainerDockerFlags = {};
    if (dockerFlags.network) {
        normalized.network = dockerFlags.network;
    }
    if (dockerFlags.env && Object.keys(dockerFlags.env).length > 0) {
        normalized.env = dockerFlags.env;
    }
    if (dockerFlags.cpuCount != undefined) {
        normalized.cpuCount = dockerFlags.cpuCount;
    }
    if (dockerFlags.memory != undefined) {
        normalized.memory = dockerFlags.memory;
    }
    if (dockerFlags.memorySwap != undefined) {
        normalized.memorySwap = dockerFlags.memorySwap;
    }
    return Object.keys(normalized).length > 0 ? normalized : undefined;
}

/**
 * Returns the containers of a build plan. A legacy configuration that only carries phases and a Docker image is
 * normalized into a single container, so that callers do not have to distinguish between the two formats. The
 * normalized container scopes no repositories, i.e. it checks out the repositories configured on the exercise.
 */
export function effectiveContainers(buildPlan: BuildPlanPhases | undefined): BuildContainer[] {
    if (buildPlan?.containers?.length) {
        return buildPlan.containers;
    }
    if (!buildPlan?.phases?.length) {
        return [];
    }
    return [{ name: DEFAULT_BUILD_CONTAINER_NAME, dockerImage: buildPlan.dockerImage, phases: buildPlan.phases }];
}

/**
 * Returns the phases of every container of a build plan. Callers that ask a question about the build plan as a whole,
 * such as whether it contains a phase that runs after the due date, do not need to know which container a phase runs in.
 */
export function allPhases(buildPlan: BuildPlanPhases | undefined): BuildPhase[] {
    return effectiveContainers(buildPlan).flatMap((container) => container.phases ?? []);
}

export function hasExpectedTestsBeforeDueDate(phase: BuildPhase | undefined): boolean {
    return !!phase && (phase.resultPaths?.length ?? 0) > 0 && phase.condition !== 'AFTER_DUE_DATE';
}

export function parseBuildPlanPhases(json: string | undefined): BuildPlanPhases | undefined {
    if (json == undefined) {
        return undefined;
    }
    let data;
    try {
        data = parseJson(json);
    } catch {
        return undefined;
    }
    if (!isBuildPlanPhases(data)) {
        return undefined;
    }
    return cloneWith(data, {
        phases: data.phases?.map(withPhaseDefaults),
        containers: data.containers?.map((container: BuildContainer) =>
            cloneWith(container, {
                // the server writes an unscoped container with an explicit null, which must not read as "scoped to nothing"
                repositories: container.repositories ?? undefined,
                phases: (container.phases ?? []).map(withPhaseDefaults),
                // likewise, null or empty flags mean the container overrides nothing
                dockerFlags: normalizedBuildContainerDockerFlags(container.dockerFlags),
            }),
        ),
    });
}

function withPhaseDefaults(parsed: BuildPhase): BuildPhase {
    return cloneWith(parsed, {
        script: parsed.script ?? '',
        condition: parsed.condition ?? 'ALWAYS',
        forceRun: parsed.forceRun ?? false,
        resultPaths: parsed.resultPaths ?? [],
    });
}

function isBuildPlanPhases(value: unknown): value is BuildPlanPhases {
    if (typeof value !== 'object' || value === null) {
        return false;
    }
    const v = value as { phases?: unknown; dockerImage?: unknown; containers?: unknown };
    // a configuration that carries neither phases nor containers is not a build plan the editor can work with
    if (v.phases === undefined && v.containers === undefined) {
        return false;
    }
    const phasesValid = v.phases === undefined || (Array.isArray(v.phases) && v.phases.every(isBuildPhase));
    const containersValid = v.containers === undefined || (Array.isArray(v.containers) && v.containers.every(isBuildContainer));
    return phasesValid && containersValid && (v.dockerImage == null || typeof v.dockerImage === 'string');
}

function isBuildContainer(value: unknown): value is BuildContainer {
    if (typeof value !== 'object' || value === null) {
        return false;
    }
    const v = value as { name?: unknown; dockerImage?: unknown; repositories?: unknown; phases?: unknown };
    return (
        typeof v.name === 'string' &&
        (v.dockerImage == null || typeof v.dockerImage === 'string') &&
        (v.repositories == undefined || (Array.isArray(v.repositories) && v.repositories.every(isBuildContainerRepository))) &&
        Array.isArray(v.phases) &&
        v.phases.every(isBuildPhase)
    );
}

function isBuildContainerRepository(value: unknown): value is BuildContainerRepository {
    if (typeof value !== 'object' || value === null) {
        return false;
    }
    const v = value as { type?: unknown; name?: unknown };
    return typeof v.type === 'string' && v.type in BUILD_CONTAINER_REPOSITORY_TYPE && (v.name == null || typeof v.name === 'string');
}

function isBuildPhase(value: unknown): value is BuildPhase {
    if (typeof value !== 'object' || value === null) {
        return false;
    }
    const v = value as { name?: unknown; script?: unknown; condition?: unknown; forceRun?: unknown; resultPaths?: unknown };
    return (
        typeof v.name === 'string' &&
        // a blank script is dropped on write by @JsonInclude(NON_EMPTY) on BuildPhaseDTO, so a stored plan can have a phase
        // with no script key at all. Treat it like the other optional fields and default it below, otherwise a single such
        // phase makes isBuildPlanPhases reject the whole plan and the exercise opens as an empty editor.
        (v.script === undefined || typeof v.script === 'string') &&
        (v.condition === undefined || isBuildPhaseCondition(v.condition)) &&
        (v.forceRun === undefined || typeof v.forceRun === 'boolean') &&
        (v.resultPaths === undefined || isResultPaths(v.resultPaths))
    );
}

function isBuildPhaseCondition(value: unknown): value is BuildPhaseCondition {
    return typeof value === 'string' && value in BUILD_PHASE_CONDITION;
}

function isResultPaths(resultPaths: unknown): resultPaths is string[] {
    return Array.isArray(resultPaths) && resultPaths.every((p: unknown) => typeof p === 'string');
}
