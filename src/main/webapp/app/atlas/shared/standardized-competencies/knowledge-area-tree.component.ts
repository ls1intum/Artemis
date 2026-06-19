import { ChangeDetectionStrategy, Component, TemplateRef, computed, contentChild, input, signal } from '@angular/core';
import { KnowledgeAreaForTree, StandardizedCompetencyForTree } from 'app/atlas/shared/entities/standardized-competency.model';
import { TreeNode } from 'primeng/api';
import { TreeModule } from 'primeng/tree';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { NgTemplateOutlet } from '@angular/common';

/**
 * Minimal data source contract for the knowledge area tree. Mirrors the small surface of the previous
 * {@link MatTreeNestedDataSource} (a mutable `data` array) so consumers and the
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

/** Data attached to a competency tree node, mirroring the previous competencyTemplate context. */
export interface CompetencyNodeData {
    competency: StandardizedCompetencyForTree;
    knowledgeArea: KnowledgeAreaForTree;
}

/**
 * Renders a forest of {@link KnowledgeAreaForTree} as a PrimeNG `p-tree`.
 *
 * Consumers provide two content templates that are projected into the corresponding tree nodes:
 * - `#knowledgeAreaTemplate` with context `{ knowledgeArea }`
 * - `#competencyTemplate` with context `{ competency, knowledgeArea }`
 *
 * The {@link KnowledgeAreaForTree} forest is converted to a PrimeNG {@link TreeNode} array internally; nodes with
 * `isVisible === false` are filtered out (matching the previous `d-none` behavior). Expansion state is kept in a
 * local set keyed by knowledge area id so it survives the rebuilds triggered by in-place data mutations.
 */
@Component({
    selector: 'jhi-knowledge-area-tree',
    templateUrl: './knowledge-area-tree.component.html',
    imports: [TreeModule, TranslateDirective, NgTemplateOutlet],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class KnowledgeAreaTreeComponent {
    readonly dataSource = input<KnowledgeAreaTreeDataSource>({ data: [] });

    readonly knowledgeAreaTemplate = contentChild<TemplateRef<{ knowledgeArea: KnowledgeAreaForTree }>>('knowledgeAreaTemplate');
    readonly competencyTemplate = contentChild<TemplateRef<{ competency: StandardizedCompetencyForTree; knowledgeArea: KnowledgeAreaForTree }>>('competencyTemplate');

    /** Ids of expanded knowledge areas. Persists expansion across tree rebuilds. */
    private readonly expandedIds = signal<Set<number>>(new Set<number>());

    /**
     * The PrimeNG tree nodes built from the {@link dataSource} forest. Recomputed when the data source input or the
     * expansion state changes. Because consumers mutate `dataSource.data` and the contained objects in place,
     * {@link rebuild} must be called to pick those changes up (the base class control methods do this implicitly).
     */
    protected readonly nodes = computed<TreeNode<KnowledgeAreaForTree | CompetencyNodeData>[]>(() => {
        // touch the rebuild trigger so in-place data mutations are reflected after rebuild() is called
        this.rebuildTrigger();
        const expanded = this.expandedIds();
        return this.buildKnowledgeAreaNodes(this.dataSource().data ?? [], expanded);
    });

    /** Incremented to force {@link nodes} to recompute after in-place data mutations. */
    private readonly rebuildTrigger = signal(0);

    /**
     * Forces the tree to rebuild its nodes from the current (possibly mutated in place) data source.
     */
    rebuild(): void {
        this.rebuildTrigger.update((value) => value + 1);
    }

    /** Syncs the persisted expansion state when the user expands a node via the built-in toggler. */
    protected onNodeExpand(node: TreeNode<KnowledgeAreaForTree | CompetencyNodeData>): void {
        const knowledgeArea = node.data as KnowledgeAreaForTree | undefined;
        if (node.type === KNOWLEDGE_AREA_NODE_TYPE && knowledgeArea?.id !== undefined) {
            this.expandedIds.update((ids) => {
                const next = new Set(ids);
                next.add(knowledgeArea.id!);
                return next;
            });
        }
    }

    /** Syncs the persisted expansion state when the user collapses a node via the built-in toggler. */
    protected onNodeCollapse(node: TreeNode<KnowledgeAreaForTree | CompetencyNodeData>): void {
        const knowledgeArea = node.data as KnowledgeAreaForTree | undefined;
        if (node.type === KNOWLEDGE_AREA_NODE_TYPE && knowledgeArea?.id !== undefined) {
            this.expandedIds.update((ids) => {
                const next = new Set(ids);
                next.delete(knowledgeArea.id!);
                return next;
            });
        }
    }

    /**
     * Expands a single knowledge area (and forces a rebuild so the change is rendered).
     * @param knowledgeArea the knowledge area to expand
     */
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
     * Toggles the expansion of a knowledge area. Restores the whole-title click-to-toggle affordance the
     * previous MatTree row had; the built-in p-tree chevron remains the keyboard-accessible toggle.
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
     * Returns whether the given knowledge area is currently expanded.
     * @param knowledgeArea the knowledge area to check
     */
    isExpanded(knowledgeArea: KnowledgeAreaForTree): boolean {
        return knowledgeArea.id !== undefined && this.expandedIds().has(knowledgeArea.id);
    }

    /** Expands every knowledge area in the tree. */
    expandAll(): void {
        const allIds = new Set<number>();
        this.collectKnowledgeAreaIds(this.dataSource().data ?? [], allIds);
        this.expandedIds.set(allIds);
    }

    /** Collapses every knowledge area in the tree. */
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

    /**
     * Recursively builds the PrimeNG tree nodes for the given knowledge areas, filtering out hidden ones and
     * applying the persisted expansion state.
     */
    private buildKnowledgeAreaNodes(knowledgeAreas: KnowledgeAreaForTree[], expanded: Set<number>): TreeNode<KnowledgeAreaForTree | CompetencyNodeData>[] {
        return knowledgeAreas
            .filter((knowledgeArea) => knowledgeArea.isVisible)
            .map((knowledgeArea) => {
                const childKnowledgeAreaNodes = this.buildKnowledgeAreaNodes(knowledgeArea.children ?? [], expanded);
                const competencyNodes = (knowledgeArea.competencies ?? [])
                    .filter((competency) => competency.isVisible)
                    .map<TreeNode<CompetencyNodeData>>((competency) => ({
                        key: `competency-${competency.id}`,
                        type: COMPETENCY_NODE_TYPE,
                        leaf: true,
                        data: { competency, knowledgeArea },
                    }));

                const children: TreeNode<KnowledgeAreaForTree | CompetencyNodeData>[] = [...childKnowledgeAreaNodes, ...competencyNodes];
                if (!knowledgeArea.children?.length && !knowledgeArea.competencies?.length) {
                    children.push({ key: `empty-${knowledgeArea.id}`, type: EMPTY_NODE_TYPE, leaf: true, selectable: false });
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
