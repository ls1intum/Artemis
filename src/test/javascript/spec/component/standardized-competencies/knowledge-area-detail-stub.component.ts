import { Component, EventEmitter, Input, Output } from '@angular/core';
import { KnowledgeArea, KnowledgeAreaDTO } from 'app/entities/competency/standardized-competency.model';
import { Observable } from 'rxjs';

@Component({
    selector: 'jhi-knowledge-area-detail',
    template: '',
})
export class KnowledgeAreaDetailStubComponent {
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
