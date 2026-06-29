import { Component, signal } from '@angular/core';
import { KnowledgeAreaDTO, KnowledgeAreaForTree, StandardizedCompetencyDTO } from 'app/atlas/shared/entities/standardized-competency.model';
import { KnowledgeAreaTreeNode, TREE_NODE_TYPE_KNOWLEDGE_AREA, convertToTreeNodes } from './knowledge-area-tree.component';

/**
 * A lightweight replacement for the Angular Material `MatTreeNestedDataSource`. It only holds the (mutable) domain data
 * and notifies its owner whenever the data reference is reassigned, so the derived PrimeNG `TreeNode[]` can be rebuilt.
 * Keeping the `data` getter/setter API means the consuming components (and their specs) keep working unchanged.
 */
export class KnowledgeAreaTreeDataSource {
    private currentData: KnowledgeAreaForTree[] = [];

    constructor(private readonly onChange: () => void) {}

    get data(): KnowledgeAreaForTree[] {
        return this.currentData;
    }

    set data(data: KnowledgeAreaForTree[]) {
        this.currentData = data ?? [];
        this.onChange();
    }
}

/**
 * An in-house replacement for the programmatic `MatTree` control used previously. It tracks the expansion state of knowledge
 * areas by id (so it survives `TreeNode` rebuilds) and exposes the same method names that the templates and the filter logic
 * relied on (`collapseAll`, `expandAll`, `expand`, `isExpanded`). Mutating the expansion state triggers a `TreeNode` rebuild.
 *
 * PrimeNG `<p-tree>` toggles a node by mutating its `expanded` flag directly. To keep the manual user toggles consistent with
 * this control (so they survive the periodic `TreeNode` rebuilds done after CRUD operations), the live nodes' expansion state
 * is harvested into this control via {@link harvestLiveExpansion} before a data-driven rebuild.
 */
export class KnowledgeAreaTreeControl {
    private readonly expandedIds = new Set<number>();

    constructor(
        private readonly getData: () => KnowledgeAreaForTree[],
        private readonly getNodes: () => KnowledgeAreaTreeNode[],
        private readonly onChange: () => void,
    ) {}

    /** Whether the given knowledge area is currently expanded (by its id). */
    isExpanded(knowledgeArea: KnowledgeAreaForTree): boolean {
        return knowledgeArea.id !== undefined && this.expandedIds.has(knowledgeArea.id);
    }

    /** Whether the knowledge area with the given id is currently expanded; used by {@link convertToTreeNodes} to seed each node's `expanded` flag. */
    isExpandedById(id: number | undefined): boolean {
        return id !== undefined && this.expandedIds.has(id);
    }

    /** Expands the given knowledge area, first harvesting any manual p-tree toggles so they are not lost on the ensuing rebuild. */
    expand(knowledgeArea: KnowledgeAreaForTree): void {
        this.harvestLiveExpansion();
        if (knowledgeArea.id !== undefined && !this.expandedIds.has(knowledgeArea.id)) {
            this.expandedIds.add(knowledgeArea.id);
        }
        this.onChange();
    }

    /** Collapses every knowledge area and triggers a tree rebuild. */
    collapseAll(): void {
        this.expandedIds.clear();
        this.onChange();
    }

    /** Expands every knowledge area (and descendants) and triggers a tree rebuild. */
    expandAll(): void {
        this.getData().forEach((knowledgeArea) => this.addSelfAndDescendants(knowledgeArea));
        this.onChange();
    }

    /**
     * Merges the expansion state that PrimeNG `<p-tree>` may have mutated on the live nodes (via its built-in toggler) back
     * into this control, so manual user toggles are not lost when the node array is later rebuilt (e.g. after a CRUD update).
     */
    harvestLiveExpansion(): void {
        this.mergeNodesExpansion(this.getNodes());
    }

    private mergeNodesExpansion(nodes: KnowledgeAreaTreeNode[] | undefined): void {
        for (const node of nodes ?? []) {
            const id = node.data?.id;
            if (id !== undefined) {
                if (node.expanded) {
                    this.expandedIds.add(id);
                } else {
                    this.expandedIds.delete(id);
                }
            }
            // recurse into the child knowledge-area nodes (competency leaf nodes have no expansion state)
            this.mergeNodesExpansion((node.children ?? []).filter((child): child is KnowledgeAreaTreeNode => child.type === TREE_NODE_TYPE_KNOWLEDGE_AREA));
        }
    }

    private addSelfAndDescendants(knowledgeArea: KnowledgeAreaForTree): void {
        if (knowledgeArea.id !== undefined) {
            this.expandedIds.add(knowledgeArea.id);
        }
        knowledgeArea.children?.forEach((child) => this.addSelfAndDescendants(child));
    }
}

/**
 * An abstract component that provides the logic to filter a {@link KnowledgeAreaTreeComponent} by competency title and knowledge area.
 * One way to set such filters is the {@link StandardizedCompetencyFilterComponent}
 *
 */
@Component({
    template: '',
})
export abstract class StandardizedCompetencyFilterPageComponent {
    protected knowledgeAreaFilter?: KnowledgeAreaDTO;
    protected competencyTitleFilter = '';

    protected knowledgeAreasForSelect: KnowledgeAreaDTO[] = [];
    /**
     * A map of id -> KnowledgeAreaForTree. Contains all knowledge areas of the tree structure.
     * <p>
     * <b>Make sure not to remove any or to replace them with copies!</b>
     */
    protected knowledgeAreaMap = new Map<number, KnowledgeAreaForTree>();

    // data for the tree structure: the mutable domain data plus the derived PrimeNG TreeNode[] consumed by `<p-tree>`
    // when the data reference changes, harvest any manual expansion the user toggled on `<p-tree>` first, then rebuild
    protected dataSource = new KnowledgeAreaTreeDataSource(() => {
        this.tree.harvestLiveExpansion();
        this.rebuildTreeNodes();
    });
    /** The PrimeNG `TreeNode[]` rendered by the tree component. Rebuilt whenever the data or the expansion state changes. */
    protected readonly treeNodes = signal<KnowledgeAreaTreeNode[]>([]);

    /**
     * The in-house tree control replacing the previous `MatTree`. It tracks expansion state and is the source for the
     * expansion flags of the rebuilt {@link treeNodes}.
     */
    protected readonly tree = new KnowledgeAreaTreeControl(
        () => this.dataSource.data,
        () => this.treeNodes(),
        () => this.rebuildTreeNodes(),
    );

    /**
     * Rebuilds the PrimeNG {@link TreeNode} array from the current data, applying the visibility filter and the current
     * expansion state tracked by {@link tree}.
     */
    protected rebuildTreeNodes(): void {
        this.treeNodes.set(convertToTreeNodes(this.dataSource.data, (id) => this.tree.isExpandedById(id)));
    }

    /**
     * Filters out all knowledge areas except for the one specified in the {@link knowledgeAreaFilter} and its direct ancestors.
     * If the filter is empty, all knowledge areas are shown again
     *
     * @param knowledgeAreaFilter the knowledge area to filter by (or undefined)
     * @param forceRefresh if the filter should be applied even if it did not change
     */
    protected filterByKnowledgeArea(knowledgeAreaFilter: KnowledgeAreaDTO | undefined, forceRefresh = true) {
        if (knowledgeAreaFilter?.id === this.knowledgeAreaFilter?.id && !forceRefresh) {
            return;
        }

        this.knowledgeAreaFilter = knowledgeAreaFilter;
        const filteredKnowledgeArea = this.getKnowledgeAreaByIdIfExists(this.knowledgeAreaFilter?.id);
        if (!filteredKnowledgeArea) {
            this.setVisibilityOfAllKnowledgeAreas(true);
        } else {
            this.setVisibilityOfAllKnowledgeAreas(false);
            this.setVisibilityOfSelfAndDescendants(filteredKnowledgeArea, true);
            this.setVisibleAndExpandSelfAndAncestors(filteredKnowledgeArea);
        }
        this.rebuildTreeNodes();
    }

    /**
     * Filters standardized competencies to only display the ones with titles containing the {@link competencyTitleFilter}.
     * Expands all knowledge areas containing matches (and their direct ancestors) to display these matches.
     * If the filter is empty all competencies are shown again.
     *
     * @param competencyTitleFilter the new title filter
     */
    protected filterByCompetencyTitle(competencyTitleFilter: string) {
        if (competencyTitleFilter === this.competencyTitleFilter) {
            return;
        }
        this.competencyTitleFilter = competencyTitleFilter;
        const trimmedFilter = this.competencyTitleFilter?.trim();

        if (!trimmedFilter) {
            this.setVisibilityOfAllCompetencies(true);
        } else {
            this.tree.collapseAll();
            this.dataSource.data.forEach((knowledgeArea) => this.filterCompetenciesForSelfAndChildren(knowledgeArea, trimmedFilter));
        }
        this.rebuildTreeNodes();
    }

    /**
     * Recursively filters standardized competencies of a knowledge area and its descendants. Only competencies with titles matching the given filter are kept visible.
     * If the knowledge area or one of its descendants contains a match, expands itself.
     *
     * @param knowledgeArea the knowledge area to filter
     * @param filter the filter string. **It is expected to be not empty!**
     * @private
     */
    private filterCompetenciesForSelfAndChildren(knowledgeArea: KnowledgeAreaForTree, filter: string) {
        let hasMatch = false;
        for (const competency of knowledgeArea.competencies ?? []) {
            if (this.competencyMatchesFilter(competency, filter)) {
                hasMatch = true;
                competency.isVisible = true;
            } else {
                competency.isVisible = false;
            }
        }
        for (const child of knowledgeArea.children ?? []) {
            if (this.filterCompetenciesForSelfAndChildren(child, filter)) {
                hasMatch = true;
            }
        }
        if (hasMatch) {
            this.tree.expand(knowledgeArea);
        }
        return hasMatch;
    }

    /**
     * Checks if the title of a competency matches a filter.
     *
     * @param competency the competency to check
     * @param filter the filter string **It is expected to be not empty!**
     * @private
     */
    protected competencyMatchesFilter(competency: StandardizedCompetencyDTO, filter: string) {
        if (!competency.title) {
            return false;
        }

        const titleLower = competency.title.toLowerCase();
        const filterLower = filter.toLowerCase();

        return titleLower.includes(filterLower);
    }

    // utility functions to set the visibility of tree objects

    /**
     * Recursively sets visible and expands a knowledge area aswell as all its ancestors.
     * This guarantees that it shows up as expanded in the tree structure, even when it is nested.
     *
     * @param knowledgeArea the knowledge area to set visible
     * @private
     */
    private setVisibleAndExpandSelfAndAncestors(knowledgeArea: KnowledgeAreaForTree) {
        knowledgeArea.isVisible = true;
        this.tree.expand(knowledgeArea);
        const parent = this.getKnowledgeAreaByIdIfExists(knowledgeArea.parentId);
        if (parent) {
            this.setVisibleAndExpandSelfAndAncestors(parent);
        }
    }

    /**
     * Recursively sets visibility of a knowledge area as well as all its descendants.
     *
     * @param knowledgeArea the knowledge area to set visible
     * @param isVisible if the knowledge areas should be set visible
     * @private
     */
    private setVisibilityOfSelfAndDescendants(knowledgeArea: KnowledgeAreaForTree, isVisible: boolean) {
        knowledgeArea.isVisible = true;
        knowledgeArea.children?.forEach((knowledgeArea) => this.setVisibilityOfSelfAndDescendants(knowledgeArea, isVisible));
    }

    private setVisibilityOfAllKnowledgeAreas(isVisible: boolean) {
        this.knowledgeAreaMap.forEach((knowledgeArea) => (knowledgeArea.isVisible = isVisible));
    }

    private setVisibilityOfAllCompetencies(isVisible: boolean) {
        for (const knowledgeArea of this.knowledgeAreaMap.values()) {
            knowledgeArea.competencies?.forEach((competency) => (competency.isVisible = isVisible));
        }
    }

    // Utility functions
    protected getKnowledgeAreaByIdIfExists(id: number | undefined) {
        if (id === undefined) {
            return undefined;
        }
        return this.knowledgeAreaMap.get(id);
    }

    // Functions to initialize data structures

    /**
     * Recursively adds a knowledge area and its descendants to the {@link knowledgeAreaMap}
     *
     * @param knowledgeArea the knowledge area to add
     * @private
     */
    protected addSelfAndDescendantsToMap(knowledgeArea: KnowledgeAreaForTree) {
        if (knowledgeArea.id !== undefined) {
            this.knowledgeAreaMap.set(knowledgeArea.id, knowledgeArea);
        }
        for (const child of knowledgeArea.children ?? []) {
            this.addSelfAndDescendantsToMap(child);
        }
    }

    /**
     * Recursively adds a knowledge area and its descendants to the {@link knowledgeAreasForSelect} array
     *
     * @param knowledgeArea
     * @private
     */
    protected addSelfAndDescendantsToSelectArray(knowledgeArea: KnowledgeAreaForTree) {
        this.knowledgeAreasForSelect.push({
            id: knowledgeArea.id,
            title: '\xa0'.repeat(knowledgeArea.level * 2) + knowledgeArea.title,
        });
        for (const child of knowledgeArea.children ?? []) {
            this.addSelfAndDescendantsToSelectArray(child);
        }
    }
}
