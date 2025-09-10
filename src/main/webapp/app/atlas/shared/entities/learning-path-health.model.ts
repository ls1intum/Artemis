export class LearningPathHealthDTO {
    public status: HealthStatus[] = [];
    public missingLearningPaths?: number;

    constructor(status: HealthStatus[]) {
        this.status = status;
    }
}

export enum HealthStatus {
    MISSING = 'MISSING',
    NO_COMPETENCIES = 'NO_COMPETENCIES',
    NO_RELATIONS = 'NO_RELATIONS',
}

function getWarningTranslation(status: HealthStatus, element: string) {
    const translation = {
        [HealthStatus.MISSING]: 'missing',
        [HealthStatus.NO_COMPETENCIES]: 'noCompetencies',
        [HealthStatus.NO_RELATIONS]: 'noRelations',
    };
    return `artemisApp.learningPath.manageLearningPaths.health.${translation[status]}.${element}`;
}

export function getWarningTitle(status: HealthStatus) {
    return getWarningTranslation(status, 'title');
}

export function getWarningBody(status: HealthStatus) {
    return getWarningTranslation(status, 'body');
}

export function getWarningAction(status: HealthStatus) {
    return getWarningTranslation(status, 'action');
}

export function getWarningHint(status: HealthStatus) {
    return getWarningTranslation(status, 'hint');
}
