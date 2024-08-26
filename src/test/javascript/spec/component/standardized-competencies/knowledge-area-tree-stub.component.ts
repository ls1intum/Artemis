import { NestedTreeControl } from '@angular/cdk/tree';
import { Component, ContentChild, Input, TemplateRef } from '@angular/core';
import { MatTreeNestedDataSource } from '@angular/material/tree';
import { KnowledgeAreaForTree } from 'app/entities/competency/standardized-competency.model';

@Component({
    selector: 'jhi-knowledge-area-tree',
    template: '',
})
export class KnowledgeAreaTreeStubComponent {
    @Input({ required: true }) dataSource: MatTreeNestedDataSource<KnowledgeAreaForTree>;
    @Input({ required: true }) treeControl: NestedTreeControl<KnowledgeAreaForTree>;

    @ContentChild('knowledgeAreaTemplate') knowledgeAreaTemplate: TemplateRef<any>;
    @ContentChild('competencyTemplate') competencyTemplate: TemplateRef<any>;
}
