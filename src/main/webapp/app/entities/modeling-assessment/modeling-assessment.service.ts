import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs/Observable';
import { SERVER_API_URL } from 'app/app.constants';
import { Result } from '../result';
import { UMLElementType, UMLModel, UMLModelElementType, UMLRelationshipType } from '@ls1intum/apollon';
import { Feedback } from 'app/entities/feedback';
import { mergeMap } from 'rxjs/operators';
import { timer } from 'rxjs';
import { ComplaintResponse } from 'app/entities/complaint-response';
import { Conflict } from 'app/modeling-assessment-editor/conflict.model';
import * as moment from 'moment';

export type EntityResponseType = HttpResponse<Result>;

@Injectable({ providedIn: 'root' })
export class ModelingAssessmentService {
    private readonly MAX_FEEDBACK_TEXT_LENGTH = 500;
    private readonly MAX_FEEDBACK_DETAIL_TEXT_LENGTH = 5000;
    private localSubmissionConflictMap: Map<number, Conflict[]>;
    private resourceUrl = SERVER_API_URL + 'api';

    constructor(private http: HttpClient) {
        this.localSubmissionConflictMap = new Map<number, Conflict[]>();
    }

    addLocalConflicts(submissionID: number, conflicts: Conflict[]) {
        return this.localSubmissionConflictMap.set(submissionID, conflicts);
    }

    getLocalConflicts(submissionID: number) {
        return this.localSubmissionConflictMap.get(submissionID);
    }

    escalateConflict(conflicts: Conflict[]): Observable<Conflict> {
        return this.http.put<Conflict>(`${this.resourceUrl}/model-assessment-conflicts/escalate`, conflicts);
    }

    saveAssessment(feedbacks: Feedback[], submissionId: number, submit = false, ignoreConflicts = false): Observable<Result> {
        let url = `${this.resourceUrl}/modeling-submissions/${submissionId}/feedback`;
        if (submit) {
            url += '?submit=true';
            if (ignoreConflicts) {
                url += '&ignoreConflicts=true';
            }
        }
        return this.http.put<Result>(url, feedbacks).map(res => this.convertResult(res));
    }

    saveExampleAssessment(feedbacks: Feedback[], exampleSubmissionId: number): Observable<Result> {
        const url = `${this.resourceUrl}/modeling-submissions/${exampleSubmissionId}/exampleAssessment`;
        return this.http.put<Result>(url, feedbacks).map(res => this.convertResult(res));
    }

    updateAssessmentAfterComplaint(feedbacks: Feedback[], complaintResponse: ComplaintResponse, submissionId: number): Observable<EntityResponseType> {
        const url = `${this.resourceUrl}/modeling-submissions/${submissionId}/assessment-after-complaint`;
        const assessmentUpdate = {
            feedbacks,
            complaintResponse,
        };
        return this.http
            .put<Result>(url, assessmentUpdate, { observe: 'response' })
            .map((res: EntityResponseType) => this.convertResponse(res));
    }

    getAssessment(submissionId: number): Observable<Result> {
        return this.http.get<Result>(`${this.resourceUrl}/modeling-submissions/${submissionId}/result`).map(res => this.convertResult(res));
    }

    getExampleAssessment(exerciseId: number, submissionId: number): Observable<Result> {
        const url = `${this.resourceUrl}/exercise/${exerciseId}/submission/${submissionId}/modelingExampleAssessment`;
        return this.http.get<Result>(url).map(res => this.convertResult(res));
    }

    getOptimalSubmissions(exerciseId: number): Observable<number[]> {
        return this.http.get<number[]>(`${this.resourceUrl}/exercises/${exerciseId}/optimal-model-submissions`);
    }

    getPartialAssessment(submissionId: number): Observable<Result> {
        return this.http.get<Result>(`${this.resourceUrl}/modeling-submissions/${submissionId}/partial-assessment`).map(res => this.convertResult(res));
    }

    resetOptimality(exerciseId: number): Observable<HttpResponse<void>> {
        return this.http.delete<void>(`${this.resourceUrl}/exercises/${exerciseId}/optimal-model-submissions`, { observe: 'response' });
    }

    cancelAssessment(submissionId: number): Observable<void> {
        return this.http.put<void>(`${this.resourceUrl}/modeling-submissions/${submissionId}/cancel-assessment`, null);
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
                feedback.referenceType = feedback.reference.split(':')[0] as UMLModelElementType;
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
        for (const feedback of result.feedbacks) {
            const referencedModelType = feedback.referenceType!;
            const referencedModelId = feedback.referenceId!;
            if (referencedModelType in UMLElementType) {
                const element = model.elements.find(elem => elem.id === referencedModelId);
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
                const relationship = model.relationships.find(rel => rel.id === referencedModelId)!;
                const source = model.elements.find(element => element.id === relationship.source.element)!.name;
                const target = model.elements.find(element => element.id === relationship.target.element)!.name;
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
            feedbackItem =>
                (!feedbackItem.text || feedbackItem.text.length <= this.MAX_FEEDBACK_TEXT_LENGTH) &&
                (!feedbackItem.detailText || feedbackItem.detailText.length <= this.MAX_FEEDBACK_DETAIL_TEXT_LENGTH),
        );
    }

    /**
     * Creates an array filled with n integers starting from the number provided by startFrom.
     * Example: numberToArray(5, 0) returns the array
     * [0, 1, 2, 3, 4]
     */
    numberToArray(n: number, startFrom: number): number[] {
        n = Math.floor(Math.abs(n));
        return [...Array(n).keys()].map(i => i + startFrom);
    }
}

export const genericRetryStrategy = ({
    maxRetryAttempts = 3,
    scalingDuration = 1000,
    excludedStatusCodes = [],
}: {
    maxRetryAttempts?: number;
    scalingDuration?: number;
    excludedStatusCodes?: number[];
} = {}) => (attempts: Observable<any>) => {
    return attempts.pipe(
        mergeMap((error, i) => {
            const retryAttempt = i + 1;
            // if maximum number of retries have been met
            // or response is a status code we don't wish to retry, throw error
            if (retryAttempt > maxRetryAttempts || excludedStatusCodes.find(e => e === error.status)) {
                throw error;
            }
            // retry after 1s, 2s, etc...
            return timer(retryAttempt * scalingDuration);
        }),
    );
};
