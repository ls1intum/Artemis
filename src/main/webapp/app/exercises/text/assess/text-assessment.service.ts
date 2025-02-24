import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { map, tap } from 'rxjs/operators';
import { Result } from 'app/entities/result.model';
import { ComplaintResponse } from 'app/entities/complaint-response.model';
import { Feedback } from 'app/entities/feedback.model';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { TextBlock } from 'app/entities/text/text-block.model';
import { TextBlockRef } from 'app/entities/text/text-block-ref.model';
import { Submission, getLatestSubmissionResult, getSubmissionResultByCorrectionRound, getSubmissionResultById, setLatestSubmissionResult } from 'app/entities/submission.model';
import { Participation } from 'app/entities/participation/participation.model';
import { TextAssessmentEvent } from 'app/entities/text/text-assesment-event.model';
import { AccountService } from 'app/core/auth/account.service';
import { convertDateFromServer } from 'app/utils/date.utils';

type EntityResponseType = HttpResponse<Result>;
type EntityResponseEventType = HttpResponse<TextAssessmentEvent>;
type TextAssessmentDTO = { feedbacks: Feedback[]; textBlocks: TextBlock[]; assessmentNote?: string };

@Injectable({
    providedIn: 'root',
})
export class TextAssessmentService {
    private http = inject(HttpClient);
    private accountService = inject(AccountService);

    private readonly RESOURCE_URL = 'api';

    /**
     * Saves the passed feedback items of the assessment.
     * @param participationId id of the participation the assessed submission was made to of type {number}
     * @param resultId id of the corresponding result of type {number}
     * @param feedbacks list of feedback made during assessment of type {Feedback[]}
     * @param textBlocks list of text blocks of type {TextBlock[]}
     * @param assessmentNote the internal tutor note for the text assessment
     */
    public save(participationId: number, resultId: number, feedbacks: Feedback[], textBlocks: TextBlock[], assessmentNote?: string): Observable<EntityResponseType> {
        const body = TextAssessmentService.prepareFeedbacksAndTextblocksForRequest(feedbacks, textBlocks, assessmentNote);
        return this.http
            .put<Result>(`${this.RESOURCE_URL}/participations/${participationId}/results/${resultId}/text-assessment`, body, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.convertResultEntityResponseTypeFromServer(res)));
    }

    /**
     * Submits the passed feedback items of the assessment.
     * @param participationId the assessed submission was made to of type {number}
     * @param resultId of the corresponding result of type {number}
     * @param feedbacks made during assessment of type {Feedback[]}
     * @param textBlocks of type {TextBlock[]}
     * @param assessmentNote of the result
     */
    public submit(participationId: number, resultId: number, feedbacks: Feedback[], textBlocks: TextBlock[], assessmentNote?: string): Observable<EntityResponseType> {
        const body = TextAssessmentService.prepareFeedbacksAndTextblocksForRequest(feedbacks, textBlocks, assessmentNote);
        return this.http
            .post<Result>(`${this.RESOURCE_URL}/participations/${participationId}/results/${resultId}/submit-text-assessment`, body, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.convertResultEntityResponseTypeFromServer(res)));
    }

    /**
     * Submits an assessment event to the artemis analytics for text exercises.
     * @param assessmentEvent an event of type {TextAssessmentEvent}
     */
    public addTextAssessmentEvent(assessmentEvent: TextAssessmentEvent): Observable<EntityResponseEventType> {
        const body = Object.assign({}, assessmentEvent);
        return this.http
            .post<TextAssessmentEvent>(this.RESOURCE_URL + '/event-insights/text-assessment/events', body, { observe: 'response' })
            .pipe(map((res: EntityResponseEventType) => Object.assign({}, res)));
    }

    /**
     * Submits an assessment event to the artemis analytics for text exercises.
     * @param courseId the id of the respective assessment event course id
     * @param exerciseId the id of the respective assessment event exercise id
     */
    public getNumberOfTutorsInvolvedInAssessment(courseId: number, exerciseId: number): Observable<number> {
        return this.http.get<number>(`${this.RESOURCE_URL}/event-insights/text-assessment/courses/${courseId}/text-exercises/${exerciseId}/tutors-involved`);
    }

    /**
     * Updates an assessment after a complaint.
     * @param feedbacks made during assessment of type {Feedback[]}
     * @param textBlocks of type {TextBlock[]}
     * @param complaintResponse of type {ComplaintResponse}
     * @param submissionId of corresponding submission of type {number}
     * @param assessmentNote of the result, if one exists
     */
    public updateAssessmentAfterComplaint(
        feedbacks: Feedback[],
        textBlocks: TextBlock[],
        complaintResponse: ComplaintResponse,
        submissionId: number,
        participationId: number,
        assessmentNote?: string,
    ): Observable<EntityResponseType> {
        const url = `${this.RESOURCE_URL}/participations/${participationId}/submissions/${submissionId}/text-assessment-after-complaint`;
        const assessmentUpdate = {
            feedbacks,
            textBlocks,
            complaintResponse,
            assessmentNote,
        };
        return this.http.put<Result>(url, assessmentUpdate, { observe: 'response' }).pipe(map((res: EntityResponseType) => this.convertResultEntityResponseTypeFromServer(res)));
    }

    saveExampleAssessment(exerciseId: number, exampleSubmissionId: number, feedbacks: Feedback[], textBlocks: TextBlock[]): Observable<EntityResponseType> {
        const url = `${this.RESOURCE_URL}/exercises/${exerciseId}/example-submissions/${exampleSubmissionId}/example-text-assessment`;
        const body = TextAssessmentService.prepareFeedbacksAndTextblocksForRequest(feedbacks, textBlocks);
        return this.http.put<Result>(url, body, { observe: 'response' }).pipe(map((res: EntityResponseType) => this.convertResultEntityResponseTypeFromServer(res)));
    }

    /**
     * Cancels an assessment.
     * @param participationId the assessed submission was made to of type {number}
     * @param submissionId of corresponding submission of type {number}
     */
    public cancelAssessment(participationId: number, submissionId: number): Observable<void> {
        return this.http.post<void>(`${this.RESOURCE_URL}/participations/${participationId}/submissions/${submissionId}/cancel-assessment`, undefined);
    }

    /**
     * Deletes an assessment.
     * @param participationId id of the participation, to which the assessment and the submission belong to
     * @param submissionId id of the submission, to which the assessment belongs to
     * @param resultId     id of the result which is deleted
     */
    deleteAssessment(participationId: number, submissionId: number, resultId: number): Observable<void> {
        return this.http.delete<void>(`${this.RESOURCE_URL}/participations/${participationId}/text-submissions/${submissionId}/results/${resultId}`);
    }

    /**
     * @param submissionId id of the submission for which the feedback items should be retrieved of type {number}
     * @param correctionRound
     * @param resultId id of the searched result (if instructors search for a specific result)
     */
    public getFeedbackDataForExerciseSubmission(submissionId: number, correctionRound = 0, resultId?: number): Observable<StudentParticipation> {
        let params = new HttpParams();
        if (resultId && resultId > 0) {
            // in case resultId is set, we do not need the correction round
            params = params.set('resultId', resultId!.toString());
        } else {
            params = params.set('correction-round', correctionRound.toString());
        }
        return this.http
            .get<StudentParticipation>(`${this.RESOURCE_URL}/text-submissions/${submissionId}/for-assessment`, { observe: 'response', params })
            .pipe<HttpResponse<StudentParticipation>, StudentParticipation>(
                // Wire up Result and Submission
                tap((response: HttpResponse<StudentParticipation>) => {
                    const participation = response.body!;
                    if (participation.exercise) {
                        this.accountService.setAccessRightsForExercise(participation.exercise);
                    }
                    const submission = participation.submissions!.last()!;
                    let result;
                    if (resultId) {
                        result = getSubmissionResultById(submission, resultId);
                    } else {
                        result = getSubmissionResultByCorrectionRound(submission, correctionRound)!;
                    }
                    TextAssessmentService.reconnectResultsParticipation(participation, submission, result!);
                }),
                map<HttpResponse<StudentParticipation>, StudentParticipation>((response: HttpResponse<StudentParticipation>) => response.body!),
            );
    }

    /**
     * Gets an example result for defined exercise and submission.
     * @param exerciseId id of the exercise for which the example result should be retrieved of type {number}
     * @param submissionId id of the submission for which the example result should be retrieved of type {number}
     */
    public getExampleResult(exerciseId: number, submissionId: number): Observable<Result> {
        return this.http.get<Result>(`${this.RESOURCE_URL}/exercises/${exerciseId}/submissions/${submissionId}/example-result`);
    }

    /**
     * Deletes the example assessment associated with given example submission.
     *
     * @param exerciseId   for which the example assessment should be deleted
     * @param exampleSubmissionId for which the example assessment should be deleted
     */
    public deleteExampleAssessment(exerciseId: number, exampleSubmissionId: number): Observable<void> {
        return this.http.delete<void>(`${this.RESOURCE_URL}/exercises/${exerciseId}/example-submissions/${exampleSubmissionId}/example-text-assessment/feedback`);
    }

    private static prepareFeedbacksAndTextblocksForRequest(feedbacks: Feedback[], textBlocks: TextBlock[], assessmentNote?: string): TextAssessmentDTO {
        feedbacks = feedbacks.map((feedback) => {
            feedback = Object.assign({}, feedback);
            delete feedback.result;
            return feedback;
        });
        const textBlocksRequestObjects = textBlocks.map((textBlock) => {
            // We convert the text block to a request object, so that we can send it to the server.
            // This way, we omit the submissionId and avoid serializing it with private properties.
            return {
                id: textBlock.id,
                type: textBlock.type,
                startIndex: textBlock.startIndex,
                endIndex: textBlock.endIndex,
                text: textBlock.text,
            };
        });

        return { feedbacks, textBlocks: textBlocksRequestObjects, assessmentNote } as TextAssessmentDTO;
    }

    private convertResultEntityResponseTypeFromServer(res: EntityResponseType): EntityResponseType {
        const result = TextAssessmentService.convertItemFromServer(res.body!);
        result.completionDate = convertDateFromServer(result.completionDate);

        if (result.submission) {
            result.submission.submissionDate = convertDateFromServer(result.submission.submissionDate);
        }
        if (result.participation) {
            result.participation.initializationDate = convertDateFromServer(result.participation.initializationDate);
            result.participation.individualDueDate = convertDateFromServer(result.participation.individualDueDate);
            if (result.participation.exercise) {
                this.accountService.setAccessRightsForExercise(result.participation.exercise);
            }
        }

        return res.clone({ body: result });
    }

    private static convertItemFromServer(result: Result): Result {
        return Object.assign({}, result);
    }

    /**
     * Match given text blocks and feedback items by text block references.
     * @param blocks list of text blocks of type {TextBlock[]}
     * @param feedbacks list of feedback made during assessment of type {Feedback[]}
     */
    public static matchBlocksWithFeedbacks(blocks: TextBlock[], feedbacks: Feedback[]): TextBlockRef[] {
        return blocks.map(
            (block: TextBlock) =>
                new TextBlockRef(
                    block,
                    feedbacks.find(({ reference }) => block.id === reference),
                ),
        );
    }

    /**
     * Connects the participation with the submission and result
     *
     *  @param participation
     *  @param submission
     *  @param result
     */
    private static reconnectResultsParticipation(participation: Participation, submission: Submission, result: Result) {
        setLatestSubmissionResult(submission, getLatestSubmissionResult(submission));
        submission.participation = participation;
        participation.results = submission.results!;
        result.submission = submission;
        result.participation = participation;
        // Make sure Feedbacks Array is initialized
        result.feedbacks = result.feedbacks || [];
    }
}
