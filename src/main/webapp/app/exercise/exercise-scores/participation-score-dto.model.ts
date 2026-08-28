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
    testCaseCount?: number;
    passedTestCaseCount?: number;
    codeIssueCount?: number;
    correctionRoundResults?: CorrectionRoundResultDTO[];
}

/**
 * One manual result of the participation's latest submission, with the correction round it belongs to. The scores view
 * renders assessment actions per round and needs an entry per round, not only the newest result.
 */
export interface CorrectionRoundResultDTO {
    resultId: number;
    correctionRound?: number;
    assessmentType?: AssessmentType;
    completionDate?: dayjs.Dayjs;
    hasComplaint?: boolean;
}
