import { WindFile } from 'app/programming/shared/entities/wind.file';
import { DockerContainerConfig } from './docker-container.config';

export class ProgrammingExerciseBuildConfig {
    public sequentialTestRuns?: boolean;
    public checkoutSolutionRepository: boolean;
    public assignmentCheckoutPath?: string;
    public testCheckoutPath?: string;
    public solutionCheckoutPath?: string;
    public timeoutSeconds?: number;
    public windfile?: WindFile;
    public theiaImage?: string;
    public allowBranching: boolean;
    public branchRegex: string;
    public containerConfigs: { [key: string]: DockerContainerConfig };

    constructor() {
        this.checkoutSolutionRepository = false; // default value
        this.allowBranching = false; // default value
        this.branchRegex = '.*'; // default value
    }
}

export const getDefaultContainerConfig = (buildConfig: ProgrammingExerciseBuildConfig | undefined): DockerContainerConfig => {
    return buildConfig!.containerConfigs['Container 1']!;
};
