import { Component, ContentChild, Input, TemplateRef } from '@angular/core';
import { faChevronRight } from '@fortawesome/free-solid-svg-icons';
import { KnowledgeAreaForTree } from 'app/atlas/shared/entities/standardized-competency.model';
import { MatTreeModule, MatTreeNestedDataSource } from '@angular/material/tree';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { NgbCollapse } from '@ng-bootstrap/ng-bootstrap';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { CommonModule } from '@angular/common';

@Component({
    selector: 'jhi-knowledge-area-tree',
    standalone: true,
    templateUrl: './knowledge-area-tree.component.html',
    styleUrls: ['./knowledge-area-tree.component.scss'],
    exportAs: 'knowledgeAreaTree',
    imports: [MatTreeModule, FaIconComponent, NgbCollapse, TranslateDirective, CommonModule],
})
export class KnowledgeAreaTreeComponent {
    @Input() dataSource = new MatTreeNestedDataSource<KnowledgeAreaForTree>();
    @ContentChild('knowledgeAreaTemplate', { static: true })
    knowledgeAreaTemplate!: TemplateRef<any>;

    @ContentChild('competencyTemplate', { static: true })
    competencyTemplate!: TemplateRef<any>;

    childrenAccessor = (node: KnowledgeAreaForTree): KnowledgeAreaForTree[] => {
        return node.children || [];
    };

    protected readonly faChevronRight = faChevronRight;

    readonly trackBy = (_: number, node: KnowledgeAreaForTree) => node.id!;

    expandedNodes = new Set<number>();

    isExpanded(node: KnowledgeAreaForTree): boolean {
        return node.id !== undefined && this.expandedNodes.has(node.id);
    }

    toggleNode(node: KnowledgeAreaForTree): void {
        if (node.id === undefined) return;
        if (this.isExpanded(node)) {
            this.expandedNodes.delete(node.id);
        } else {
            this.expandedNodes.add(node.id);
        }
    }

    expandAll(): void {
        const addAll = (nodes: KnowledgeAreaForTree[] | undefined) => {
            if (!nodes) return;
            for (const node of nodes) {
                if (node.id !== undefined) {
                    this.expandedNodes.add(node.id);
                }
                addAll(node.children);
            }
        };
        this.expandedNodes.clear();
        addAll(this.dataSource.data);
    }

    collapseAll(): void {
        this.expandedNodes.clear();
    }

    // Helper method to check if node has children
    hasChildren(node: KnowledgeAreaForTree): boolean {
        return !!(node.children && node.children.length > 0);
    }
}
