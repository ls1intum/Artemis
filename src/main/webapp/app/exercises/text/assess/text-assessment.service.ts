import { Injectable } from '@angular/core';
import { HttpClient, HttpParams, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { map, tap } from 'rxjs/operators';
import { Result } from 'app/entities/result.model';
import dayjs from 'dayjs/esm';
import { ComplaintResponse } from 'app/entities/complaint-response.model';
import { Feedback } from 'app/entities/feedback.model';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { TextBlock } from 'app/entities/text-block.model';
import { TextBlockRef } from 'app/entities/text-block-ref.model';
import { cloneDeep } from 'lodash-es';
import { TextSubmission } from 'app/entities/text-submission.model';
import { FeedbackConflict } from 'app/entities/feedback-conflict';
import { getLatestSubmissionResult, getSubmissionResultByCorrectionRound, getSubmissionResultById, setLatestSubmissionResult, Submission } from 'app/entities/submission.model';
import { Participation } from 'app/entities/participation/participation.model';
import { TextAssessmentEvent } from 'app/entities/text-assesment-event.model';
import { AccountService } from 'app/core/auth/account.service';

type EntityResponseType = HttpResponse<Result>;
type EntityResponseEventType = HttpResponse<TextAssessmentEvent>;
type TextAssessmentDTO = { feedbacks: Feedback[]; textBlocks: TextBlock[] };

@Injectable({
    providedIn: 'root',
})
export class TextAssessmentService {
    private readonly resourceUrl = SERVER_API_URL + 'api';

    constructor(private http: HttpClient, private accountService: AccountService) {}

    /**
     * Saves the passed feedback items of the assessment.
     * @param participationId id of the participation the assessed submission was made to of type {number}
     * @param resultId id of the corresponding result of type {number}
     * @param feedbacks list of feedback made during assessment of type {Feedback[]}
     * @param textBlocks list of text blocks of type {TextBlock[]}
     */
    public save(participationId: number, resultId: number, feedbacks: Feedback[], textBlocks: TextBlock[]): Observable<EntityResponseType> {
        const body = TextAssessmentService.prepareFeedbacksAndTextblocksForRequest(feedbacks, textBlocks);
        return this.http
            .put<Result>(`${this.resourceUrl}/participations/${participationId}/results/${resultId}/text-assessment`, body, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.convertResponse(res)));
    }

    /**
     * Submits the passed feedback items of the assessment.
     * @param participationId the assessed submission was made to of type {number}
     * @param resultId of the corresponding result of type {number}
     * @param feedbacks made during assessment of type {Feedback[]}
     * @param textBlocks of type {TextBlock[]}
     */
    public submit(participationId: number, resultId: number, feedbacks: Feedback[], textBlocks: TextBlock[]): Observable<EntityResponseType> {
        const body = TextAssessmentService.prepareFeedbacksAndTextblocksForRequest(feedbacks, textBlocks);
        return this.http
            .post<Result>(`${this.resourceUrl}/participations/${participationId}/results/${resultId}/submit-text-assessment`, body, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.convertResponse(res)));
    }

    /**
     * Submits an assessment event to the artemis analytics for text exercises.
     * @param assessmentEvent an event of type {TextAssessmentEvent}
     */
    public addTextAssessmentEvent(assessmentEvent: TextAssessmentEvent): Observable<EntityResponseEventType> {
        const body = Object.assign({}, assessmentEvent);
        return this.http
            .post<TextAssessmentEvent>(this.resourceUrl + '/analytics/text-assessment/events', body, { observe: 'response' })
            .pipe(map((res: EntityResponseEventType) => Object.assign({}, res)));
    }

    /**
     * Submits an assessment event to the artemis analytics for text exercises.
     * @param courseId the id of the respective assessment event course id
     * @param exerciseId the id of the respective assessment event exercise id
     */
    public getNumberOfTutorsInvolvedInAssessment(courseId: number, exerciseId: number): Observable<number> {
        return this.http.get<number>(`${this.resourceUrl}/analytics/text-assessment/courses/${courseId}/text-exercises/${exerciseId}/tutors-involved`);
    }

    /**
     * Updates an assessment after a complaint.
     * @param feedbacks made during assessment of type {Feedback[]}
     * @param textBlocks of type {TextBlock[]}
     * @param complaintResponse of type {ComplaintResponse}
     * @param submissionId of corresponding submission of type {number}
     * @param participationId of the corresponding participation
     */
    public updateAssessmentAfterComplaint(
        feedbacks: Feedback[],
        textBlocks: TextBlock[],
        complaintResponse: ComplaintResponse,
        submissionId: number,
        participationId: number,
    ): Observable<EntityResponseType> {
        const url = `${this.resourceUrl}/participations/${participationId}/submissions/${submissionId}/text-assessment-after-complaint`;
        const assessmentUpdate = {
            feedbacks,
            textBlocks,
            complaintResponse,
        };
        return this.http.put<Result>(url, assessmentUpdate, { observe: 'response' }).pipe(map((res: EntityResponseType) => this.convertResponse(res)));
    }

    saveExampleAssessment(exerciseId: number, exampleSubmissionId: number, feedbacks: Feedback[], textBlocks: TextBlock[]): Observable<EntityResponseType> {
        const url = `${this.resourceUrl}/exercises/${exerciseId}/example-submissions/${exampleSubmissionId}/example-text-assessment`;
        const body = TextAssessmentService.prepareFeedbacksAndTextblocksForRequest(feedbacks, textBlocks);
        return this.http.put<Result>(url, body, { observe: 'response' }).pipe(map((res: EntityResponseType) => this.convertResponse(res)));
    }

    /**
     * Cancels an assessment.
     * @param participationId the assessed submission was made to of type {number}
     * @param submissionId of corresponding submission of type {number}
     */
    public cancelAssessment(participationId: number, submissionId: number): Observable<void> {
        return this.http.post<void>(`${this.resourceUrl}/participations/${participationId}/submissions/${submissionId}/cancel-assessment`, undefined);
    }

    /**
     * Deletes an assessment.
     * @param participationId id of the participation, to which the assessment and the submission belong to
     * @param submissionId id of the submission, to which the assessment belongs to
     * @param resultId     id of the result which is deleted
     */
    deleteAssessment(participationId: number, submissionId: number, resultId: number): Observable<void> {
        return this.http.delete<void>(`${this.resourceUrl}/participations/${participationId}/text-submissions/${submissionId}/results/${resultId}`);
    }

    /**
     * @param participationId id of the participation the submission belongs to
     * @param submissionId id of the submission for which the feedback items should be retrieved of type {number}
     * @param correctionRound
     * @param resultId id of the searched result (if instructors search for a specific result)
     */
    public getFeedbackDataForExerciseSubmission(participationId: number, submissionId: number, correctionRound = 0, resultId?: number): Observable<StudentParticipation> {
        let params = new HttpParams();
        if (resultId && resultId > 0) {
            // in case resultId is set, we do not need the correction round
            params = params.set('resultId', resultId!.toString());
        } else {
            params = params.set('correction-round', correctionRound.toString());
        }
        return this.http
            .get<StudentParticipation>(`${this.resourceUrl}/participations/${participationId}/submissions/${submissionId}/for-text-assessment`, { observe: 'response', params })
            .pipe<HttpResponse<StudentParticipation>, StudentParticipation>(
                // Wire up Result and Submission
                tap((response: HttpResponse<StudentParticipation>) => {
                    const participation = response.body!;
                    if (participation.exercise) {
                        this.accountService.setAccessRightsForExercise(participation.exercise);
                    }
                    const submission = participation.submissions![0];
                    let result;
                    if (resultId) {
                        result = getSubmissionResultById(submission, resultId);
                    } else {
                        result = getSubmissionResultByCorrectionRound(submission, correctionRound)!;
                    }
                    TextAssessmentService.reconnectResultsParticipation(participation, submission, result!);
                    (submission as TextSubmission).atheneTextAssessmentTrackingToken = response.headers.get('x-athene-tracking-authorization') || undefined;
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
        return this.http.get<Result>(`${this.resourceUrl}/exercises/${exerciseId}/submissions/${submissionId}/example-result`);
    }

    /**
     * Deletes the example assessment associated with given example submission.
     *
     * @param exerciseId   for which the example assessment should be deleted
     * @param exampleSubmissionId for which the example assessment should be deleted
     */
    public deleteExampleAssessment(exerciseId: number, exampleSubmissionId: number): Observable<void> {
        return this.http.delete<void>(`${this.resourceUrl}/exercises/${exerciseId}/example-submissions/${exampleSubmissionId}/example-text-assessment/feedback`);
    }

    /**
     * Gets an array of text submissions that contains conflicting feedback with the given feedback id.
     *
     * @param participationId the feedback belongs to
     * @param submissionId the feedback belongs to of type {number}
     * @param feedbackId to search for conflicts of type {number}
     */
    public getConflictingTextSubmissions(participationId: number, submissionId: number, feedbackId: number): Observable<TextSubmission[]> {
        return this.http.get<TextSubmission[]>(`${this.resourceUrl}/participations/${participationId}/submissions/${submissionId}/feedback/${feedbackId}/feedback-conflicts`);
    }

    /**
     * Set feedback conflict as solved. (Tutor decides it is not a conflict)
     *
     * @param exerciseId id of the exercise feedback conflict belongs to
     * @param feedbackConflictId id of the feedback conflict to be solved
     */
    public solveFeedbackConflict(exerciseId: number, feedbackConflictId: number): Observable<FeedbackConflict> {
        return this.http.post<FeedbackConflict>(`${this.resourceUrl}/exercises/${exerciseId}/feedback-conflicts/${feedbackConflictId}/solve`, undefined);
    }

    private static prepareFeedbacksAndTextblocksForRequest(feedbacks: Feedback[], textBlocks: TextBlock[]): TextAssessmentDTO {
        feedbacks = feedbacks.map((feedback) => {
            feedback = Object.assign({}, feedback);
            delete feedback.result;
            delete feedback.conflictingTextAssessments;
            return feedback;
        });
        textBlocks = textBlocks.map((textBlock) => {
            textBlock = Object.assign({}, textBlock);
            textBlock.submission = undefined;
            return textBlock;
        });

        return { feedbacks, textBlocks };
    }

    private convertResponse(res: EntityResponseType): EntityResponseType {
        const result = TextAssessmentService.convertItemFromServer(res.body!);

        if (result.completionDate) {
            result.completionDate = dayjs(result.completionDate);
        }
        if (result.submission?.submissionDate) {
            result.submission.submissionDate = dayjs(result.submission.submissionDate);
        }
        if (result.participation) {
            if (result.participation.initializationDate) {
                result.participation.initializationDate = dayjs(result.participation.initializationDate);
            }
            if (result.participation.individualDueDate) {
                result.participation.individualDueDate = dayjs(result.participation.individualDueDate);
            }
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
     * Convert Feedback elements to use single array of FeedbackConflicts in the Feedback class.
     * It is stored with two references on the server side.
     *
     * @param feedbacks list of Feedback elements to convert.
     */
    private static convertFeedbackConflictsFromServer(feedbacks: Feedback[]): void {
        feedbacks.forEach((feedback) => {
            feedback.conflictingTextAssessments = [...(feedback['firstConflicts'] || []), ...(feedback['secondConflicts'] || [])];
            delete feedback['firstConflicts'];
            delete feedback['secondConflicts'];
            feedback.conflictingTextAssessments.forEach((textAssessmentConflict) => {
                textAssessmentConflict.conflictingFeedbackId =
                    textAssessmentConflict['firstFeedback'].id === feedback.id ? textAssessmentConflict['secondFeedback'].id : textAssessmentConflict['firstFeedback'].id;
            });
        });
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
     * Track the change of the Feedback in Athene.
     *
     * The routing to athene is done using nginx on the production server.
     *
     * @param submission - The submission object that holds the data that is tracked
     * @param origin - The method that calls the the tracking method
     */
    public trackAssessment(submission?: TextSubmission, origin?: string) {
        if (submission?.atheneTextAssessmentTrackingToken) {
            // clone submission and resolve circular json properties
            const submissionForSending = cloneDeep(submission);
            if (submissionForSending.participation) {
                submissionForSending.participation.submissions = [];
                if (submissionForSending.participation.exercise) {
                    submissionForSending.participation.exercise.course = undefined;
                    submissionForSending.participation.exercise.exerciseGroup = undefined;
                }
            }
            submissionForSending.atheneTextAssessmentTrackingToken = undefined;

            submissionForSending.participation?.results!.forEach((result) => {
                result.participation = undefined;
                result.submission = undefined;
            });

            const trackingObject = {
                origin,
                textBlocks: submissionForSending.blocks,
                participation: submissionForSending.participation,
            };

            // The request is directly routed to athene via nginx
            this.http
                .post(`${SERVER_API_URL}athene-tracking/text-exercise-assessment`, trackingObject, {
                    headers: { 'X-Athene-Tracking-Authorization': submission.atheneTextAssessmentTrackingToken },
                })
                .subscribe();
        }
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
        TextAssessmentService.convertFeedbackConflictsFromServer(result.feedbacks);
    }
}
