export class LearningPathHealthDTO {
    public status?: HealthStatus;
    public missingLearningPaths?: number;

    constructor(status: HealthStatus) {
        this.status = status;
    }
}

export enum HealthStatus {
    OK = 'OK',
    DISABLED = 'DISABLED',
    MISSING = 'MISSING',
}
