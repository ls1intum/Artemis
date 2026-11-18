import { Component, input, output } from '@angular/core';
import { StandardizedCompetencyDTO } from 'app/atlas/shared/entities/standardized-competency.model';

@Component({
    selector: 'jhi-standardized-competency-detail',
    template: '',
    standalone: true,
})
export class StandardizedCompetencyDetailStubComponent {
    // values for the knowledge area select
    competency = input.required<StandardizedCompetencyDTO>();
    knowledgeAreaTitle = input('');
    sourceString = input('');

    onClose = output<void>();
}
