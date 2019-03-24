import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs/Observable';
import { SERVER_API_URL } from '../../app.constants';
import { Result } from '../result';
import { UMLModel, ElementType, RelationshipType, UMLClassifier } from '@ls1intum/apollon';
import { ModelElementType } from 'app/entities/modeling-assessment/uml-element.model';
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
            feedback.referenceType = feedback.reference.split(':')[0] as ModelElementType;
            feedback.referenceId = feedback.reference.split(':')[1];
        }
        return result;
    }

    /**
     * Creates the labels for the assessment elements for displaying them in the modeling and assessment editor.
     */
    getNamesForAssessments(result: Result, model: UMLModel): Map<string, Map<string, string>> {
        const assessmentsNames = new Map<string, Map<string, string>>();
        for (const feedback of result.feedbacks) {
            const referencedModelType = feedback.referenceType;
            const referencedModelId = feedback.referenceId;
            if (referencedModelType === ModelElementType.CLASS) {
                const classElement = model.elements[referencedModelId];
                const className = classElement.name;
                let type: string;
                switch (classElement.type) {
                    case ElementType.ActivityInitialNode:
                        type = 'initial node';
                        break;
                    case ElementType.ActivityFinalNode:
                        type = 'final node';
                        break;
                    case ElementType.ActivityObjectNode:
                        type = 'object';
                        break;
                    case ElementType.ActivityActionNode:
                        type = 'action';
                        break;
                    case ElementType.ActivityForkNode:
                        type = 'fork node';
                        break;
                    case ElementType.ActivityMergeNode:
                        type = 'merge node';
                        break;
                    default:
                        type = referencedModelType;
                        break;
                }
                assessmentsNames[referencedModelId] = {type, name: className};
            } else if (referencedModelType === ModelElementType.ATTRIBUTE) {
                for (const elem of Object.values(model.elements)) {
                    if (
                        ([
                            ElementType.Class,
                            ElementType.AbstractClass,
                            ElementType.Interface,
                            ElementType.Enumeration
                        ] as ElementType[]).includes(elem.type)
                    ) {
                        for (const att of (elem as UMLClassifier).attributes) {
                            if (att.id === referencedModelId) {
                                assessmentsNames[referencedModelId] = { type: referencedModelType, name: att.name };
                            }
                        }
                    }
                }
            } else if (referencedModelType === ModelElementType.METHOD) {
                for (const elem of Object.values(model.elements)) {
                    if (
                        ([
                            ElementType.Class,
                            ElementType.AbstractClass,
                            ElementType.Interface,
                            ElementType.Enumeration
                        ] as ElementType[]).includes(elem.type)
                    ) {
                        for (const method of (elem as UMLClassifier).methods) {
                            if (method.id === referencedModelId) {
                                assessmentsNames[referencedModelId] = { type: referencedModelType, name: method.name };
                            }
                        }
                    }
                }
            } else if (referencedModelType === ModelElementType.RELATIONSHIP) {
                const relationship = model.relationships[referencedModelId];
                const source = model.elements[relationship.source.element].name;
                const target = model.elements[relationship.target.element].name;
                const kind: RelationshipType = model.relationships[referencedModelId].type;
                let type = 'association';
                let relation: string;
                switch (kind) {
                    case RelationshipType.ClassBidirectional:
                        relation = ' <-> ';
                        break;
                    case RelationshipType.ClassUnidirectional:
                        relation = ' --> ';
                        break;
                    case RelationshipType.ClassAggregation:
                        relation = ' --◇ ';
                        break;
                    case RelationshipType.ClassInheritance:
                        relation = ' --▷ ';
                        break;
                    case RelationshipType.ClassDependency:
                        relation = ' ╌╌> ';
                        break;
                    case RelationshipType.ClassComposition:
                        relation = ' --◆ ';
                        break;
                    case RelationshipType.ActivityControlFlow:
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

    // TODO: this should be part of Apollon in the future
    /**
     * Calculates the positions for the symbols used for visualizing the scores of the assessment.
     * For associations the symbol is positioned at the source entity of the association.
     */
    getElementPositions(result: Result, model: UMLModel): Map<string, { x: number; y: number }> {
        const SYMBOL_HEIGHT = 31;
        const SYMBOL_WIDTH = 65;
        const positions = new Map<string, { x: number; y: number }>();
        for (const feedback of result.feedbacks) {
            const referencedModelType = feedback.referenceType;
            const referencedModelId = feedback.referenceId;

            const element = [...Object.values(model.elements), ...Object.values(model.relationships)]
                .find(elem => elem.id === referencedModelId);

            const elemPosition: { x: number; y: number } = { x: 0, y: 0 };
            if (element) {
                elemPosition.x = element.bounds.x;
                elemPosition.y = element.bounds.y;
            }
            //
            // if (referencedModelType === ModelElementType.CLASS) {
            //     if (model.elements.byId[referencedModelId]) {
            //         const entity = model.elements.byId[referencedModelId];
            //         elemPosition.x = entity.position.x + entity.size.width;
            //         if (entity.kind === ElementType.ActivityInitialNode || entity.kind === ElementType.ActivityFinalNode) {
            //             elemPosition.x = entity.position.x;
            //         }
            //         elemPosition.y = entity.position.y;
            //     }
            // } else if (referencedModelType === ModelElementType.ATTRIBUTE) {
            //     for (const element of model.elements) {
            //         element.attributes.forEach((attribute, index) => {
            //             if (attribute.id === referencedModelId) {
            //                 elemPosition.x = element.position.x + element.size.width;
            //                 elemPosition.y = element.position.y + ENTITY_NAME_HEIGHT + ENTITY_MEMBER_LIST_VERTICAL_PADDING;
            //                 if (element.kind === ElementType.Interface || element.kind === EntityKind.Enumeration) {
            //                     elemPosition.y += ENTITY_KIND_HEIGHT;
            //                 }
            //                 if (element.attributes.length > 1 && index > 0) {
            //                     elemPosition.y += index * ENTITY_MEMBER_HEIGHT;
            //                 }
            //             }
            //         });
            //     }
            // } else if (referencedModelType === ModelElementType.METHOD) {
            //     for (const element of model.elements) {
            //         element.methods.forEach((method, index) => {
            //             if (method.id === referencedModelId) {
            //                 elemPosition.x = element.position.x + element.size.width;
            //                 elemPosition.y = element.position.y + ENTITY_NAME_HEIGHT + ENTITY_MEMBER_LIST_VERTICAL_PADDING;
            //                 if (element.kind === ElementType.Interface || entity.kind === ElementType.Enumeration) {
            //                     elemPosition.y += ENTITY_KIND_HEIGHT;
            //                 }
            //                 if (element.attributes.length > 0) {
            //                     elemPosition.y += 2 * ENTITY_MEMBER_LIST_VERTICAL_PADDING + entity.attributes.length * ENTITY_MEMBER_HEIGHT;
            //                 }
            //                 if (element.methods.length > 1 && index > 0) {
            //                     elemPosition.y += index * ENTITY_MEMBER_HEIGHT;
            //                 }
            //             }
            //         });
            //     }
            // } else if (referencedModelType === ModelElementType.RELATIONSHIP) {
            //     if (model.relationships.byId[referencedModelId]) {
            //         const relationship = model.relationships.byId[referencedModelId];
            //         const kind: RelationshipType = relationship.kind;
            //         const sourceEntity = model.elements.byId[relationship.source.entityId];
            //         const destEntity = model.elements.byId[relationship.target.entityId];
            //         if (kind === RelationshipType.ClassBidirectional) {
            //             const rightElem = sourceEntity.position.x > destEntity.position.x ? sourceEntity : destEntity;
            //             const rightEdge: RectEdge = rightElem === sourceEntity ? relationship.source.edge : relationship.target.edge;
            //             elemPosition.x = rightElem.position.x;
            //             elemPosition.y = rightElem.position.y;
            //             switch (rightEdge) {
            //                 case 'TOP':
            //                     elemPosition.x += rightElem.size.width / 2;
            //                     elemPosition.y -= SYMBOL_HEIGHT;
            //                     break;
            //                 case 'BOTTOM':
            //                     elemPosition.x += rightElem.size.width / 2;
            //                     elemPosition.y += rightElem.size.height;
            //                     break;
            //                 case 'LEFT':
            //                     elemPosition.y += rightElem.size.height / 2;
            //                     break;
            //                 case 'RIGHT':
            //                     elemPosition.x += rightElem.size.width + SYMBOL_WIDTH;
            //                     elemPosition.y += rightElem.size.height / 2;
            //                     break;
            //                 default:
            //                     break;
            //             }
            //         } else {
            //             elemPosition.x = sourceEntity.position.x;
            //             elemPosition.y = sourceEntity.position.y;
            //             const sourceEdge: RectEdge = relationship.source.edge;
            //             switch (sourceEdge) {
            //                 case 'TOP':
            //                     elemPosition.x += sourceEntity.size.width / 2;
            //                     elemPosition.y -= SYMBOL_HEIGHT;
            //                     break;
            //                 case 'BOTTOM':
            //                     elemPosition.x += sourceEntity.size.width / 2;
            //                     elemPosition.y += sourceEntity.size.height;
            //                     break;
            //                 case 'LEFT':
            //                     elemPosition.y += sourceEntity.size.height / 2;
            //                     break;
            //                 case 'RIGHT':
            //                     elemPosition.x += sourceEntity.size.width + SYMBOL_WIDTH;
            //                     elemPosition.y += sourceEntity.size.height / 2;
            //                     break;
            //                 default:
            //                     break;
            //             }
            //         }
            //     }
            // }
            positions[referencedModelId] = elemPosition;
        }

        return positions;
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
