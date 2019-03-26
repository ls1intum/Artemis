import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs/Observable';
import { SERVER_API_URL } from 'app/app.constants';
import { Result } from '../entities/result';
import { UMLModel, ElementType, UMLElementType, UMLRelationshipType, UMLClassifier } from '@ls1intum/apollon';
import { Feedback } from 'app/entities/feedback';

export type EntityResponseType = HttpResponse<Result>;

@Injectable({providedIn: 'root'})
export class ModelingAssessmentService {
    private resourceUrl = SERVER_API_URL + 'api';

    constructor(private http: HttpClient) {
    }

    save(feedbacks: Feedback[], submissionId: number, submit = false, ignoreConflicts = false): Observable<Result> {
        let url = `${this.resourceUrl}/modeling-submissions/${submissionId}/feedback`;
        if (submit) {
            url += '?submit=true';
            if (ignoreConflicts) {
                url += '&ignoreConflicts=true';
            }
        }
        return this.http
            .put<Result>(url, feedbacks)
            .map( res => this.convertResult(res));
    }

    getAssessment(submissionId: number): Observable<Result> {
        return this.http
            .get<Result>(`${this.resourceUrl}/modeling-submissions/${submissionId}/result`)
            .map( res => this.convertResult(res));
    }

    getOptimalSubmissions(exerciseId: number): Observable<HttpResponse<any>> {
        return this.http
            .get(`${this.resourceUrl}/exercises/${exerciseId}/optimal-model-submissions`, {observe: 'response'});
    }

    getPartialAssessment(submissionId: number): Observable<Result> {
        return this.http
            .get<Result>(`${this.resourceUrl}/modeling-submissions/${submissionId}/partial-assessment`)
            .map( res => this.convertResult(res));
    }

    resetOptimality(exerciseId: number): Observable<HttpResponse<void>> {
        return this.http
            .delete<void>(`${this.resourceUrl}/exercises/${exerciseId}/optimal-model-submissions`, {observe: 'response'});
    }

    /**
     * Iterates over all feedback elements of a response and converts the reference field of the feedback into
     * separate referenceType and referenceId fields. The reference field is of the form <referenceType>:<referenceId>.
     */
    convertResult(result: Result): Result {
        for (const feedback of result.feedbacks) {
            feedback.referenceType = feedback.reference.split(':')[0] as ElementType;
            feedback.referenceId = feedback.reference.split(':')[1];
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
            const referencedModelType = feedback.referenceType;
            const referencedModelId = feedback.referenceId;
            if (referencedModelType in UMLElementType) {
                const element = model.elements.find(elem => elem.id === referencedModelId);
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
                assessmentsNames[referencedModelId] = {type, name};
            } else if (referencedModelType in UMLRelationshipType) {
                const relationship = model.relationships.find(rel => rel.id === referencedModelId);
                const source = model.elements.find(element => element.id === relationship.source.element).name;
                const target = model.elements.find(element => element.id === relationship.target.element).name;
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
                assessmentsNames[referencedModelId] = {type, name: source + relation + target};
            } else {
                assessmentsNames[referencedModelId] = {type: referencedModelType, name: ''};
            }
        }
        return assessmentsNames;
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
