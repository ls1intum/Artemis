import { Component, EventEmitter, Output, input } from '@angular/core';
import { Competency, CompetencyRelation } from 'app/entities/competency.model';

@Component({
    selector: 'jhi-competency-relation-graph',
    template: '',
})
export class CompetencyRelationGraphStubComponent {
    competencies = input<Competency[]>([]);
    relations = input<CompetencyRelation[]>([]);

    @Output() onRemoveRelation = new EventEmitter<number>();
    @Output() onCreateRelation = new EventEmitter<CompetencyRelation>();
}
