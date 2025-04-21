import { Component, EventEmitter, Input, Output } from '@angular/core';
import { KnowledgeArea, KnowledgeAreaDTO } from 'app/atlas/shared/entities/standardized-competency.model';
import { Observable } from 'rxjs';

@Component({
    selector: 'jhi-knowledge-area-edit',
    template: '',
})
export class KnowledgeAreaEditStubComponent {
    @Input() knowledgeAreas: KnowledgeArea[] = [];
    @Input() knowledgeArea: KnowledgeAreaDTO;
    @Input() isEditing = false;
    @Input() dialogError: Observable<string>;

    @Output() onSave = new EventEmitter<KnowledgeAreaDTO>();
    @Output() onDelete = new EventEmitter<number>();
    @Output() onClose = new EventEmitter<void>();
    @Output() onOpenNewCompetency = new EventEmitter<number>();
    @Output() onOpenNewKnowledgeArea = new EventEmitter<number>();
    @Output() isEditingChange = new EventEmitter<boolean>();
}
