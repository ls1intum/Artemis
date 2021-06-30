import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { SERVER_API_URL } from 'app/app.constants';
import { UMLElementType, UMLModel, UMLRelationshipType } from '@ls1intum/apollon';
import * as moment from 'moment';
import { ComplaintResponse } from 'app/entities/complaint-response.model';
import { Feedback } from 'app/entities/feedback.model';
import { Result } from 'app/entities/result.model';
import { map } from 'rxjs/operators';

export type EntityResponseType = HttpResponse<Result>;

@Injectable({ providedIn: 'root' })
export class ModelingAssessmentService {
    private readonly MAX_FEEDBACK_TEXT_LENGTH = 500;
    private readonly MAX_FEEDBACK_DETAIL_TEXT_LENGTH = 5000;
    private resourceUrl = SERVER_API_URL + 'api';

    constructor(private http: HttpClient) {}

    saveAssessment(resultId: number, feedbacks: Feedback[], submissionId: number, submit = false): Observable<Result> {
        let params = new HttpParams();
        if (submit) {
            params = params.set('submit', 'true');
        }
        const url = `${this.resourceUrl}/modeling-submissions/${submissionId}/result/${resultId}/assessment`;
        return this.http.put<Result>(url, feedbacks, { params }).pipe(map((res: Result) => this.convertResult(res)));
    }

    saveExampleAssessment(feedbacks: Feedback[], exampleSubmissionId: number): Observable<Result> {
        const url = `${this.resourceUrl}/modeling-submissions/${exampleSubmissionId}/example-assessment`;
        return this.http.put<Result>(url, feedbacks).pipe(map((res) => this.convertResult(res)));
    }

    updateAssessmentAfterComplaint(feedbacks: Feedback[], complaintResponse: ComplaintResponse, submissionId: number): Observable<EntityResponseType> {
        const url = `${this.resourceUrl}/modeling-submissions/${submissionId}/assessment-after-complaint`;
        const assessmentUpdate = {
            feedbacks,
            complaintResponse,
        };
        return this.http.put<Result>(url, assessmentUpdate, { observe: 'response' }).pipe(map((res: EntityResponseType) => this.convertResponse(res)));
    }

    getAssessment(submissionId: number): Observable<Result> {
        return this.http.get<Result>(`${this.resourceUrl}/modeling-submissions/${submissionId}/result`).pipe(map((res) => this.convertResult(res)));
    }

    getExampleAssessment(exerciseId: number, submissionId: number): Observable<Result> {
        const url = `${this.resourceUrl}/exercise/${exerciseId}/modeling-submissions/${submissionId}/example-assessment`;
        return this.http.get<Result>(url).pipe(map((res) => this.convertResult(res)));
    }

    /**
     * TODO delete
     * @deprecated do not use any more, instead use modeling-submission-service.getModelingSubmissionForExerciseForCorrectionRoundWithoutAssessment(...)
     * @param exerciseId the modeling exercise id
     */
    getOptimalSubmissions(exerciseId: number): Observable<number[]> {
        return this.http.get<number[]>(`${this.resourceUrl}/exercises/${exerciseId}/optimal-model-submissions`);
    }

    /**
     * TODO delete
     * @deprecated do not use any more
     * @param exerciseId the modeling exercise id
     */
    resetOptimality(exerciseId: number): Observable<HttpResponse<void>> {
        return this.http.delete<void>(`${this.resourceUrl}/exercises/${exerciseId}/optimal-model-submissions`, { observe: 'response' });
    }

    cancelAssessment(submissionId: number): Observable<void> {
        return this.http.put<void>(`${this.resourceUrl}/modeling-submissions/${submissionId}/cancel-assessment`, null);
    }

    /**
     * Deletes an assessment
     * @param participationId id of the participation, to which the assessment and the submission belong to
     * @param submissionId id of the submission, to which the assessment belongs to
     * @param resultId     id of the result which is deleted
     */
    deleteAssessment(participationId: number, submissionId: number, resultId: number): Observable<void> {
        return this.http.delete<void>(`${this.resourceUrl}/participations/${participationId}/modeling-submissions/${submissionId}/results/${resultId}`);
    }

    private convertResponse(res: EntityResponseType): EntityResponseType {
        let result = this.convertItemFromServer(res.body!);
        result = this.convertResult(result);

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

    /**
     * Iterates over all feedback elements of a response and converts the reference field of the feedback into
     * separate referenceType and referenceId fields. The reference field is of the form <referenceType>:<referenceId>.
     */
    convertResult(result: Result): Result {
        if (!result || !result.feedbacks) {
            return result;
        }
        for (const feedback of result.feedbacks) {
            if (feedback.reference) {
                feedback.referenceType = feedback.reference.split(':')[0];
                feedback.referenceId = feedback.reference.split(':')[1];
            }
        }
        return result;
    }

    /**
     * Creates the labels for the assessment elements for displaying them in the modeling and assessment editor.
     */
    // TODO: define a mapping or simplify this complex monster in a another way so that we can support other diagram types as well
    getNamesForAssessments(result: Result, model: UMLModel): Map<string, Map<string, string>> {
        const assessmentsNames = new Map<string, Map<string, string>>();
        for (const feedback of result.feedbacks!) {
            const referencedModelType = feedback.referenceType! as UMLElementType;
            const referencedModelId = feedback.referenceId!;
            if (referencedModelType in UMLElementType) {
                const element = model.elements.find((elem) => elem.id === referencedModelId);
                if (!element) {
                    // prevent errors when element could not be found, should never happen
                    assessmentsNames[referencedModelId] = { name: '', type: '' };
                    continue;
                }

                const name = element.name;
                let type: string;
                switch (element.type) {
                    case UMLElementType.Class:
                        type = 'class';
                        break;
                    case UMLElementType.Package:
                        type = 'package';
                        break;
                    case UMLElementType.Interface:
                        type = 'interface';
                        break;
                    case UMLElementType.AbstractClass:
                        type = 'abstract class';
                        break;
                    case UMLElementType.Enumeration:
                        type = 'enum';
                        break;
                    case UMLElementType.ClassAttribute:
                        type = 'attribute';
                        break;
                    case UMLElementType.ClassMethod:
                        type = 'method';
                        break;
                    case UMLElementType.ActivityInitialNode:
                        type = 'initial node';
                        break;
                    case UMLElementType.ActivityFinalNode:
                        type = 'final node';
                        break;
                    case UMLElementType.ActivityObjectNode:
                        type = 'object';
                        break;
                    case UMLElementType.ActivityActionNode:
                        type = 'action';
                        break;
                    case UMLElementType.ActivityForkNode:
                        type = 'fork node';
                        break;
                    case UMLElementType.ActivityMergeNode:
                        type = 'merge node';
                        break;
                    default:
                        type = '';
                        break;
                }
                assessmentsNames[referencedModelId] = { type, name };
            } else if (referencedModelType in UMLRelationshipType) {
                const relationship = model.relationships.find((rel) => rel.id === referencedModelId)!;
                const source = model.elements.find((element) => element.id === relationship.source.element)!.name;
                const target = model.elements.find((element) => element.id === relationship.target.element)!.name;
                const relationshipType = relationship.type;
                let type = 'association';
                let relation: string;
                switch (relationshipType) {
                    case UMLRelationshipType.ClassBidirectional:
                        relation = ' <-> ';
                        break;
                    case UMLRelationshipType.ClassUnidirectional:
                        relation = ' --> ';
                        break;
                    case UMLRelationshipType.ClassAggregation:
                        relation = ' --◇ ';
                        break;
                    case UMLRelationshipType.ClassInheritance:
                        relation = ' --▷ ';
                        break;
                    case UMLRelationshipType.ClassDependency:
                        relation = ' ╌╌> ';
                        break;
                    case UMLRelationshipType.ClassComposition:
                        relation = ' --◆ ';
                        break;
                    case UMLRelationshipType.ActivityControlFlow:
                        relation = ' --> ';
                        type = 'control flow';
                        break;
                    default:
                        relation = ' --- ';
                }
                assessmentsNames[referencedModelId] = { type, name: source + relation + target };
            } else {
                assessmentsNames[referencedModelId] = { type: referencedModelType, name: '' };
            }
        }
        return assessmentsNames;
    }

    /**
     * Checks if the feedback text and detail text of every feedback item is smaller than the configured maximum length. Returns true if the length of the texts is valid or if
     * there is no feedback, false otherwise.
     */
    isFeedbackTextValid(feedback: Feedback[]): boolean {
        if (!feedback) {
            return true;
        }
        return feedback.every(
            (feedbackItem) =>
                (!feedbackItem.text || feedbackItem.text.length <= this.MAX_FEEDBACK_TEXT_LENGTH) &&
                (!feedbackItem.detailText || feedbackItem.detailText.length <= this.MAX_FEEDBACK_DETAIL_TEXT_LENGTH),
        );
    }
}
