import { Component, Input } from '@angular/core';
import { KnowledgeArea } from 'app/entities/competency/standardized-competency.model';

@Component({
    selector: 'jhi-knowledge-area-detail',
    templateUrl: './knowledge-area-detail.component.html',
})
export class KnowledgeAreaDetailComponent {
    @Input({ required: true }) knowledgeArea: KnowledgeArea;

    //TODO: edit, delete, create button -> Add in a later PR.
}
