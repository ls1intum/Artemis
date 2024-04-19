import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { KnowledgeAreaDTO, StandardizedCompetencyDTO } from 'app/entities/competency/standardized-competency.model';

@Injectable({
    providedIn: 'root',
})
export class AdminStandardizedCompetencyService {
    private resourceURL = 'api/admin/standardized-competencies';

    constructor(private httpClient: HttpClient) {}

    createStandardizedCompetency(competency: StandardizedCompetencyDTO) {
        return this.httpClient.post<StandardizedCompetencyDTO>(`${this.resourceURL}`, competency, { observe: 'response' });
    }

    updateStandardizedCompetency(competency: StandardizedCompetencyDTO) {
        return this.httpClient.put<StandardizedCompetencyDTO>(`${this.resourceURL}/${competency.id}`, competency, { observe: 'response' });
    }

    deleteStandardizedCompetency(competencyId: number) {
        return this.httpClient.delete<void>(`${this.resourceURL}/${competencyId}`, { observe: 'response' });
    }

    createKnowledgeArea(knowledgeArea: KnowledgeAreaDTO) {
        return this.httpClient.post<KnowledgeAreaDTO>(`${this.resourceURL}/knowledge-areas`, knowledgeArea, { observe: 'response' });
    }

    updateKnowledgeArea(knowledgeArea: KnowledgeAreaDTO) {
        return this.httpClient.put<KnowledgeAreaDTO>(`${this.resourceURL}/knowledge-areas/${knowledgeArea.id}`, knowledgeArea, { observe: 'response' });
    }

    deleteKnowledgeArea(knowledgeAreaId: number) {
        return this.httpClient.delete<void>(`${this.resourceURL}/knowledge-areas/${knowledgeAreaId}`, { observe: 'response' });
    }
}
