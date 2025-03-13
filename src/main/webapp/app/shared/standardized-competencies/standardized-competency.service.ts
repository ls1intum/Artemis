import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { KnowledgeAreaDTO, Source, StandardizedCompetency } from 'app/entities/competency/standardized-competency.model';

@Injectable({
    providedIn: 'root',
})
export class StandardizedCompetencyService {
    private httpClient = inject(HttpClient);

    private resourceURL = 'api/atlas/standardized-competencies';

    getStandardizedCompetency(competencyId: number) {
        return this.httpClient.get<StandardizedCompetency>(`${this.resourceURL}/${competencyId}`, { observe: 'response' });
    }

    getAllForTreeView() {
        return this.httpClient.get<KnowledgeAreaDTO[]>(`${this.resourceURL}/for-tree-view`, { observe: 'response' });
    }

    getSources() {
        return this.httpClient.get<Source[]>(`${this.resourceURL}/sources`, { observe: 'response' });
    }
}
