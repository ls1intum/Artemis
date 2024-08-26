import { Component, EventEmitter, Input, Output } from '@angular/core';
import { KnowledgeArea, Source, StandardizedCompetencyDTO } from 'app/entities/competency/standardized-competency.model';
import { Observable } from 'rxjs';

@Component({
    selector: 'jhi-standardized-competency-edit',
    template: '',
})
export class StandardizedCompetencyEditStubComponent {
    @Input() knowledgeAreas: KnowledgeArea[] = [];
    @Input() sources: Source[] = [];
    @Input() competency: StandardizedCompetencyDTO;
    @Input() isEditing = false;
    @Input() dialogError: Observable<string>;

    @Output() onSave = new EventEmitter<StandardizedCompetencyDTO>();
    @Output() onDelete = new EventEmitter<number>();
    @Output() onClose = new EventEmitter<void>();
    @Output() isEditingChange = new EventEmitter<boolean>();
}
