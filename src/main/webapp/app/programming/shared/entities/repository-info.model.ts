export class RepositoryInfo {
    public repositoryName?: string;
    public repositoryType?: string;
    public triggeredByPushTo?: TriggeredByPushTo;
    public assignmentRepositoryUri?: string;
    public testRepositoryUri?: string;
    public solutionRepositoryUri?: string;
    public auxiliaryRepositoryUris?: string[];
    public auxiliaryRepositoryCheckoutDirectories?: string[];
}

export enum TriggeredByPushTo {
    USER = 'USER',
    TESTS = 'TESTS',
    SOLUTION = 'SOLUTION',
    TEMPLATE = 'TEMPLATE',
    AUXILIARY = 'AUXILIARY',
}
