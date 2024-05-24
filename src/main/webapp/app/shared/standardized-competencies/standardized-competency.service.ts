import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { KnowledgeAreaDTO, Source, StandardizedCompetency } from 'app/entities/competency/standardized-competency.model';

@Injectable({
    providedIn: 'root',
})
export class StandardizedCompetencyService {
    private resourceURL = 'api/standardized-competencies';

    constructor(private httpClient: HttpClient) {}

    getStandardizedCompetency(competencyId: number) {
        return this.httpClient.get<StandardizedCompetency>(`${this.resourceURL}/${competencyId}`, { observe: 'response' });
    }

    getAllForTreeView() {
        return this.httpClient.get<KnowledgeAreaDTO[]>(`${this.resourceURL}/for-tree-view`, { observe: 'response' });
    }

    getKnowledgeArea(knowledgeAreaId: number) {
        return this.httpClient.get<StandardizedCompetency>(`${this.resourceURL}/knowledge-areas/${knowledgeAreaId}`, { observe: 'response' });
    }

    getSources() {
        return this.httpClient.get<Source[]>(`${this.resourceURL}/sources`, { observe: 'response' });
    }
}
