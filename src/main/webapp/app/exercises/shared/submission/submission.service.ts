import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import dayjs from 'dayjs/esm';
import { createRequestOption } from 'app/shared/util/request.util';
import { Result } from 'app/entities/result.model';
import { Submission, getLatestSubmissionResult, setLatestSubmissionResult } from 'app/entities/submission.model';
import { filter, map, tap } from 'rxjs/operators';
import { TextSubmission } from 'app/entities/text-submission.model';
import { Feedback } from 'app/entities/feedback.model';
import { Complaint } from 'app/entities/complaint.model';
import { ComplaintResponseService } from 'app/complaints/complaint-response.service';
import { AccountService } from 'app/core/auth/account.service';
import { ParticipationService } from 'app/exercises/shared/participation/participation.service';
import { convertDateFromServer } from 'app/utils/date.utils';

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

    constructor(private http: HttpClient, private complaintResponseService: ComplaintResponseService, private accountService: AccountService) {}

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
            map((res) => this.convertSubmissionArrayResponseDatesFromServer(res)),
            filter((res) => !!res.body),
            tap((res) =>
                res.body!.forEach((submission) => {
                    this.reconnectSubmissionAndResult(submission);
                    this.setSubmissionAccessRights(submission);
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

    /**
     * Find more feedback requests for tutor in this exercise.
     * @param exerciseId
     */
    getSubmissionsWithMoreFeedbackRequestsForTutor(exerciseId: number): Observable<HttpResponse<SubmissionWithComplaintDTO[]>> {
        return this.http
            .get<SubmissionWithComplaintDTO[]>(`api/exercises/${exerciseId}/more-feedback-requests-with-complaints`, { observe: 'response' })
            .pipe(map((res) => this.convertDTOsFromServer(res)));
    }

    protected convertDTOsFromServer(res: HttpResponse<SubmissionWithComplaintDTO[]>) {
        if (res.body) {
            res.body.forEach((dto) => {
                dto.submission = this.convertSubmissionDateFromServer(dto.submission);
                dto.complaint = this.convertComplaintDatesFromServer(dto.complaint);
                this.setSubmissionAccessRights(dto.submission);
            });
        }
        return res;
    }

    protected convertSubmissionDateFromServer(submission: Submission) {
        submission.submissionDate = submission.submissionDate ? dayjs(submission.submissionDate) : undefined;
        this.reconnectSubmissionAndResult(submission);
        return submission;
    }

    convertComplaintDatesFromServer(complaint: Complaint) {
        complaint.submittedTime = convertDateFromServer(complaint.submittedTime);
        if (complaint.complaintResponse) {
            this.complaintResponseService.convertComplaintResponseDatesFromServer(complaint.complaintResponse);
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

    convertResultArrayDatesFromServer(results?: Result[]) {
        const convertedResults: Result[] = [];
        if (results != undefined && results.length > 0) {
            results.forEach((result: Result) => {
                result.completionDate = convertDateFromServer(result.completionDate);
                convertedResults.push(result);
            });
        }
        return convertedResults;
    }

    convertSubmissionArrayDatesFromServer(submissions?: Submission[]) {
        const convertedSubmissions: Submission[] = [];
        if (submissions != undefined && submissions.length > 0) {
            submissions.forEach((submission: Submission) => {
                if (submission !== null) {
                    submission.submissionDate = convertDateFromServer(submission.submissionDate);
                    this.reconnectSubmissionAndResult(submission);
                    convertedSubmissions.push(submission);
                }
            });
        }
        return convertedSubmissions;
    }

    protected convertSubmissionArrayResponseDatesFromServer(res: EntityArrayResponseType): EntityArrayResponseType {
        if (res.body) {
            this.convertSubmissionArrayDatesFromServer(res.body);
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

    public convertResponse(res: EntityResponseType): EntityResponseType {
        const body: Submission = this.convertSubmissionFromServer(res.body!);
        return res.clone({ body });
    }

    public convertArrayResponse<T extends Submission>(res: HttpResponse<T[]>): HttpResponse<T[]> {
        const jsonResponse: T[] = res.body!;
        const body: T[] = [];
        for (let i = 0; i < jsonResponse.length; i++) {
            body.push(this.convertSubmissionFromServer(jsonResponse[i]));
        }
        return res.clone({ body });
    }

    public convertSubmissionResponseFromServer<T extends Submission>(response: HttpResponse<T>): HttpResponse<T> {
        const submission = response.body!;
        setLatestSubmissionResult(submission, getLatestSubmissionResult(submission));
        this.setSubmissionAccessRights(submission);
        SubmissionService.convertConnectedParticipationFromServer(submission);
        return response;
    }

    /**
     * Sets the result and the access rights for the submission.
     *
     * @param submission
     * @return convertedSubmission with set result and access rights
     * @private
     */
    public convertSubmissionFromServer<T extends Submission>(submission: T): T {
        const convertedSubmission = this.convert(submission);
        setLatestSubmissionResult(convertedSubmission, getLatestSubmissionResult(convertedSubmission));
        this.setSubmissionAccessRights(convertedSubmission);
        SubmissionService.convertConnectedParticipationFromServer(convertedSubmission);
        return convertedSubmission;
    }

    /**
     * Convert a Submission to a JSON which can be sent to the server.
     */
    public convert<T extends Submission>(submission: T): T {
        return Object.assign({}, submission);
    }

    /**
     * Sets the access rights for the exercise that is referenced by the participation of the submission.
     *
     * @param submission
     * @return submission with set access rights
     */
    public setSubmissionAccessRights(submission: Submission): Submission {
        if (submission.participation?.exercise) {
            this.accountService.setAccessRightsForExerciseAndReferencedCourse(submission.participation.exercise);
        }
        return submission;
    }

    /**
     * Converts the participation that is connected to the given submission from server to client format.
     * @param submission to which the conversion should be applied.
     * @private
     */
    private static convertConnectedParticipationFromServer(submission: Submission): Submission {
        if (submission.participation) {
            submission.participation = ParticipationService.convertParticipationDatesFromServer(submission.participation);
        }
        return submission;
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
                    if (secondFeedback.copiedFeedbackId === undefined && this.areFeedbacksCopies(firstFeedback, secondFeedback)) {
                        secondFeedback.copiedFeedbackId = firstFeedback.id;
                    } else if (secondFeedback.copiedFeedbackId === firstFeedback.id && !this.areFeedbacksCopies(firstFeedback, secondFeedback)) {
                        secondFeedback.copiedFeedbackId = undefined;
                    }
                });
            });
        }
    }

    /**
     * Checks if one of the two Feedback instances directly copied from the other and unmodified
     * by comparing a set of fields for equality.
     * @param firstFeedback
     * @param secondFeedback
     * @returns true if the compared set of fields match, false otherwise.
     */
    private areFeedbacksCopies(firstFeedback: Feedback, secondFeedback: Feedback) {
        return (
            secondFeedback.type === firstFeedback.type &&
            secondFeedback.credits === firstFeedback.credits &&
            secondFeedback.detailText === firstFeedback.detailText &&
            secondFeedback.reference === firstFeedback.reference &&
            secondFeedback.text === firstFeedback.text
        );
    }
}
