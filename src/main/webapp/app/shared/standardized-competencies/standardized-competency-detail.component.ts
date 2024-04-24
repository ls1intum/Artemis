import { Component, EventEmitter, Input, Output } from '@angular/core';
import { StandardizedCompetencyDTO } from 'app/entities/competency/standardized-competency.model';
import { ButtonSize, ButtonType } from 'app/shared/components/button.component';

@Component({
    selector: 'jhi-standardized-competency-detail',
    templateUrl: './standardized-competency-detail.component.html',
})
export class StandardizedCompetencyDetailComponent {
    // values for the knowledge area select
    @Input({ required: true }) competency: StandardizedCompetencyDTO;
    //TODO: input for buttons
    //TODO: another type so I can see the knowledgeAreaTitle??? or have it as input ^^

    @Output() onClose = new EventEmitter<void>();

    // other constants
    protected readonly ButtonSize = ButtonSize;
    protected readonly ButtonType = ButtonType;

    constructor() {}

    close() {
        this.onClose.emit();
    }
}
