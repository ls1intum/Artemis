import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs/Observable';
import { SERVER_API_URL } from '../../app.constants';

import { ModelingAssessment } from './modeling-assessment.model';
import { Result } from '../result';
import {
    ENTITY_KIND_HEIGHT, ENTITY_MEMBER_HEIGHT,
    ENTITY_MEMBER_LIST_VERTICAL_PADDING, ENTITY_NAME_HEIGHT
} from '@ls1intum/apollon/dist/rendering/layouters/entity';

export type EntityResponseType = HttpResponse<Result>;

@Injectable()
export class ModelingAssessmentService {
    private resourceUrl =  SERVER_API_URL + 'api/modeling-assessments';

    constructor(private http: HttpClient) { }

    save(modelingAssessment: ModelingAssessment[], exerciseId: number, resultId: number): Observable<EntityResponseType> {
        const copy = this.convert(modelingAssessment);
        return this.http.put<Result>(`${this.resourceUrl}/exercise/${exerciseId}/result/${resultId}`, copy, { observe: 'response' })
            .map((res: EntityResponseType) => this.convertResponse(res));
    }

    submit(modelingAssessment: ModelingAssessment[], exerciseId: number, resultId: number): Observable<EntityResponseType> {
        const copy = this.convert(modelingAssessment);
        return this.http.put<Result>(`${this.resourceUrl}/exercise/${exerciseId}/result/${resultId}/submit`, copy, { observe: 'response' })
            .map((res: EntityResponseType) => this.convertResponse(res));
    }

    find(participationId: number, submissionId: number): Observable<HttpResponse<ModelingAssessment[]>> {
        return this.http.get<ModelingAssessment[]>(`${this.resourceUrl}/participation/${participationId}/submission/${submissionId}`, {observe: 'response'})
            .map(res => this.convertArrayResponse(res));
    }

    getOptimalSubmissions(exerciseId: number): Observable<HttpResponse<any>> {
        return this.http.get(`${this.resourceUrl}/exercise/${exerciseId}/optimal-models`, {observe: 'response'});
    }

    getPartialAssessment(exerciseId: number, submissionId: number): Observable<HttpResponse<ModelingAssessment[]>> {
        return this.http.get<ModelingAssessment[]>(`${this.resourceUrl}/exercise/${exerciseId}/submission/${submissionId}/partial-assessment`, {observe: 'response'})
            .map(res => this.convertArrayResponse(res));
    }

    getDataForEditor(exerciseId: number, submissionId: number): Observable<any> {
        return this.http.get(`api/assessment-editor/${exerciseId}/${submissionId}`, {responseType: 'json'});
    }

    resetOptimality(exerciseId: number): Observable<HttpResponse<any>> {
        return this.http.delete(`${this.resourceUrl}/exercise/${exerciseId}/optimal-models`, {observe: 'response'});
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
     * Convert the assessment to a String which can be sent to the server.
     */
    private convert(modelingAssessment: ModelingAssessment[]): String {
        const copy: String = JSON.stringify({'assessments': modelingAssessment});
        return copy;
    }

    getNamesForAssessments(assessments, model) {
        const assessmentsNames = [];
        for (const assessment of assessments) {
            if (assessment.type === 'class') {
                assessmentsNames[assessment.id] = model.entities.byId[assessment.id].name;
            } else if (assessment.type === 'attribute') {
                for (const entityId of model.entities.allIds) {
                    for (const att of model.entities.byId[entityId].attributes) {
                        if (att.id === assessment.id) {
                            assessmentsNames[assessment.id] = att.name;
                        }
                    }
                }
            } else if (assessment.type === 'method') {
                for (const entityId of model.entities.allIds) {
                    for (const method of model.entities.byId[entityId].methods) {
                        if (method.id === assessment.id) {
                            assessmentsNames[assessment.id] = method.name;
                        }
                    }
                }
            } else if (assessment.type === 'relationship') {
                const relationship = model.relationships.byId[assessment.id];
                const source = model.entities.byId[relationship.source.entityId].name;
                const target = model.entities.byId[relationship.target.entityId].name;
                const kind = model.relationships.byId[assessment.id].kind;
                let relation;
                switch (kind) {
                    case 'ASSOCIATION_BIDIRECTIONAL':
                        relation = ' <-> ';
                        break;
                    case 'ASSOCIATION_UNIDIRECTIONAL':
                        relation = ' -> ';
                        break;
                    case 'AGGREGATION':
                        relation = ' -◇ ';
                        break;
                    case 'INHERITANCE':
                        relation = ' -▷ ';
                        break;
                    case 'DEPENDENCY':
                        relation = ' ╌> ';
                        break;
                    case 'COMPOSITION':
                        relation = ' -◆ ';
                        break;
                    default:
                        relation = '/';
                }
                assessmentsNames[assessment.id] = source + relation + target;
            } else {
                assessmentsNames[assessment.id] = '';
            }
        }
        return assessmentsNames;
    }

    getElementPositions(assessments, state) {
        const SYMBOL_HEIGHT = 31;
        const SYMBOL_WIDTH = 65;
        const positions = [];
        for (const assessment of assessments) {
            const elemPosition = {x: 0, y: 0};
            if (assessment.type === 'class') {
                if (state.entities.byId[assessment.id]) {
                    const entity = state.entities.byId[assessment.id];
                    elemPosition.x = entity.position.x + entity.size.width;
                    elemPosition.y = entity.position.y;
                }
            } else if (assessment.type === 'attribute') {
                for (const entityId of state.entities.allIds) {
                    const entity = state.entities.byId[entityId];
                    entity.attributes.forEach((attribute, index) => {
                        if (attribute.id === assessment.id) {
                            elemPosition.x = entity.position.x + entity.size.width;
                            elemPosition.y = entity.position.y + ENTITY_NAME_HEIGHT + ENTITY_MEMBER_LIST_VERTICAL_PADDING;
                            if (entity.kind === 'INTERFACE' || entity.kind === 'ENUMERATION') {
                                elemPosition.y += ENTITY_KIND_HEIGHT;
                            }
                            if (entity.attributes.length > 1 && index > 0) {
                                elemPosition.y += index * ENTITY_MEMBER_HEIGHT;
                            }
                        }
                    });
                }
            } else if (assessment.type === 'method') {
                for (const entityId of state.entities.allIds) {
                    const entity = state.entities.byId[entityId];
                    entity.methods.forEach((method, index) => {
                        if (method.id === assessment.id) {
                            elemPosition.x = entity.position.x + entity.size.width;
                            elemPosition.y = entity.position.y + ENTITY_NAME_HEIGHT + ENTITY_MEMBER_LIST_VERTICAL_PADDING;
                            if (entity.kind === 'INTERFACE' || entity.kind === 'ENUMERATION') {
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
            } else if (assessment.type === 'relationship') {
                if (state.relationships.byId[assessment.id]) {
                    const relationship = state.relationships.byId[assessment.id];
                    const kind = relationship.kind;
                    const sourceEntity = state.entities.byId[relationship.source.entityId];
                    const destEntity = state.entities.byId[relationship.target.entityId];
                    if (kind === 'BIDIRECTIONAL') {
                        const leftElem = (sourceEntity.position.x < destEntity.position.x) ? sourceEntity : destEntity;
                        const rightElem = (sourceEntity.position.x > destEntity.position.x) ? sourceEntity : destEntity;
                        const rightEdge = (rightElem === sourceEntity) ? relationship.source.edge : relationship.target.edge;
                        elemPosition.x = rightElem.position.x;
                        elemPosition.y = rightElem.position.y;
                        if (rightEdge === 'TOP') {
                            elemPosition.x += rightElem.size.width / 2;
                            elemPosition.y -= SYMBOL_HEIGHT;
                        } else if (rightEdge === 'BOTTOM') {
                            elemPosition.x += rightElem.size.width / 2;
                            elemPosition.y += rightElem.size.height;
                        } else if (rightEdge === 'LEFT') {
                            elemPosition.y += rightElem.size.height / 2;
                        } else if (rightEdge === 'RIGHT') {
                            elemPosition.x += rightElem.size.width + SYMBOL_WIDTH;
                            elemPosition.y += rightElem.size.height / 2;
                        }
                    } else {
                        elemPosition.x = sourceEntity.position.x;
                        elemPosition.y = sourceEntity.position.y;
                        const sourceEdge = relationship.source.edge;
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

    numberToArray(n: number, startFrom: number): number[] {
        n = Math.floor(Math.abs(n));
        return [...Array(n).keys()].map(i => i + startFrom);
    }
}
