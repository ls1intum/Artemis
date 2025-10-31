import { AfterViewInit, Component, ContentChild, EventEmitter, Input, Output, TemplateRef } from '@angular/core';
import { MatTreeNestedDataSource } from '@angular/material/tree';
import { KnowledgeAreaForTree } from 'app/atlas/shared/entities/standardized-competency.model';

@Component({
    selector: 'jhi-knowledge-area-tree',
    template: '',
})
export class KnowledgeAreaTreeStubComponent implements AfterViewInit {
    @Input({ required: true }) dataSource: MatTreeNestedDataSource<KnowledgeAreaForTree>;
    @Input() childrenAccessor?: (node: KnowledgeAreaForTree) => KnowledgeAreaForTree[] | undefined;
    @Output() treeReady = new EventEmitter<KnowledgeAreaTreeStubComponent>();

    @ContentChild('knowledgeAreaTemplate') knowledgeAreaTemplate: TemplateRef<any>;
    @ContentChild('competencyTemplate') competencyTemplate: TemplateRef<any>;

    ngAfterViewInit(): void {
        this.treeReady.emit(this);
    }

    collapseAll() {}

    expand(_: KnowledgeAreaForTree) {}

    expandAll() {}
}
