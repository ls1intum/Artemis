import { NestedTreeControl } from '@angular/cdk/tree';
import { Component, TemplateRef, contentChild, input } from '@angular/core';
import { faChevronRight } from '@fortawesome/free-solid-svg-icons';
import { KnowledgeAreaForTree } from 'app/atlas/shared/entities/standardized-competency.model';
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
    dataSource = input<MatTreeNestedDataSource<KnowledgeAreaForTree>>(new MatTreeNestedDataSource<KnowledgeAreaForTree>());
    treeControl = input<NestedTreeControl<KnowledgeAreaForTree>>(new NestedTreeControl<KnowledgeAreaForTree>((node) => node.children));

    knowledgeAreaTemplate = contentChild(TemplateRef<any>);
    competencyTemplate = contentChild(TemplateRef<any>);

    //Icons
    protected readonly faChevronRight = faChevronRight;
    //constants
    readonly trackBy = (_: number, node: KnowledgeAreaForTree) => node.id;
}
