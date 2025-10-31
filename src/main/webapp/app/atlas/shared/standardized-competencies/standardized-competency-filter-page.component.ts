import { Component } from '@angular/core';
import { MatTreeNestedDataSource } from '@angular/material/tree';
import { KnowledgeAreaDTO, KnowledgeAreaForTree, StandardizedCompetencyDTO } from 'app/atlas/shared/entities/standardized-competency.model';
import { KnowledgeAreaTreeComponent } from 'app/atlas/shared/standardized-competencies/knowledge-area-tree.component';

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

    // data and control for the tree structure
    protected dataSource = new MatTreeNestedDataSource<KnowledgeAreaForTree>();
    protected knowledgeAreaTree?: KnowledgeAreaTreeComponent;
    protected readonly registerKnowledgeAreaTree = (tree: KnowledgeAreaTreeComponent) => {
        this.knowledgeAreaTree = tree;
    };
    protected readonly collapseTree = () => {
        this.knowledgeAreaTree?.collapseAll();
    };
    protected readonly expandTree = () => {
        this.knowledgeAreaTree?.expandAll();
    };

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
            this.knowledgeAreaTree?.collapseAll();
            this.dataSource.data.forEach((knowledgeArea) => this.filterCompetenciesForSelfAndChildren(knowledgeArea, trimmedFilter));
        }
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
            this.knowledgeAreaTree?.expand(knowledgeArea);
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
        this.knowledgeAreaTree?.expand(knowledgeArea);
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
