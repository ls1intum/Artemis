import { NestedTreeControl } from '@angular/cdk/tree';
import { Component, ContentChild, TemplateRef, input } from '@angular/core';
import { faChevronRight } from '@fortawesome/free-solid-svg-icons';
import { KnowledgeAreaForTree, StandardizedCompetencyForTree } from 'app/atlas/shared/entities/standardized-competency.model';
import { MatTreeModule, MatTreeNestedDataSource } from '@angular/material/tree';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { NgbCollapse } from '@ng-bootstrap/ng-bootstrap';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { CommonModule } from '@angular/common';

@Component({
    selector: 'jhi-knowledge-area-tree',
    templateUrl: './knowledge-area-tree.component.html',
    styleUrls: ['./knowledge-area-tree.component.scss'],
    imports: [MatTreeModule, FaIconComponent, NgbCollapse, TranslateDirective, CommonModule],
})
export class KnowledgeAreaTreeComponent {
    dataSource = input(new MatTreeNestedDataSource<KnowledgeAreaForTree>());
    treeControl = input(new NestedTreeControl<KnowledgeAreaForTree>((node) => node.children));

    @ContentChild('knowledgeAreaTemplate') knowledgeAreaTemplate: TemplateRef<{ knowledgeArea: KnowledgeAreaForTree }>;
    @ContentChild('competencyTemplate') competencyTemplate: TemplateRef<{ competency: StandardizedCompetencyForTree; knowledgeArea: KnowledgeAreaForTree }>;

    //Icons
    protected readonly faChevronRight = faChevronRight;
    //constants
    readonly trackBy = (_: number, node: KnowledgeAreaForTree) => node.id;
}
