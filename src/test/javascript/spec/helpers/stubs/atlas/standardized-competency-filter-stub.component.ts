import { Component, EventEmitter, Input, Output } from '@angular/core';
import { KnowledgeAreaDTO } from 'app/atlas/shared/entities/standardized-competency.model';

@Component({
    selector: 'jhi-standardized-competency-filter',
    template: '',
    standalone: true,
})
export class StandardizedCompetencyFilterStubComponent {
    @Input() competencyTitleFilter: string;
    @Input() knowledgeAreaFilter?: KnowledgeAreaDTO;
    @Input() knowledgeAreasForSelect: KnowledgeAreaDTO[] = [];

    @Output() competencyTitleFilterChange = new EventEmitter<string>();
    @Output() knowledgeAreaFilterChange = new EventEmitter<KnowledgeAreaDTO>();
}
