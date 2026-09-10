/**
 * Maximum allowed length (in characters) of the serialized build plan configuration.
 * Mirrors the server-side limit in Constants#MAX_BUILD_PLAN_CONFIGURATION_LENGTH (1 MB).
 */
export const BUILD_PLAN_CONFIGURATION_MAX_LENGTH = 1024 * 1024;

/**
 * Maximum allowed length (in characters) of the serialized docker flags.
 * Mirrors the server-side limit in Constants#MAX_DOCKER_FLAGS_LENGTH (8 KB).
 */
export const DOCKER_FLAGS_MAX_LENGTH = 8 * 1024;

export class ProgrammingExerciseBuildConfig {
    public sequentialTestRuns?: boolean;
    public buildPlanConfiguration?: string;
    public buildScript?: string;
    public checkoutSolutionRepository: boolean;
    public assignmentCheckoutPath?: string;
    public testCheckoutPath?: string;
    public solutionCheckoutPath?: string;
    public timeoutSeconds?: number;
    public dockerFlags?: string;
    public theiaImage?: string;
    public allowBranching: boolean;
    public branchRegex: string;

    constructor() {
        this.checkoutSolutionRepository = false; // default value
        this.allowBranching = false; // default value
        this.branchRegex = '.*'; // default value
    }
}
