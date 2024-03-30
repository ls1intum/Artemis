import { Component, EventEmitter, Input, Output } from '@angular/core';
import { KnowledgeArea, StandardizedCompetencyDTO } from 'app/entities/competency/standardized-competency.model';
import { Observable } from 'rxjs';

@Component({
    selector: 'jhi-standardized-competency-detail',
    template: '',
})
export class StandardizedCompetencyDetailComponent {
    @Input() knowledgeAreas: KnowledgeArea[] = [];
    @Input({ required: true }) competency: StandardizedCompetencyDTO;
    @Input() isInEditMode = false;
    @Input() dialogError: Observable<string>;

    @Output() onSave = new EventEmitter<StandardizedCompetencyDTO>();
    @Output() onDelete = new EventEmitter<void>();
    @Output() onClose = new EventEmitter<void>();
    @Output() isInEditModeChange = new EventEmitter<boolean>();
}
