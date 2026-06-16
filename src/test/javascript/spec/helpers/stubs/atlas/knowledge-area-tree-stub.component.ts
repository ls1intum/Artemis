import { Component, ContentChild, Input, TemplateRef } from '@angular/core';
import { KnowledgeAreaTreeDataSource } from 'app/atlas/shared/standardized-competencies/knowledge-area-tree.component';

@Component({
    selector: 'jhi-knowledge-area-tree',
    template: '',
    standalone: true,
})
export class KnowledgeAreaTreeStubComponent {
    @Input() dataSource: KnowledgeAreaTreeDataSource;

    @ContentChild('knowledgeAreaTemplate') knowledgeAreaTemplate: TemplateRef<any>;
    @ContentChild('competencyTemplate') competencyTemplate: TemplateRef<any>;
}
