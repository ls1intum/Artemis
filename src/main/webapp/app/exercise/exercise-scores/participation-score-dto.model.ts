import dayjs from 'dayjs/esm';
import { AssessmentType } from 'app/assessment/shared/entities/assessment-type.model';

export interface ParticipationScoreDTO {
    participationId: number;
    initializationDate?: dayjs.Dayjs;
    submissionCount: number;
    participantName?: string;
    participantIdentifier?: string;
    studentId?: number;
    teamId?: number;
    resultId?: number;
    score?: number;
    successful?: boolean;
    completionDate?: dayjs.Dayjs;
    assessmentType?: AssessmentType;
    assessmentNote?: string;
    durationInSeconds?: number;
    submissionId?: number;
    buildFailed?: boolean;
    buildPlanId?: string;
    repositoryUri?: string;
    testRun: boolean;
}
