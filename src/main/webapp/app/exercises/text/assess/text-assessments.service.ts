import { Injectable } from '@angular/core';
import { SERVER_API_URL } from 'app/app.constants';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { tap } from 'rxjs/operators';
import { Result } from 'app/entities/result.model';
import * as moment from 'moment';
import { ComplaintResponse } from 'app/entities/complaint-response.model';
import { Feedback } from 'app/entities/feedback.model';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { TextBlock } from 'app/entities/text-block.model';
import { TextBlockRef } from 'app/entities/text-block-ref.model';

type EntityResponseType = HttpResponse<Result>;
type TextAssessmentDTO = { feedbacks: Feedback[]; textBlocks: TextBlock[] };

@Injectable({
    providedIn: 'root',
})
export class TextAssessmentsService {
    private readonly resourceUrl = SERVER_API_URL + 'api/text-assessments';

    constructor(private http: HttpClient) {}

    public save(exerciseId: number, resultId: number, feedbacks: Feedback[], textBlocks: TextBlock[]): Observable<EntityResponseType> {
        const body = TextAssessmentsService.prepareFeedbacksAndTextblocksForRequest(feedbacks, textBlocks);
        return this.http
            .put<Result>(`${this.resourceUrl}/exercise/${exerciseId}/result/${resultId}`, body, { observe: 'response' })
            .map((res: EntityResponseType) => TextAssessmentsService.convertResponse(res));
    }

    public submit(exerciseId: number, resultId: number, feedbacks: Feedback[], textBlocks: TextBlock[]): Observable<EntityResponseType> {
        const body = TextAssessmentsService.prepareFeedbacksAndTextblocksForRequest(feedbacks, textBlocks);
        return this.http
            .put<Result>(`${this.resourceUrl}/exercise/${exerciseId}/result/${resultId}/submit`, body, { observe: 'response' })
            .map((res: EntityResponseType) => TextAssessmentsService.convertResponse(res));
    }

    public updateAssessmentAfterComplaint(feedbacks: Feedback[], complaintResponse: ComplaintResponse, submissionId: number): Observable<EntityResponseType> {
        const url = `${this.resourceUrl}/text-submissions/${submissionId}/assessment-after-complaint`;
        const assessmentUpdate = {
            feedbacks,
            complaintResponse,
        };
        return this.http
            .put<Result>(url, assessmentUpdate, { observe: 'response' })
            .map((res: EntityResponseType) => TextAssessmentsService.convertResponse(res));
    }

    public cancelAssessment(exerciseId: number, submissionId: number): Observable<void> {
        return this.http.put<void>(`${this.resourceUrl}/exercise/${exerciseId}/submission/${submissionId}/cancel-assessment`, null);
    }

    public getResultWithPredefinedTextblocks(resultId: number): Observable<EntityResponseType> {
        return this.http
            .get<Result>(`${this.resourceUrl}/result/${resultId}/with-textblocks`, { observe: 'response' })
            .map((res: EntityResponseType) => TextAssessmentsService.convertResponse(res));
    }

    public getFeedbackDataForExerciseSubmission(submissionId: number): Observable<StudentParticipation> {
        return this.http.get<StudentParticipation>(`${this.resourceUrl}/submission/${submissionId}`).pipe(
            // Wire up Result and Submission
            tap((sp: StudentParticipation) => (sp.submissions[0].result = sp.results[0])),
            tap((sp: StudentParticipation) => (sp.submissions[0].participation = sp)),
            tap((sp: StudentParticipation) => (sp.results[0].submission = sp.submissions[0])),
            tap((sp: StudentParticipation) => (sp.results[0].participation = sp)),
            // Make sure Feedbacks Array is initialized
            tap((sp: StudentParticipation) => (sp.results[0].feedbacks = sp.results[0].feedbacks || [])),
        );
    }

    public getExampleResult(exerciseId: number, submissionId: number): Observable<Result> {
        return this.http.get<Result>(`${this.resourceUrl}/exercise/${exerciseId}/submission/${submissionId}/example-result`);
    }

    private static prepareFeedbacksAndTextblocksForRequest(feedbacks: Feedback[], textBlocks: TextBlock[]): TextAssessmentDTO {
        feedbacks = feedbacks.map((f) => {
            f = Object.assign({}, f);
            f.result = null;
            return f;
        });
        textBlocks = textBlocks.map((tb) => {
            tb = Object.assign({}, tb);
            tb.submission = undefined;
            return tb;
        });

        return { feedbacks, textBlocks };
    }

    private static convertResponse(res: EntityResponseType): EntityResponseType {
        const result = TextAssessmentsService.convertItemFromServer(res.body!);

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

    private static convertItemFromServer(result: Result): Result {
        return Object.assign({}, result);
    }

    public static matchBlocksWithFeedbacks(blocks: TextBlock[], feedbacks: Feedback[]): TextBlockRef[] {
        return blocks.map(
            (block: TextBlock) =>
                new TextBlockRef(
                    block,
                    feedbacks.find(({ reference }) => block.id === reference),
                ),
        );
    }
}
