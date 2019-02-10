import {Injectable} from '@angular/core';
import {HttpClient, HttpResponse} from '@angular/common/http';
import {Observable} from 'rxjs/Observable';
import {SERVER_API_URL} from '../../app.constants';

import {ModelElementType, ModelingAssessment} from './modeling-assessment.model';
import {Result} from '../result';
import {
    ENTITY_KIND_HEIGHT,
    ENTITY_MEMBER_HEIGHT,
    ENTITY_MEMBER_LIST_VERTICAL_PADDING,
    ENTITY_NAME_HEIGHT,
    Point,
    EntityKind,
    RelationshipKind,
    State,
    RectEdge
} from '@ls1intum/apollon';

export type EntityResponseType = HttpResponse<Result>;

@Injectable({providedIn: 'root'})
export class ModelingAssessmentService {
    private resourceUrl = SERVER_API_URL + 'api/modeling-assessments';

    constructor(private http: HttpClient) {
    }

    save(modelingAssessment: ModelingAssessment[], exerciseId: number, resultId: number): Observable<EntityResponseType> {
        return this.http
            .put<Result>(`${this.resourceUrl}/exercise/${exerciseId}/result/${resultId}`, modelingAssessment, {observe: 'response'})
            .map((res: EntityResponseType) => this.convertResponse(res));
    }

    submit(modelingAssessment: ModelingAssessment[], exerciseId: number, resultId: number, ignoreConflict: boolean = false): Observable<any> {
        let url = `${this.resourceUrl}/exercise/${exerciseId}/result/${resultId}/submit`;
        if (ignoreConflict) {
            url += '?ignoreConflict=true';
        }
        return this.http.put<any>(url, modelingAssessment);
    }

    find(participationId: number, submissionId: number): Observable<HttpResponse<ModelingAssessment[]>> {
        return this.http
            .get<ModelingAssessment[]>(`${this.resourceUrl}/participation/${participationId}/submission/${submissionId}`, {
                observe: 'response'
            })
            .map(res => this.convertArrayResponse(res));
    }

    getOptimalSubmissions(exerciseId: number): Observable<HttpResponse<any>> {
        return this.http.get(`${this.resourceUrl}/exercise/${exerciseId}/optimal-model-submissions`, {observe: 'response'});
    }

    getPartialAssessment(exerciseId: number, submissionId: number): Observable<HttpResponse<ModelingAssessment[]>> {
        return this.http
            .get<ModelingAssessment[]>(`${this.resourceUrl}/exercise/${exerciseId}/submission/${submissionId}/partial-assessment`, {
                observe: 'response'
            })
            .map(res => this.convertArrayResponse(res));
    }

    getDataForEditor(exerciseId: number, submissionId: number): Observable<any> {
        return this.http.get(`api/assessment-editor/${exerciseId}/${submissionId}`, {responseType: 'json'});
    }

    resetOptimality(exerciseId: number): Observable<HttpResponse<void>> {
        return this.http.delete<void>(`${this.resourceUrl}/exercise/${exerciseId}/optimal-model-submissions`, {observe: 'response'});
    }

    private convertResponse(res: EntityResponseType): EntityResponseType {
        const body: Result = this.convertItemFromServer(res.body);
        return res.clone({body});
    }

    /**
     * Convert a returned JSON object to Result.
     */
    private convertItemFromServer(result: Result): Result {
        const copy: Result = Object.assign({}, result);
        return copy;
    }

    private convertAssessmentFromServer(assessment: ModelingAssessment): ModelingAssessment {
        const copy: ModelingAssessment = Object.assign({}, assessment);
        return copy;
    }

    private convertArrayResponse(res: HttpResponse<ModelingAssessment[]>): HttpResponse<ModelingAssessment[]> {
        const jsonResponse: ModelingAssessment[] = res.body;
        const body: ModelingAssessment[] = [];
        if (jsonResponse) {
            for (let i = 0; i < jsonResponse.length; i++) {
                body.push(this.convertAssessmentFromServer(jsonResponse[i]));
            }
        }
        return res.clone({body});
    }


    /**
     * Creates the labels for the assessment elements for displaying them in the modeling and assessment editor.
     */
    getNamesForAssessments(assessments: ModelingAssessment[], model: State): Map<string, Map<string, string>> {
        const assessmentsNames = new Map<string, Map<string, string>>();
        for (const assessment of assessments) {
            if (assessment.type === ModelElementType.CLASS) {
                const classElement = model.entities.byId[assessment.id];
                const className = classElement.name;
                let type: string;
                switch (classElement.kind) {
                    case EntityKind.ActivityControlInitialNode:
                        type = 'initial node';
                        break;
                    case EntityKind.ActivityControlFinalNode:
                        type = 'final node';
                        break;
                    case EntityKind.ActivityObject:
                        type = 'object';
                        break;
                    case EntityKind.ActivityActionNode:
                        type = 'action';
                        break;
                    case EntityKind.ActivityForkNode:
                    case EntityKind.ActivityForkNodeHorizontal:
                        type = 'fork node';
                        break;
                    case EntityKind.ActivityMergeNode:
                        type = 'merge node';
                        break;
                    default:
                        type = assessment.type;
                        break;
                }
                assessmentsNames[assessment.id] = {type, name: className};
            } else if (assessment.type === ModelElementType.ATTRIBUTE) {
                for (const entityId of model.entities.allIds) {
                    for (const att of model.entities.byId[entityId].attributes) {
                        if (att.id === assessment.id) {
                            assessmentsNames[assessment.id] = {type: assessment.type, name: att.name};
                        }
                    }
                }
            } else if (assessment.type === ModelElementType.METHOD) {
                for (const entityId of model.entities.allIds) {
                    for (const method of model.entities.byId[entityId].methods) {
                        if (method.id === assessment.id) {
                            assessmentsNames[assessment.id] = {type: assessment.type, name: method.name};
                        }
                    }
                }
            } else if (assessment.type === ModelElementType.RELATIONSHIP) {
                const relationship = model.relationships.byId[assessment.id];
                const source = model.entities.byId[relationship.source.entityId].name;
                const target = model.entities.byId[relationship.target.entityId].name;
                const kind: RelationshipKind = model.relationships.byId[assessment.id].kind;
                let type = 'association';
                let relation: string;
                switch (kind) {
                    case RelationshipKind.AssociationBidirectional:
                        relation = ' <-> ';
                        break;
                    case RelationshipKind.AssociationUnidirectional:
                        relation = ' --> ';
                        break;
                    case RelationshipKind.Aggregation:
                        relation = ' --◇ ';
                        break;
                    case RelationshipKind.Inheritance:
                        relation = ' --▷ ';
                        break;
                    case RelationshipKind.Dependency:
                        relation = ' ╌╌> ';
                        break;
                    case RelationshipKind.Composition:
                        relation = ' --◆ ';
                        break;
                    case RelationshipKind.ActivityControlFlow:
                        relation = ' --> ';
                        type = 'control flow';
                        break;
                    default:
                        relation = ' --- ';
                }
                assessmentsNames[assessment.id] = {type, name: source + relation + target};
            } else {
                assessmentsNames[assessment.id] = {type: assessment.type, name: ''};
            }
        }
        return assessmentsNames;
    }

    /**
     * Calculates the positions for the symbols used for visualizing the scores of the assessment.
     * For associations the symbol is positioned at the source entity of the association.
     */
    getElementPositions(assessments: ModelingAssessment[], model: State): Map<string, Point> {
        const SYMBOL_HEIGHT = 31;
        const SYMBOL_WIDTH = 65;
        const positions = new Map<string, Point>();
        for (const assessment of assessments) {
            const elemPosition: Point = {x: 0, y: 0};
            if (assessment.type === ModelElementType.CLASS) {
                if (model.entities.byId[assessment.id]) {
                    const entity = model.entities.byId[assessment.id];
                    elemPosition.x = entity.position.x + entity.size.width;
                    if (entity.kind === EntityKind.ActivityControlInitialNode || entity.kind === EntityKind.ActivityControlFinalNode) {
                        elemPosition.x = entity.position.x;
                    }
                    elemPosition.y = entity.position.y;
                }
            } else if (assessment.type === ModelElementType.ATTRIBUTE) {
                for (const entityId of model.entities.allIds) {
                    const entity = model.entities.byId[entityId];
                    entity.attributes.forEach((attribute, index) => {
                        if (attribute.id === assessment.id) {
                            elemPosition.x = entity.position.x + entity.size.width;
                            elemPosition.y = entity.position.y + ENTITY_NAME_HEIGHT + ENTITY_MEMBER_LIST_VERTICAL_PADDING;
                            if (entity.kind === EntityKind.Interface || entity.kind === EntityKind.Enumeration) {
                                elemPosition.y += ENTITY_KIND_HEIGHT;
                            }
                            if (entity.attributes.length > 1 && index > 0) {
                                elemPosition.y += index * ENTITY_MEMBER_HEIGHT;
                            }
                        }
                    });
                }
            } else if (assessment.type === ModelElementType.METHOD) {
                for (const entityId of model.entities.allIds) {
                    const entity = model.entities.byId[entityId];
                    entity.methods.forEach((method, index) => {
                        if (method.id === assessment.id) {
                            elemPosition.x = entity.position.x + entity.size.width;
                            elemPosition.y = entity.position.y + ENTITY_NAME_HEIGHT + ENTITY_MEMBER_LIST_VERTICAL_PADDING;
                            if (entity.kind === EntityKind.Interface || entity.kind === EntityKind.Enumeration) {
                                elemPosition.y += ENTITY_KIND_HEIGHT;
                            }
                            if (entity.attributes.length > 0) {
                                elemPosition.y += 2 * ENTITY_MEMBER_LIST_VERTICAL_PADDING + entity.attributes.length * ENTITY_MEMBER_HEIGHT;
                            }
                            if (entity.methods.length > 1 && index > 0) {
                                elemPosition.y += index * ENTITY_MEMBER_HEIGHT;
                            }
                        }
                    });
                }
            } else if (assessment.type === ModelElementType.RELATIONSHIP) {
                if (model.relationships.byId[assessment.id]) {
                    const relationship = model.relationships.byId[assessment.id];
                    const kind: RelationshipKind = relationship.kind;
                    const sourceEntity = model.entities.byId[relationship.source.entityId];
                    const destEntity = model.entities.byId[relationship.target.entityId];
                    if (kind === RelationshipKind.AssociationBidirectional) {
                        const rightElem = sourceEntity.position.x > destEntity.position.x ? sourceEntity : destEntity;
                        const rightEdge: RectEdge = rightElem === sourceEntity ? relationship.source.edge : relationship.target.edge;
                        elemPosition.x = rightElem.position.x;
                        elemPosition.y = rightElem.position.y;
                        switch (rightEdge) {
                            case 'TOP':
                                elemPosition.x += rightElem.size.width / 2;
                                elemPosition.y -= SYMBOL_HEIGHT;
                                break;
                            case 'BOTTOM':
                                elemPosition.x += rightElem.size.width / 2;
                                elemPosition.y += rightElem.size.height;
                                break;
                            case 'LEFT':
                                elemPosition.y += rightElem.size.height / 2;
                                break;
                            case 'RIGHT':
                                elemPosition.x += rightElem.size.width + SYMBOL_WIDTH;
                                elemPosition.y += rightElem.size.height / 2;
                                break;
                            default:
                                break;
                        }
                    } else {
                        elemPosition.x = sourceEntity.position.x;
                        elemPosition.y = sourceEntity.position.y;
                        const sourceEdge: RectEdge = relationship.source.edge;
                        switch (sourceEdge) {
                            case 'TOP':
                                elemPosition.x += sourceEntity.size.width / 2;
                                elemPosition.y -= SYMBOL_HEIGHT;
                                break;
                            case 'BOTTOM':
                                elemPosition.x += sourceEntity.size.width / 2;
                                elemPosition.y += sourceEntity.size.height;
                                break;
                            case 'LEFT':
                                elemPosition.y += sourceEntity.size.height / 2;
                                break;
                            case 'RIGHT':
                                elemPosition.x += sourceEntity.size.width + SYMBOL_WIDTH;
                                elemPosition.y += sourceEntity.size.height / 2;
                                break;
                            default:
                                break;
                        }
                    }
                }
            }
            positions[assessment.id] = elemPosition;
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
