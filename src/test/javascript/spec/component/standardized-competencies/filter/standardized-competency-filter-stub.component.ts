import { Component, EventEmitter, Input, OnDestroy, OnInit, Output } from '@angular/core';
import { KnowledgeAreaDTO } from 'app/entities/competency/standardized-competency.model';

@Component({
    selector: 'jhi-standardized-competency-filter',
    template: '',
})
export class StandardizedCompetencyFilterStubComponent implements OnInit, OnDestroy {
    @Input() competencyTitleFilter: string;
    @Input() knowledgeAreaFilter: KnowledgeAreaDTO | undefined = undefined;
    @Input() knowledgeAreasForSelect: KnowledgeAreaDTO[] = [];

    @Output() competencyTitleFilterChange = new EventEmitter<string>();
    @Output() knowledgeAreaFilterChange = new EventEmitter<KnowledgeAreaDTO>();
}
