import { Component, TemplateRef, contentChild, input } from '@angular/core';
import { KnowledgeAreaTreeDataSource } from 'app/atlas/shared/standardized-competencies/knowledge-area-tree.component';

@Component({
    selector: 'jhi-knowledge-area-tree',
    template: '',
    standalone: true,
})
export class KnowledgeAreaTreeStubComponent {
    dataSource = input<KnowledgeAreaTreeDataSource>({ data: [] });

    knowledgeAreaTemplate = contentChild<TemplateRef<any>>('knowledgeAreaTemplate');
    competencyTemplate = contentChild<TemplateRef<any>>('competencyTemplate');
}
