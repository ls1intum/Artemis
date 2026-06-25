import { Component, ContentChild, Input, TemplateRef } from '@angular/core';
import { TreeNode } from 'primeng/api';
import { KnowledgeAreaForTree } from 'app/atlas/shared/entities/standardized-competency.model';

@Component({
    selector: 'jhi-knowledge-area-tree',
    template: '',
    standalone: true,
})
export class KnowledgeAreaTreeStubComponent {
    @Input() nodes: TreeNode<KnowledgeAreaForTree>[] = [];

    @ContentChild('knowledgeAreaTemplate') knowledgeAreaTemplate: TemplateRef<any>;
    @ContentChild('competencyTemplate') competencyTemplate: TemplateRef<any>;
}
