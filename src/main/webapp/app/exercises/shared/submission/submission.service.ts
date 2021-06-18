import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { SERVER_API_URL } from 'app/app.constants';
import * as moment from 'moment';
import { createRequestOption } from 'app/shared/util/request-util';
import { Result } from 'app/entities/result.model';
import { getLatestSubmissionResult, setLatestSubmissionResult, Submission } from 'app/entities/submission.model';
import { filter, map, tap } from 'rxjs/operators';
import { TextSubmission } from 'app/entities/text-submission.model';
import { Feedback } from 'app/entities/feedback.model';
import { Complaint } from 'app/entities/complaint.model';
import { ComplaintResponseService } from 'app/complaints/complaint-response.service';

export type EntityResponseType = HttpResponse<Submission>;
export type EntityArrayResponseType = HttpResponse<Submission[]>;

export class SubmissionWithComplaintDTO {
    public submission: Submission;
    public complaint: Complaint;
}

@Injectable({ providedIn: 'root' })
export class SubmissionService {
    public resourceUrl = SERVER_API_URL + 'api/submissions';
    public resourceUrlParticipation = SERVER_API_URL + 'api/participations';

    constructor(private http: HttpClient, private complaintResponseService: ComplaintResponseService) {}

    /**
     * Delete an existing submission
     * @param submissionId - The id of the submission to be deleted
     * @param req - A request with additional options in it
     */
    delete(submissionId: number, req?: any): Observable<HttpResponse<void>> {
        const options = createRequestOption(req);
        return this.http.delete<void>(`${this.resourceUrl}/${submissionId}`, { params: options, observe: 'response' });
    }

    /**
     * Find all submissions of a given participation
     * @param {number} participationId - The id of the participation to be searched for
     */
    findAllSubmissionsOfParticipation(participationId: number): Observable<EntityArrayResponseType> {
        return this.http.get<Submission[]>(`${this.resourceUrlParticipation}/${participationId}/submissions`, { observe: 'response' }).pipe(
            map((res) => this.convertDateArrayFromServer(res)),
            filter((res) => !!res.body),
            tap((res) =>
                res.body!.forEach((submission) => {
                    this.reconnectSubmissionAndResult(submission);
                }),
            ),
        );
    }

    /**
     * Find the submissions with complaints for a tutor for a specified exercise (complaintType == 'COMPLAINT').
     * @param exerciseId
     */
    getSubmissionsWithComplaintsForTutor(exerciseId: number): Observable<HttpResponse<SubmissionWithComplaintDTO[]>> {
        return this.http
            .get<SubmissionWithComplaintDTO[]>(`api/exercises/${exerciseId}/submissions-with-complaints`, { observe: 'response' })
            .pipe(map((res) => this.convertDTOsFromServer(res)));
    }

    protected convertDTOsFromServer(res: HttpResponse<SubmissionWithComplaintDTO[]>) {
        if (res.body) {
            res.body.forEach((dto) => {
                dto.submission = this.convertSubmissionDateFromServer(dto.submission);
                dto.complaint = this.convertDateFromServerComplaint(dto.complaint);
            });
        }
        return res;
    }

    protected convertSubmissionDateFromServer(submission: Submission) {
        submission.submissionDate = submission.submissionDate ? moment(submission.submissionDate) : undefined;
        this.reconnectSubmissionAndResult(submission);
        return submission;
    }

    convertDateFromServerComplaint(complaint: Complaint) {
        complaint.submittedTime = complaint.submittedTime ? moment(complaint.submittedTime) : undefined;
        if (complaint.complaintResponse) {
            this.complaintResponseService.convertDatesToMoment(complaint.complaintResponse);
        }
        return complaint;
    }

    /**
     * reconnect submission and result
     * @param submission
     */
    private reconnectSubmissionAndResult(submission: Submission) {
        const result = getLatestSubmissionResult(submission);
        if (result) {
            setLatestSubmissionResult(submission, result);
            result.submission = submission;
        }
    }

    convertResultsDateFromServer(results?: Result[]) {
        const convertedResults: Result[] = [];
        if (results != undefined && results.length > 0) {
            results.forEach((result: Result) => {
                result.completionDate = result.completionDate ? moment(result.completionDate) : undefined;
                convertedResults.push(result);
            });
        }
        return convertedResults;
    }

    convertSubmissionsDateFromServer(submissions?: Submission[]) {
        const convertedSubmissions: Submission[] = [];
        if (submissions != undefined && submissions.length > 0) {
            submissions.forEach((submission: Submission) => {
                if (submission !== null) {
                    submission.submissionDate = submission.submissionDate ? moment(submission.submissionDate) : undefined;
                    this.reconnectSubmissionAndResult(submission);
                    convertedSubmissions.push(submission);
                }
            });
        }
        return convertedSubmissions;
    }

    protected convertDateArrayFromServer(res: EntityArrayResponseType): EntityArrayResponseType {
        if (res.body) {
            this.convertSubmissionsDateFromServer(res.body);
        }
        return res;
    }

    getTestRunSubmissionsForExercise(exerciseId: number): Observable<HttpResponse<Submission[]>> {
        return this.http
            .get<TextSubmission[]>(`api/exercises/${exerciseId}/test-run-submissions`, {
                observe: 'response',
            })
            .pipe(map((res: HttpResponse<TextSubmission[]>) => this.convertArrayResponse(res)));
    }

    private convertArrayResponse(res: HttpResponse<Submission[]>): HttpResponse<Submission[]> {
        const jsonResponse: Submission[] = res.body!;
        const body: Submission[] = [];
        for (let i = 0; i < jsonResponse.length; i++) {
            body.push(this.convertItemFromServer(jsonResponse[i]));
        }
        return res.clone({ body });
    }

    /**
     * Convert a returned JSON object to TextSubmission.
     */
    private convertItemFromServer(submission: Submission): Submission {
        return Object.assign({}, submission);
    }

    /**
     * Sets the transient property copiedFeedback for feedbacks when comparing a submissions results of two correction rounds
     * copiedFeedback indicates if the feedback is directly copied and unmodified compared to the first correction round
     *
     * @param correctionRound current correction round
     * @param submission current submission
     */
    public handleFeedbackCorrectionRoundTag(correctionRound: number, submission: Submission) {
        if (correctionRound > 0 && submission?.results && submission.results.length > 1) {
            const firstResult = submission!.results![0] as Result;
            const secondCorrectionFeedback1 = submission!.results![1].feedbacks as Feedback[];
            secondCorrectionFeedback1!.forEach((secondFeedback) => {
                firstResult.feedbacks!.forEach((firstFeedback) => {
                    if (
                        secondFeedback.copiedFeedbackId === undefined &&
                        secondFeedback.type === firstFeedback.type &&
                        secondFeedback.credits === firstFeedback.credits &&
                        secondFeedback.detailText === firstFeedback.detailText &&
                        secondFeedback.reference === firstFeedback.reference &&
                        secondFeedback.text === firstFeedback.text
                    ) {
                        secondFeedback.copiedFeedbackId = firstFeedback.id;
                    } else if (
                        secondFeedback.copiedFeedbackId === firstFeedback.id &&
                        !(
                            secondFeedback.type === firstFeedback.type &&
                            secondFeedback.credits === firstFeedback.credits &&
                            secondFeedback.detailText === firstFeedback.detailText &&
                            secondFeedback.reference === firstFeedback.reference &&
                            secondFeedback.text === firstFeedback.text
                        )
                    ) {
                        secondFeedback.copiedFeedbackId = undefined;
                    }
                });
            });
        }
    }
}
