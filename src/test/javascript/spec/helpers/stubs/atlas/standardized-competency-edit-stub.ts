import { Component, input, output } from '@angular/core';
import { KnowledgeArea, Source, StandardizedCompetencyDTO } from 'app/atlas/shared/entities/standardized-competency.model';
import { Observable } from 'rxjs';

@Component({
    selector: 'jhi-standardized-competency-edit',
    template: '',
})
export class StandardizedCompetencyEditStubComponent {
    knowledgeAreas = input<KnowledgeArea[]>([]);
    sources = input<Source[]>([]);
    competency = input<StandardizedCompetencyDTO>();
    isEditing = input(false);
    dialogError = input<Observable<string>>();

    onSave = output<StandardizedCompetencyDTO>();
    onDelete = output<number>();
    onClose = output<void>();
    isEditingChange = output<boolean>();
}
