import { Injectable } from '@angular/core';
import { SERVER_API_URL } from 'app/app.constants';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { tap } from 'rxjs/operators';
import { Result } from 'app/entities/result';
import { StudentParticipation } from 'app/entities/participation';
import { Feedback } from 'app/entities/feedback';
import * as moment from 'moment';
import { ComplaintResponse } from 'app/entities/complaint-response';

type EntityResponseType = HttpResponse<Result>;

@Injectable({
    providedIn: 'root',
})
export class TextAssessmentsService {
    private readonly resourceUrl = SERVER_API_URL + 'api/text-assessments';

    constructor(private http: HttpClient) {}

    public save(textAssessments: Feedback[], exerciseId: number, resultId: number): Observable<EntityResponseType> {
        return this.http
            .put<Result>(`${this.resourceUrl}/exercise/${exerciseId}/result/${resultId}`, textAssessments, { observe: 'response' })
            .map((res: EntityResponseType) => this.convertResponse(res));
    }

    public submit(textAssessments: Feedback[], exerciseId: number, resultId: number): Observable<EntityResponseType> {
        return this.http
            .put<Result>(`${this.resourceUrl}/exercise/${exerciseId}/result/${resultId}/submit`, textAssessments, { observe: 'response' })
            .map((res: EntityResponseType) => this.convertResponse(res));
    }

    public updateAssessmentAfterComplaint(feedbacks: Feedback[], complaintResponse: ComplaintResponse, submissionId: number): Observable<EntityResponseType> {
        const url = `${this.resourceUrl}/text-submissions/${submissionId}/assessment-after-complaint`;
        const assessmentUpdate = {
            feedbacks,
            complaintResponse,
        };
        return this.http
            .put<Result>(url, assessmentUpdate, { observe: 'response' })
            .map((res: EntityResponseType) => this.convertResponse(res));
    }

    public cancelAssessment(exerciseId: number, submissionId: number): Observable<void> {
        return this.http.put<void>(`${this.resourceUrl}/exercise/${exerciseId}/submission/${submissionId}/cancel-assessment`, null);
    }

    public getResultWithPredefinedTextblocks(resultId: number): Observable<EntityResponseType> {
        return this.http
            .get<Result>(`${this.resourceUrl}/result/${resultId}/with-textblocks`, { observe: 'response' })
            .map((res: EntityResponseType) => this.convertResponse(res));
    }

    public getFeedbackDataForExerciseSubmission(submissionId: number): Observable<StudentParticipation> {
        return this.http.get<StudentParticipation>(`${this.resourceUrl}/submission/${submissionId}`).pipe(
            // Wire up Result and Submission
            tap((sp: StudentParticipation) => (sp.submissions[0].result = sp.results[0])),
        );
    }

    public getExampleAssessment(exerciseId: number, submissionId: number): Observable<Result> {
        return this.http.get<Result>(`${this.resourceUrl}/exercise/${exerciseId}/submission/${submissionId}/exampleAssessment`);
    }

    getParticipationForSubmissionWithoutAssessment(exerciseId: number) {
        return this.http.get<StudentParticipation>(`${SERVER_API_URL}api/exercise/${exerciseId}/participation-without-assessment`);
    }

    private convertResponse(res: EntityResponseType): EntityResponseType {
        const result = this.convertItemFromServer(res.body!);

        if (result.completionDate) {
            result.completionDate = moment(result.completionDate);
        }
        if (result.submission && result.submission.submissionDate) {
            result.submission.submissionDate = moment(result.submission.submissionDate);
        }
        if (result.participation && result.participation.initializationDate) {
            result.participation.initializationDate = moment(result.participation.initializationDate);
        }

        return res.clone({ body: result });
    }

    private convertItemFromServer(result: Result): Result {
        return Object.assign({}, result);
    }
}
