import { HttpClient } from '@angular/common/http';
import { Service, inject } from '@angular/core';
import { KnowledgeAreaDTO, Source, StandardizedCompetencyDTO } from 'app/atlas/shared/entities/standardized-competency.model';

@Service()
export class StandardizedCompetencyService {
    private httpClient = inject(HttpClient);

    private resourceURL = 'api/atlas/standardized-competencies';

    getStandardizedCompetency(competencyId: number) {
        return this.httpClient.get<StandardizedCompetencyDTO>(`${this.resourceURL}/${competencyId}`, { observe: 'response' });
    }

    getAllForTreeView() {
        return this.httpClient.get<KnowledgeAreaDTO[]>(`${this.resourceURL}/for-tree-view`, { observe: 'response' });
    }

    getSources() {
        return this.httpClient.get<Source[]>(`${this.resourceURL}/sources`, { observe: 'response' });
    }
}
