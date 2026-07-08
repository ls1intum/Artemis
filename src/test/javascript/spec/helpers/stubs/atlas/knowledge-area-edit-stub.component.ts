import { Component, input, output } from '@angular/core';
import { KnowledgeArea, KnowledgeAreaDTO } from 'app/atlas/shared/entities/standardized-competency.model';
import { Observable } from 'rxjs';

@Component({
    selector: 'jhi-knowledge-area-edit',
    template: '',
})
export class KnowledgeAreaEditStubComponent {
    knowledgeAreas = input<KnowledgeArea[]>([]);
    knowledgeArea = input<KnowledgeAreaDTO>();
    isEditing = input(false);
    dialogError = input<Observable<string>>();

    onSave = output<KnowledgeAreaDTO>();
    onDelete = output<number>();
    onClose = output<void>();
    onOpenNewCompetency = output<number>();
    onOpenNewKnowledgeArea = output<number>();
    isEditingChange = output<boolean>();
}
