import dayjs from 'dayjs/esm';

export interface ParticipationManagementDTO {
    participationId: number;
    initializationState?: string;
    initializationDate?: dayjs.Dayjs;
    submissionCount: number;
    participantName?: string;
    participantIdentifier?: string;
    studentId?: number;
    studentLogin?: string;
    teamId?: number;
    testRun: boolean;
    presentationScore?: number;
    individualDueDate?: dayjs.Dayjs;
    buildPlanId?: string;
    repositoryUri?: string;
    buildFailed?: boolean;
    lastResultIsManual?: boolean;
}
