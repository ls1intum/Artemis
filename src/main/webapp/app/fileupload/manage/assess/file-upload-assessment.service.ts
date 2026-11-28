import { Service, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { ComplaintResponse } from 'app/assessment/shared/entities/complaint-response.model';
import { Feedback, convertFeedbackFromServer } from 'app/assessment/shared/entities/feedback.model';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { map } from 'rxjs/operators';
import { convertDateFromServer, convertDateStringFromServer } from 'app/foundation/util/date.utils';
import { hydrate } from 'app/foundation/util/deep-clone.util';
import { toFeedbackFromServer } from 'app/fileupload/shared/entities/file-upload-assessment-dto.model';
import { FileUploadSubmission } from 'app/fileupload/shared/entities/file-upload-submission.model';
import { addPublicFilePrefix } from 'app/app.constants';
import { FileUploadAssessmentApi } from 'app/openapi/api/file-upload-assessment-api';
import { FileUploadResult } from 'app/openapi/model/file-upload-result';
import { FileUploadFeedbackInput } from 'app/openapi/model/file-upload-feedback-input';
import { FileUploadComplaintResponseInput } from 'app/openapi/model/file-upload-complaint-response-input';

@Service()
export class FileUploadAssessmentService {
    private fileUploadAssessmentApi = inject(FileUploadAssessmentApi);

    saveAssessment(feedbacks: Feedback[], submissionId: number, assessmentNote: string | undefined, submit = false): Observable<Result> {
        const body = { feedbacks: feedbacks.map((feedback) => this.toFeedbackInputDTO(feedback)), assessmentNote };
        return this.fileUploadAssessmentApi.saveFileUploadAssessment(submissionId, body, submit).pipe(map((result) => this.convertResultFromServer(result)));
    }

    updateAssessmentAfterComplaint(feedbacks: Feedback[], complaintResponse: ComplaintResponse, submissionId: number, assessmentNote?: string): Observable<Result> {
        const assessmentUpdate = {
            feedbacks: feedbacks.map((feedback) => this.toFeedbackInputDTO(feedback)),
            complaintResponse: this.toComplaintResponseInputDTO(complaintResponse),
            assessmentNote,
        };
        return this.fileUploadAssessmentApi.updateFileUploadAssessmentAfterComplaint(submissionId, assessmentUpdate).pipe(map((result) => this.convertResultFromServer(result)));
    }

    cancelAssessment(submissionId: number, resultId?: number): Observable<void> {
        return this.fileUploadAssessmentApi.cancelAssessment(submissionId, resultId);
    }

    /**
     * Deletes an assessment.
     * @param participationId id of the participation, to which the assessment and the submission belong to
     * @param submissionId id of the submission, to which the assessment belongs to
     * @param resultId     id of the result which is deleted
     */
    deleteAssessment(participationId: number, submissionId: number, resultId: number): Observable<void> {
        return this.fileUploadAssessmentApi.deleteAssessment(participationId, submissionId, resultId);
    }

    private convertResultFromServer(dto: FileUploadResult): Result {
        // Annotated as Result rather than hydrate's Result & FileUploadResult intersection, so the
        // converted feedbacks below stay assignable to Feedback[] instead of the generated shape.
        const result: Result = hydrate(new Result(), dto);
        result.completionDate = convertDateStringFromServer(dto.completionDate);
        result.feedbacks = dto.feedbacks?.map((feedback) => convertFeedbackFromServer(toFeedbackFromServer(feedback)));
        if (dto.submission) {
            const submission: FileUploadSubmission = hydrate(new FileUploadSubmission(), dto.submission);
            submission.submissionDate = convertDateStringFromServer(dto.submission.submissionDate);
            submission.filePathUrl = addPublicFilePrefix(dto.submission.filePath);
            if (submission.participation) {
                submission.participation.initializationDate = convertDateFromServer(submission.participation.initializationDate);
                submission.participation.individualDueDate = convertDateFromServer(submission.participation.individualDueDate);
            }
            result.submission = submission;
        }
        return result;
    }

    private toFeedbackInputDTO(feedback: Feedback): FileUploadFeedbackInput {
        const gradingInstruction = feedback.gradingInstruction;
        return {
            id: feedback.id,
            text: feedback.text,
            detailText: feedback.detailText,
            reference: feedback.reference,
            credits: feedback.credits,
            positive: feedback.positive,
            type: feedback.type,
            visibility: feedback.testCase?.visibility,
            gradingInstruction: gradingInstruction
                ? {
                      id: gradingInstruction.id,
                      credits: gradingInstruction.credits,
                      gradingScale: gradingInstruction.gradingScale,
                      instructionDescription: gradingInstruction.instructionDescription,
                      feedback: gradingInstruction.feedback,
                      usageCount: gradingInstruction.usageCount,
                  }
                : undefined,
        };
    }

    private toComplaintResponseInputDTO(complaintResponse: ComplaintResponse): FileUploadComplaintResponseInput {
        const id = complaintResponse.id;
        const complaintIsAccepted = complaintResponse.complaint?.accepted;
        if (id === undefined || complaintIsAccepted === undefined) {
            throw new Error('Cannot update an assessment without a complaint response ID and acceptance decision.');
        }
        return {
            id,
            responseText: complaintResponse.responseText,
            complaintIsAccepted,
        };
    }
}
