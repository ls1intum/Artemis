export class LearningPathHealthDTO {
    public status: HealthStatus[] = [];
    public missingLearningPaths?: number;

    constructor(status: HealthStatus[]) {
        this.status = status;
    }
}

export enum HealthStatus {
    OK = 'OK',
    DISABLED = 'DISABLED',
    MISSING = 'MISSING',
    NO_COMPETENCIES = 'NO_COMPETENCIES',
    NO_RELATIONS = 'NO_RELATIONS',
}

function getWarningTranslation(status: HealthStatus, element: string) {
    if (!status || status === HealthStatus.OK || status === HealthStatus.DISABLED) {
        return '';
    }

    const translation = {
        [HealthStatus.MISSING]: 'missing',
        [HealthStatus.NO_COMPETENCIES]: 'noCompetencies',
        [HealthStatus.NO_RELATIONS]: 'noRelations',
    };
    return `artemisApp.learningPath.manageLearningPaths.health.${translation[status]}.${element}`;
}

export function getWarningTitle(status: HealthStatus) {
    if (!status || status === HealthStatus.OK || status === HealthStatus.DISABLED) {
        return '';
    }

    return getWarningTranslation(status, 'title');
}

export function getWarningBody(status: HealthStatus) {
    if (!status || status === HealthStatus.OK || status === HealthStatus.DISABLED) {
        return '';
    }

    return getWarningTranslation(status, 'body');
}

export function getWarningAction(status: HealthStatus) {
    if (!status || status === HealthStatus.OK || status === HealthStatus.DISABLED) {
        return '';
    }

    return getWarningTranslation(status, 'action');
}

export function getWarningHint(status: HealthStatus) {
    if (!status || status === HealthStatus.OK || status === HealthStatus.DISABLED) {
        return '';
    }

    return getWarningTranslation(status, 'hint');
}
