import { WindFile } from 'app/programming/shared/entities/wind.file';

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
    public windfile?: WindFile;
    public theiaImage?: string;
    public allowBranching: boolean;
    public branchRegex: string;

    constructor() {
        this.checkoutSolutionRepository = false; // default value
        this.allowBranching = false; // default value
        this.branchRegex = '.*'; // default value
    }
}
