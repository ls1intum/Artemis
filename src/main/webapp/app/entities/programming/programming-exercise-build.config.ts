import { WindFile } from 'app/entities/programming/wind.file';

export class DockerRunConfig {
    public static readonly NETWORK_KEY = 'network';
    public flags?: [string, string][];

    constructor() {
        this.flags = [];
    }
}

export class ProgrammingExerciseBuildConfig {
    public sequentialTestRuns?: boolean;
    public buildPlanConfiguration?: string;
    public buildScript?: string;
    public checkoutSolutionRepository?: boolean;
    public checkoutPath?: string;
    public timeoutSeconds?: number;
    public dockerFlags?: string;
    public windfile?: WindFile;
    public testwiseCoverageEnabled?: boolean;
    public theiaImage?: string;

    constructor() {
        this.checkoutSolutionRepository = false; // default value
        this.testwiseCoverageEnabled = false; // default value
    }
}
