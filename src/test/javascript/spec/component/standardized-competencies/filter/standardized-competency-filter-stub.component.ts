import { Component, EventEmitter, Input, Output } from '@angular/core';
import { KnowledgeAreaDTO } from 'app/entities/competency/standardized-competency.model';

@Component({
    selector: 'jhi-standardized-competency-filter',
    template: '',
})
export class StandardizedCompetencyFilterStubComponent {
    @Input() competencyTitleFilter: string;
    @Input() knowledgeAreaFilter?: KnowledgeAreaDTO;
    @Input() knowledgeAreasForSelect: KnowledgeAreaDTO[] = [];

    @Output() competencyTitleFilterChange = new EventEmitter<string>();
    @Output() knowledgeAreaFilterChange = new EventEmitter<KnowledgeAreaDTO>();
}
