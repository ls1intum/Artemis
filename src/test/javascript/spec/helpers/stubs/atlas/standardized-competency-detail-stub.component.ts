import { Component, EventEmitter, Input, Output } from '@angular/core';
import { StandardizedCompetencyDTO } from 'app/atlas/shared/entities/standardized-competency.model';

@Component({
    selector: 'jhi-standardized-competency-detail',
    template: '',
    standalone: true,
})
export class StandardizedCompetencyDetailStubComponent {
    // values for the knowledge area select
    @Input({ required: true }) competency: StandardizedCompetencyDTO;
    @Input() knowledgeAreaTitle = '';
    @Input() sourceString = '';

    @Output() onClose = new EventEmitter<void>();
}
