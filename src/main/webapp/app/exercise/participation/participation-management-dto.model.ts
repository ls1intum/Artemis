import dayjs from 'dayjs/esm';
import { AssessmentType } from 'app/assessment/shared/entities/assessment-type.model';

export interface TeamStudentDTO {
    name?: string;
    login?: string;
}

/**
 * One manual result of the participation's latest submission, with the correction round it belongs to. The assessment
 * actions are rendered per round and need an entry per round, not only the newest result.
 */
export interface CorrectionRoundResultDTO {
    resultId: number;
    correctionRound?: number;
    assessmentType?: AssessmentType;
    completionDate?: dayjs.Dayjs;
    hasComplaint?: boolean;
}

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
    teamStudents?: TeamStudentDTO[];
    testRun: boolean;
    presentationScore?: number;
    individualDueDate?: dayjs.Dayjs;
    buildPlanId?: string;
    repositoryUri?: string;
    buildFailed?: boolean;
    lastResultIsManual?: boolean;
    submissionId?: number;
    // The fields below describe the newest result of the latest submission
    resultId?: number;
    score?: number;
    successful?: boolean;
    completionDate?: dayjs.Dayjs;
    assessmentType?: AssessmentType;
    assessmentNote?: string;
    durationInSeconds?: number;
    testCaseCount?: number;
    passedTestCaseCount?: number;
    codeIssueCount?: number;
    correctionRoundResults?: CorrectionRoundResultDTO[];
}
