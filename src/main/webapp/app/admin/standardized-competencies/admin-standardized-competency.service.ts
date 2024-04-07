import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { KnowledgeArea, StandardizedCompetency } from 'app/entities/competency/standardized-competency.model';

@Injectable({
    providedIn: 'root',
})
export class AdminStandardizedCompetencyService {
    private resourceURL = 'api/admin/standardized-competencies';

    constructor(private httpClient: HttpClient) {}

    createStandardizedCompetency(competency: StandardizedCompetency) {
        return this.httpClient.post<StandardizedCompetency>(`${this.resourceURL}`, competency, { observe: 'response' });
    }

    updateStandardizedCompetency(competency: StandardizedCompetency) {
        return this.httpClient.put<StandardizedCompetency>(`${this.resourceURL}/${competency.id}`, competency, { observe: 'response' });
    }

    deleteStandardizedCompetency(competencyId: number) {
        return this.httpClient.delete<void>(`${this.resourceURL}/${competencyId}`, { observe: 'response' });
    }

    createKnowledgeArea(knowledgeArea: KnowledgeArea) {
        return this.httpClient.post<KnowledgeArea>(`${this.resourceURL}/knowledge-areas`, knowledgeArea, { observe: 'response' });
    }

    updateKnowledgeArea(knowledgeArea: KnowledgeArea) {
        return this.httpClient.put<KnowledgeArea>(`${this.resourceURL}/knowledge-areas/${knowledgeArea.id}`, knowledgeArea, { observe: 'response' });
    }

    deleteKnowledgeArea(knowledgeAreaId: number) {
        return this.httpClient.delete<void>(`${this.resourceURL}/knowledge-areas/${knowledgeAreaId}`, { observe: 'response' });
    }
}
