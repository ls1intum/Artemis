import dayjs from 'dayjs/esm';
import { AssessmentType } from 'app/assessment/shared/entities/assessment-type.model';
import { CorrectionRoundResultDTO, TeamStudentDTO } from './participation-management-dto.model';
import { SortingOrder } from 'app/foundation/pagination/pageable-table';

export interface AssessmentDraftDTO {
    submissionId: number;
    resultId: number;
    correctionRound?: number;
}

/** Queue response. Empty arrays are omitted by the server’s NON_EMPTY serialization. */
export interface AssessmentQueueRowDTO {
    participationId: number;
    submissionCount: number;
    participantName?: string;
    participantIdentifier?: string;
    teamId?: number;
    teamStudents?: TeamStudentDTO[];
    testRun: boolean;
    individualDueDate?: dayjs.Dayjs;
    buildFailed?: boolean;
    submissionId?: number;
    submissionDate?: dayjs.Dayjs;
    submissionLanguage?: string;
    // The fields below describe the newest result of the latest submission
    resultId?: number;
    score?: number;
    successful?: boolean;
    completionDate?: dayjs.Dayjs;
    assessmentType?: AssessmentType;
    testCaseCount?: number;
    passedTestCaseCount?: number;
    codeIssueCount?: number;
    correctionRoundResults?: CorrectionRoundResultDTO[];
    assessableCorrectionRounds?: number[];
    olderAssessmentDrafts?: AssessmentDraftDTO[];
}

export interface AssessmentQueueSearch {
    page: number;
    pageSize: number;
    sortingOrder: SortingOrder;
    sortedColumn: string;
    searchTerm: string;
}
