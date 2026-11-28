import { Service, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { StandardizedCompetencyApi } from 'app/openapi/api/standardized-competency-api';
import { KnowledgeAreaResult } from 'app/openapi/model/knowledge-area-result';
import { Source } from 'app/openapi/model/source';

/** Observable catalog reads; importer resources belong to their component's lifetime. */
@Service()
export class StandardizedCompetencyService {
    private readonly api = inject(StandardizedCompetencyApi);

    getAllForTreeView(): Observable<KnowledgeAreaResult[]> {
        return this.api.getAllForTreeView();
    }

    getSources(): Observable<Source[]> {
        return this.api.getSources();
    }
}
