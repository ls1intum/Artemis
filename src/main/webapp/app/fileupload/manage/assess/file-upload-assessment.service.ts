import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ComplaintResponse } from 'app/assessment/shared/entities/complaint-response.model';
import { Feedback, convertFeedbackFromServer } from 'app/assessment/shared/entities/feedback.model';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { map } from 'rxjs/operators';
import { convertDateFromServer } from 'app/foundation/util/date.utils';
import { hydrate } from 'app/foundation/util/deep-clone.util';
import {
    FileUploadAssessmentInputDTO,
    FileUploadAssessmentUpdateDTO,
    FileUploadComplaintResponseInputDTO,
    FileUploadFeedbackInputDTO,
    FileUploadResultDTO,
} from 'app/fileupload/shared/entities/file-upload-assessment-dto.model';
import { FileUploadSubmission } from 'app/fileupload/shared/entities/file-upload-submission.model';
import { addPublicFilePrefix } from 'app/app.constants';

export type EntityResponseType = HttpResponse<Result>;
type FileUploadResultDTOResponseType = HttpResponse<FileUploadResultDTO>;

@Injectable({
    providedIn: 'root',
})
export class FileUploadAssessmentService {
    private http = inject(HttpClient);

    private resourceUrl = 'api/fileupload';

    saveAssessment(feedbacks: Feedback[], submissionId: number, assessmentNote: string | undefined, submit = false): Observable<Result> {
        let params = new HttpParams();
        if (submit) {
            params = params.set('submit', 'true');
        }
        const body: FileUploadAssessmentInputDTO = { feedbacks: feedbacks.map((feedback) => this.toFeedbackInputDTO(feedback)), assessmentNote };
        const url = `${this.resourceUrl}/file-upload-submissions/${submissionId}/feedback`;
        return this.http.put<FileUploadResultDTO>(url, body, { params }).pipe(map((result) => this.convertResultFromServer(result)));
    }

    updateAssessmentAfterComplaint(feedbacks: Feedback[], complaintResponse: ComplaintResponse, submissionId: number, assessmentNote?: string): Observable<EntityResponseType> {
        const url = `${this.resourceUrl}/file-upload-submissions/${submissionId}/assessment-after-complaint`;
        const assessmentUpdate: FileUploadAssessmentUpdateDTO = {
            feedbacks: feedbacks.map((feedback) => this.toFeedbackInputDTO(feedback)),
            complaintResponse: this.toComplaintResponseInputDTO(complaintResponse),
            assessmentNote,
        };
        return this.http
            .put<FileUploadResultDTO>(url, assessmentUpdate, { observe: 'response' })
            .pipe(map((res: FileUploadResultDTOResponseType) => this.convertResultEntityResponseTypeFromServer(res)));
    }

    // TODO refactor all asssessment.service getAssessment calls to make similar REST calls
    getAssessment(submissionId: number): Observable<Result> {
        return this.http.get<FileUploadResultDTO>(`${this.resourceUrl}/file-upload-submissions/${submissionId}/result`).pipe(map((result) => this.convertResultFromServer(result)));
    }

    cancelAssessment(submissionId: number, resultId?: number): Observable<void> {
        const params = resultId ? new HttpParams().set('resultId', resultId) : undefined;
        return this.http.put<void>(`${this.resourceUrl}/file-upload-submissions/${submissionId}/cancel-assessment`, null, { params });
    }

    /**
     * Deletes an assessment.
     * @param participationId id of the participation, to which the assessment and the submission belong to
     * @param submissionId id of the submission, to which the assessment belongs to
     * @param resultId     id of the result which is deleted
     */
    deleteAssessment(participationId: number, submissionId: number, resultId: number): Observable<void> {
        return this.http.delete<void>(`${this.resourceUrl}/participations/${participationId}/file-upload-submissions/${submissionId}/results/${resultId}`);
    }

    private convertResultEntityResponseTypeFromServer(res: FileUploadResultDTOResponseType): EntityResponseType {
        const body = res.body;
        if (!body) {
            return res.clone<Result>({ body: null });
        }
        return res.clone({ body: this.convertResultFromServer(body) });
    }

    private convertResultFromServer(dto: FileUploadResultDTO): Result {
        // Annotated as Result rather than hydrate's Result & FileUploadResultDTO intersection, so the
        // converted feedbacks below stay assignable to Feedback[] instead of the DTO's stricter shape.
        const result: Result = hydrate(new Result(), dto);
        result.completionDate = convertDateFromServer(dto.completionDate);
        result.feedbacks = dto.feedbacks?.map(convertFeedbackFromServer);
        if (dto.submission) {
            const submission = hydrate(new FileUploadSubmission(), dto.submission);
            submission.submissionDate = convertDateFromServer(dto.submission.submissionDate);
            submission.filePathUrl = addPublicFilePrefix(dto.submission.filePath);
            if (submission.participation) {
                submission.participation.initializationDate = convertDateFromServer(submission.participation.initializationDate);
                submission.participation.individualDueDate = convertDateFromServer(submission.participation.individualDueDate);
            }
            result.submission = submission;
        }
        return result;
    }

    private toFeedbackInputDTO(feedback: Feedback): FileUploadFeedbackInputDTO {
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

    private toComplaintResponseInputDTO(complaintResponse: ComplaintResponse): FileUploadComplaintResponseInputDTO {
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
