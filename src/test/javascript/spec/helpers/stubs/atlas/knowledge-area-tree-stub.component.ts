import { Component, TemplateRef, contentChild, input } from '@angular/core';
import { TreeNode } from 'primeng/api';
import { KnowledgeAreaForTree, StandardizedCompetencyForTree } from 'app/atlas/shared/entities/standardized-competency.model';

@Component({
    selector: 'jhi-knowledge-area-tree',
    template: '',
    standalone: true,
})
export class KnowledgeAreaTreeStubComponent {
    nodes = input<TreeNode<KnowledgeAreaForTree | StandardizedCompetencyForTree>[]>([]);

    knowledgeAreaTemplate = contentChild<TemplateRef<any>>('knowledgeAreaTemplate');
    competencyTemplate = contentChild<TemplateRef<any>>('competencyTemplate');
}
