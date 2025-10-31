import { AfterViewInit, Component, ContentChild, EventEmitter, Output, TemplateRef, ViewChild, input } from '@angular/core';
import { faChevronRight } from '@fortawesome/free-solid-svg-icons';
import { KnowledgeAreaForTree, StandardizedCompetencyForTree } from 'app/atlas/shared/entities/standardized-competency.model';
import { MatTree, MatTreeModule, MatTreeNestedDataSource } from '@angular/material/tree';
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
export class KnowledgeAreaTreeComponent implements AfterViewInit {
    dataSource = input(new MatTreeNestedDataSource<KnowledgeAreaForTree>());
    childrenAccessor = input((node: KnowledgeAreaForTree) => node.children ?? []);

    @ViewChild(MatTree) private matTree?: MatTree<KnowledgeAreaForTree>;
    @Output() treeReady = new EventEmitter<KnowledgeAreaTreeComponent>();

    @ContentChild('knowledgeAreaTemplate') knowledgeAreaTemplate: TemplateRef<{ knowledgeArea: KnowledgeAreaForTree }>;
    @ContentChild('competencyTemplate') competencyTemplate: TemplateRef<{ competency: StandardizedCompetencyForTree; knowledgeArea: KnowledgeAreaForTree }>;

    //Icons
    protected readonly faChevronRight = faChevronRight;
    //constants
    readonly trackBy = (_: number, node: KnowledgeAreaForTree) => node.id;

    ngAfterViewInit() {
        this.treeReady.emit(this);
    }

    isExpanded(node: KnowledgeAreaForTree) {
        return !!this.matTree?.isExpanded(node);
    }

    expand(node: KnowledgeAreaForTree) {
        this.matTree?.expand(node);
    }

    collapse(node: KnowledgeAreaForTree) {
        this.matTree?.collapse(node);
    }

    collapseAll() {
        this.matTree?.collapseAll();
    }

    expandAll() {
        this.matTree?.expandAll();
    }
}
