import dayjs from 'dayjs/esm';

import { AssessmentType } from 'app/assessment/shared/entities/assessment-type.model';
import { FeedbackType } from 'app/assessment/shared/entities/feedback.model';
import { GradingInstructionDTO } from 'app/exercise/shared/exercise-update-shared-dto.model';
import { Visibility } from 'app/programming/shared/entities/programming-exercise-test-case.model';
import { FileUploadSubmissionDTO } from 'app/fileupload/shared/entities/file-upload-submission.model';

export interface FileUploadFeedbackInputDTO {
    id?: number;
    text?: string;
    detailText?: string;
    reference?: string;
    credits?: number;
    positive?: boolean;
    type?: FeedbackType;
    visibility?: Visibility;
    gradingInstruction?: GradingInstructionDTO;
}

export interface FileUploadAssessmentInputDTO {
    feedbacks: FileUploadFeedbackInputDTO[];
    assessmentNote?: string;
}

export interface FileUploadComplaintResponseInputDTO {
    id: number;
    responseText?: string;
    complaintIsAccepted: boolean;
}

export interface FileUploadAssessmentUpdateDTO {
    feedbacks: FileUploadFeedbackInputDTO[];
    complaintResponse: FileUploadComplaintResponseInputDTO;
    assessmentNote?: string;
}

export interface FileUploadUserDTO {
    id: number;
    login: string;
    name?: string;
    firstName?: string;
    lastName?: string;
    imageUrl?: string;
}

export interface FileUploadAssessmentNoteDTO {
    id: number;
    note?: string;
    creator?: FileUploadUserDTO;
}

export interface FileUploadFeedbackDTO {
    id: number;
    text?: string;
    detailText?: string;
    hasLongFeedbackText?: boolean;
    reference?: string;
    credits?: number;
    positive?: boolean;
    type?: FeedbackType;
    visibility?: Visibility;
    gradingInstruction?: GradingInstructionDTO;
}

export interface FileUploadResultDTO {
    id: number;
    completionDate?: dayjs.Dayjs;
    successful?: boolean;
    score?: number;
    rated: boolean;
    assessmentType?: AssessmentType;
    hasComplaint?: boolean;
    exampleResult?: boolean;
    assessmentNote?: FileUploadAssessmentNoteDTO;
    assessor?: FileUploadUserDTO;
    feedbacks?: FileUploadFeedbackDTO[];
    submission?: FileUploadSubmissionDTO;
}
