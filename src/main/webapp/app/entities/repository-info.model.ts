export class RepositoryInfo {
    public repositoryName?: string;
    public repositoryType?: string;
    public triggeredByPushTo?: string;
    public assignmentRepositoryUri?: string;
    public testRepositoryUri?: string;
    public solutionRepositoryUri?: string;
    public auxiliaryRepositoryUris?: string[];
    public auxiliaryRepositoryCheckoutDirectories?: string[];
}
