import { Component, input, output } from '@angular/core';
import { KnowledgeAreaDTO } from 'app/atlas/shared/entities/standardized-competency.model';

@Component({
    selector: 'jhi-standardized-competency-filter',
    template: '',
    standalone: true,
})
export class StandardizedCompetencyFilterStubComponent {
    competencyTitleFilter = input<string>();
    knowledgeAreaFilter = input<KnowledgeAreaDTO>();
    knowledgeAreasForSelect = input<KnowledgeAreaDTO[]>([]);

    competencyTitleFilterChange = output<string>();
    knowledgeAreaFilterChange = output<KnowledgeAreaDTO>();
}
