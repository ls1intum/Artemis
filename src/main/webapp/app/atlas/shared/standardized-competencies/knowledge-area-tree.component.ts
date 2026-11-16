import { ChangeDetectionStrategy, Component, ElementRef, TemplateRef, computed, contentChild, inject, input, signal } from '@angular/core';
import { KnowledgeAreaForTree, StandardizedCompetencyForTree } from 'app/atlas/shared/entities/standardized-competency.model';
import { faChevronDown, faChevronRight } from '@fortawesome/free-solid-svg-icons';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { NgTemplateOutlet } from '@angular/common';

/**
 * Minimal data source contract for the knowledge area tree: a mutable `data` array, so consumers and the
 * {@link StandardizedCompetencyFilterPageComponent} base class can keep reading/writing `dataSource.data`.
 */
export interface KnowledgeAreaTreeDataSource {
    data: KnowledgeAreaForTree[];
}

/** Node type used to render a knowledge area via the consumer-provided knowledgeAreaTemplate. */
export const KNOWLEDGE_AREA_NODE_TYPE = 'knowledgeArea';
/** Node type used to render a competency via the consumer-provided competencyTemplate. */
export const COMPETENCY_NODE_TYPE = 'competency';
/** Node type used to render the empty-state placeholder for a knowledge area without children/competencies. */
export const EMPTY_NODE_TYPE = 'emptyKnowledgeArea';

export interface CompetencyNodeData {
    competency: StandardizedCompetencyForTree;
    knowledgeArea: KnowledgeAreaForTree;
}

/** A node of the rendered tree: a knowledge area with its children, a competency, or the empty-state placeholder. */
export interface KnowledgeAreaTreeNode {
    key: string;
    type: string;
    data?: KnowledgeAreaForTree | CompetencyNodeData;
    expanded?: boolean;
    children?: KnowledgeAreaTreeNode[];
}

/**
 * Renders a forest of {@link KnowledgeAreaForTree} as an ARIA tree of native elements (`role="tree"`, `treeitem`, `group`).
 *
 * Consumers provide two content templates that are projected into the corresponding tree nodes:
 * - `#knowledgeAreaTemplate` with context `{ knowledgeArea }`
 * - `#competencyTemplate` with context `{ competency, knowledgeArea }`
 *
 * The {@link KnowledgeAreaForTree} forest is converted to a {@link KnowledgeAreaTreeNode} array internally; nodes with
 * `isVisible === false` are filtered out. Expansion state is kept in a
 * local set keyed by knowledge area id so it survives the rebuilds triggered by in-place data mutations.
 */
@Component({
    selector: 'jhi-knowledge-area-tree',
    templateUrl: './knowledge-area-tree.component.html',
    imports: [FaIconComponent, TranslateDirective, NgTemplateOutlet],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class KnowledgeAreaTreeComponent {
    private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);

    protected readonly KNOWLEDGE_AREA_NODE_TYPE = KNOWLEDGE_AREA_NODE_TYPE;
    protected readonly COMPETENCY_NODE_TYPE = COMPETENCY_NODE_TYPE;
    protected readonly EMPTY_NODE_TYPE = EMPTY_NODE_TYPE;
    protected readonly faChevronDown = faChevronDown;
    protected readonly faChevronRight = faChevronRight;

    readonly dataSource = input<KnowledgeAreaTreeDataSource>({ data: [] });

    readonly knowledgeAreaTemplate = contentChild<TemplateRef<{ knowledgeArea: KnowledgeAreaForTree }>>('knowledgeAreaTemplate');
    readonly competencyTemplate = contentChild<TemplateRef<{ competency: StandardizedCompetencyForTree; knowledgeArea: KnowledgeAreaForTree }>>('competencyTemplate');

    /** Ids of expanded knowledge areas. Persists expansion across tree rebuilds. */
    private readonly expandedIds = signal<Set<number>>(new Set<number>());

    /**
     * The tree nodes built from the {@link dataSource} forest. Recomputed when the data source input or the
     * expansion state changes. Because consumers mutate `dataSource.data` and the contained objects in place,
     * {@link rebuild} must be called to pick those changes up (the base class control methods do this implicitly).
     */
    protected readonly nodes = computed<KnowledgeAreaTreeNode[]>(() => {
        // touch the rebuild trigger so in-place data mutations are reflected after rebuild() is called
        this.rebuildTrigger();
        const expanded = this.expandedIds();
        return this.buildKnowledgeAreaNodes(this.dataSource().data ?? [], expanded);
    });

    protected readonly focusedKey = signal<string | undefined>(undefined);
    /** Keep a single tree row in the tab order, falling back when filtering or collapse removes the focused row. */
    protected readonly tabStopKey = computed(() => {
        const visibleKeys = (nodes: KnowledgeAreaTreeNode[]): string[] => nodes.flatMap((node) => [node.key, ...(node.expanded ? visibleKeys(node.children ?? []) : [])]);
        const keys = visibleKeys(this.nodes());
        const focused = this.focusedKey();
        return focused && keys.includes(focused) ? focused : keys[0];
    });

    /** Incremented to force {@link nodes} to recompute after in-place data mutations. */
    private readonly rebuildTrigger = signal(0);

    rebuild(): void {
        this.rebuildTrigger.update((value) => value + 1);
    }

    expand(knowledgeArea: KnowledgeAreaForTree): void {
        if (knowledgeArea.id === undefined) {
            return;
        }
        this.expandedIds.update((ids) => {
            const next = new Set(ids);
            next.add(knowledgeArea.id!);
            return next;
        });
    }

    /**
     * Toggles the expansion of a knowledge area. The focusable row, title and chevron call it.
     * @param knowledgeArea the knowledge area whose expansion to toggle
     */
    protected toggle(knowledgeArea: KnowledgeAreaForTree): void {
        if (knowledgeArea.id === undefined) {
            return;
        }
        this.expandedIds.update((ids) => {
            const next = new Set(ids);
            if (next.has(knowledgeArea.id!)) {
                next.delete(knowledgeArea.id!);
            } else {
                next.add(knowledgeArea.id!);
            }
            return next;
        });
    }

    /**
     * Navigate all visible tree rows, including competency and empty-state leaves. Keys from projected controls
     * belong to those controls. Right expands or enters a child; left collapses or returns to the parent.
     */
    protected onNodeKeydown(event: KeyboardEvent, node: KnowledgeAreaTreeNode): void {
        if (event.target !== event.currentTarget) {
            return;
        }
        const row = event.currentTarget as HTMLElement;
        const rows = Array.from(this.host.nativeElement.querySelectorAll<HTMLElement>('[role="treeitem"]'));
        const index = rows.indexOf(row);
        const knowledgeArea = node.type === KNOWLEDGE_AREA_NODE_TYPE ? (node.data as KnowledgeAreaForTree) : undefined;
        let target: HTMLElement | undefined;
        switch (event.key) {
            case 'ArrowRight':
                if (knowledgeArea && !node.expanded) {
                    this.expand(knowledgeArea);
                } else if (node.expanded && node.children?.length) {
                    target = rows[index + 1];
                }
                break;
            case 'ArrowLeft':
                if (knowledgeArea && node.expanded) {
                    this.toggle(knowledgeArea);
                } else {
                    target = row.parentElement?.closest<HTMLElement>('[role="treeitem"]') ?? undefined;
                }
                break;
            case 'ArrowDown':
                target = rows[index + 1];
                break;
            case 'ArrowUp':
                target = rows[index - 1];
                break;
            case 'Home':
                target = rows[0];
                break;
            case 'End':
                target = rows[rows.length - 1];
                break;
            case 'Enter':
            case ' ':
                if (!knowledgeArea) {
                    return;
                }
                this.toggle(knowledgeArea);
                break;
            default:
                return;
        }
        event.preventDefault();
        target?.focus();
    }

    isExpanded(knowledgeArea: KnowledgeAreaForTree): boolean {
        return knowledgeArea.id !== undefined && this.expandedIds().has(knowledgeArea.id);
    }

    expandAll(): void {
        const allIds = new Set<number>();
        this.collectKnowledgeAreaIds(this.dataSource().data ?? [], allIds);
        this.expandedIds.set(allIds);
    }

    collapseAll(): void {
        this.expandedIds.set(new Set<number>());
    }

    private collectKnowledgeAreaIds(knowledgeAreas: KnowledgeAreaForTree[], target: Set<number>): void {
        for (const knowledgeArea of knowledgeAreas) {
            if (knowledgeArea.id !== undefined) {
                target.add(knowledgeArea.id);
            }
            this.collectKnowledgeAreaIds(knowledgeArea.children ?? [], target);
        }
    }

    private buildKnowledgeAreaNodes(knowledgeAreas: KnowledgeAreaForTree[], expanded: Set<number>): KnowledgeAreaTreeNode[] {
        return knowledgeAreas
            .filter((knowledgeArea) => knowledgeArea.isVisible)
            .map((knowledgeArea) => {
                const childKnowledgeAreaNodes = this.buildKnowledgeAreaNodes(knowledgeArea.children ?? [], expanded);
                const competencyNodes = (knowledgeArea.competencies ?? [])
                    .filter((competency) => competency.isVisible)
                    .map<KnowledgeAreaTreeNode>((competency) => ({
                        key: `competency-${competency.id}`,
                        type: COMPETENCY_NODE_TYPE,
                        data: { competency, knowledgeArea },
                    }));

                const children: KnowledgeAreaTreeNode[] = [...childKnowledgeAreaNodes, ...competencyNodes];
                if (!knowledgeArea.children?.length && !knowledgeArea.competencies?.length) {
                    children.push({ key: `empty-${knowledgeArea.id}`, type: EMPTY_NODE_TYPE });
                }

                return {
                    key: `knowledgeArea-${knowledgeArea.id}`,
                    type: KNOWLEDGE_AREA_NODE_TYPE,
                    data: knowledgeArea,
                    expanded: knowledgeArea.id !== undefined && expanded.has(knowledgeArea.id),
                    children,
                };
            });
    }
}
