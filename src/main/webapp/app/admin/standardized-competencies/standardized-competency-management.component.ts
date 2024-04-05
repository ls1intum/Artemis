import { Component, OnDestroy, OnInit } from '@angular/core';
import { NestedTreeControl } from '@angular/cdk/tree';
import { MatTreeNestedDataSource } from '@angular/material/tree';
import { faChevronRight, faDownLeftAndUpRightToCenter, faPlus, faUpRightAndDownLeftFromCenter } from '@fortawesome/free-solid-svg-icons';
import {
    KnowledgeArea,
    KnowledgeAreaDTO,
    KnowledgeAreaForTree,
    StandardizedCompetencyDTO,
    convertToStandardizedCompetency,
    convertToStandardizedCompetencyDTO,
    convertToStandardizedCompetencyForTree,
} from 'app/entities/competency/standardized-competency.model';
import { onError } from 'app/shared/util/global.utils';
import { AdminStandardizedCompetencyService } from 'app/admin/standardized-competencies/admin-standardized-competency.service';
import { StandardizedCompetencyService } from 'app/admin/standardized-competencies/standardized-competency.service';
import { HttpErrorResponse } from '@angular/common/http';
import { AlertService } from 'app/core/util/alert.service';
import { Subject, debounceTime, map } from 'rxjs';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { ConfirmAutofocusModalComponent } from 'app/shared/components/confirm-autofocus-modal.component';
import { getIcon } from 'app/entities/competency.model';
import { ButtonSize, ButtonType } from 'app/shared/components/button.component';
import { TranslateService } from '@ngx-translate/core';

@Component({
    selector: 'jhi-standardized-competency-management',
    templateUrl: './standardized-competency-management.component.html',
    styleUrls: ['standardized-competency-management.component.scss'],
})
export class StandardizedCompetencyManagementComponent implements OnInit, OnDestroy {
    protected isLoading = false;
    //true if a competency is getting edited in the detail component
    protected isEditing = false;
    //the competency to hand to the detail component
    protected selectedCompetency?: StandardizedCompetencyDTO;
    protected knowledgeAreaFilter?: KnowledgeAreaDTO;
    protected competencyTitleFilter?: string;
    protected titleFilterSubject = new Subject<void>();

    protected knowledgeAreasForSelect: KnowledgeAreaDTO[] = [];
    /**
     * A map of id -> KnowledgeAreaForTree. Contains all knowledge areas of the tree structure.
     * <p>
     * <b>Make sure not to remove any or to replace them with copies!</b>
     */
    private knowledgeAreaMap = new Map<number, KnowledgeAreaForTree>();

    //data and control for the tree structure
    protected dataSource = new MatTreeNestedDataSource<KnowledgeAreaForTree>();
    protected treeControl = new NestedTreeControl<KnowledgeAreaForTree>((node) => node.children);
    //observable for the error button
    private dialogErrorSource = new Subject<string>();
    protected dialogError = this.dialogErrorSource.asObservable();

    //Icons
    protected readonly faChevronRight = faChevronRight;
    protected readonly faPlus = faPlus;
    protected readonly faMinimize = faDownLeftAndUpRightToCenter;
    protected readonly faMaximize = faUpRightAndDownLeftFromCenter;
    //Other constants for template
    protected readonly ButtonType = ButtonType;
    protected readonly ButtonSize = ButtonSize;
    protected readonly getIcon = getIcon;
    readonly trackBy = (_: number, node: KnowledgeAreaDTO) => node.id;

    constructor(
        private adminStandardizedCompetencyService: AdminStandardizedCompetencyService,
        private standardizedCompetencyService: StandardizedCompetencyService,
        private alertService: AlertService,
        private modalService: NgbModal,
        private translateService: TranslateService,
    ) {}

    ngOnInit() {
        this.isLoading = true;
        this.standardizedCompetencyService
            .getAllForTreeView()
            .pipe(map((response) => response.body!))
            .subscribe({
                next: (knowledgeAreas) => {
                    const knowledgeAreasForTree = knowledgeAreas.map((knowledgeArea) => this.getSelfAndDescendantsAsKnowledgeAreaForTree(knowledgeArea, 0));
                    this.dataSource.data = knowledgeAreasForTree;
                    this.treeControl.dataNodes = knowledgeAreasForTree;
                    knowledgeAreasForTree.forEach((knowledgeArea) => {
                        this.addSelfAndDescendantsToMap(knowledgeArea);
                        this.addSelfAndDescendantsToSelectArray(knowledgeArea);
                    });
                    this.isLoading = false;
                },
                error: (errorResponse: HttpErrorResponse) => onError(this.alertService, errorResponse),
            });
        this.titleFilterSubject.pipe(debounceTime(500)).subscribe(() => this.filterByCompetencyTitle());
    }

    ngOnDestroy(): void {
        this.dialogErrorSource.unsubscribe();
        this.titleFilterSubject.unsubscribe();
    }

    //filter functions

    /**
     * Filters out all knowledge areas except for the one specified in the {@link knowledgeAreaFilter} and its direct ancestors.
     * If the filter is empty, all knowledge areas are shown again
     */
    filterByKnowledgeArea() {
        const filteredKnowledgeArea = this.getKnowledgeAreaByIdIfExists(this.knowledgeAreaFilter?.id);
        if (!filteredKnowledgeArea) {
            this.setVisibilityOfAllKnowledgeAreas(true);
        } else {
            this.setVisibilityOfAllKnowledgeAreas(false);
            this.setVisibleAndExpandSelfAndAncestors(filteredKnowledgeArea);
            this.setVisibleSelfAndDescendants(filteredKnowledgeArea);
        }
    }

    /**
     * Filters standardized competencies to only display the ones with titles containing the {@link competencyTitleFilter}.
     * Expands all knowledge areas containing matches (and their direct ancestors) to display these matches.
     * If the filter is empty all competencies are shown again.
     */
    filterByCompetencyTitle() {
        const trimmedFilter = this.competencyTitleFilter?.trim();

        if (!trimmedFilter) {
            this.setVisiblityOfAllCompetencies(true);
        } else {
            this.treeControl.collapseAll();
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
            this.treeControl.expand(knowledgeArea);
        }
        return hasMatch;
    }

    //Callback methods for the competency detail component

    openNewCompetency(knowledgeArea?: KnowledgeArea) {
        const newCompetency: StandardizedCompetencyDTO = {
            knowledgeAreaId: knowledgeArea?.id,
        };
        if (this.isEditing) {
            this.openCancelModal(this.selectedCompetency?.title ?? '', () => {
                this.isEditing = true;
                this.selectedCompetency = newCompetency;
            });
        } else {
            this.isEditing = true;
            this.selectedCompetency = newCompetency;
        }
    }

    selectCompetency(competency: StandardizedCompetencyDTO) {
        if (this.selectedCompetency?.id === competency.id) {
            return;
        }

        if (this.selectedCompetency && this.isEditing) {
            this.openCancelModal(this.selectedCompetency.title ?? '', () => {
                this.isEditing = false;
                this.selectedCompetency = competency;
            });
        } else {
            this.selectedCompetency = competency;
        }
    }

    closeCompetency() {
        if (this.isEditing) {
            this.openCancelModal(this.selectedCompetency?.title ?? '', () => {
                this.isEditing = false;
                this.selectedCompetency = undefined;
            });
        } else {
            this.isEditing = false;
            this.selectedCompetency = undefined;
        }
    }

    deleteCompetency() {
        //if the competency does not exist just close the detail component
        if (this.selectedCompetency?.id === undefined) {
            this.isEditing = false;
            this.selectedCompetency = undefined;
            return;
        }

        this.adminStandardizedCompetencyService.deleteStandardizedCompetency(this.selectedCompetency.id).subscribe({
            next: () => {
                this.alertService.success('artemisApp.standardizedCompetency.manage.successAlerts.delete', { competencyTitle: this.selectedCompetency?.title });
                this.updateTreeAfterDelete();
                this.dialogErrorSource.next('');
                //close the detail component
                this.isEditing = false;
                this.selectedCompetency = undefined;
            },
            error: (error: HttpErrorResponse) => this.dialogErrorSource.next(error.message),
        });
    }

    /**
     * Saves the given standardized competency by either creating a new one (if it has no id) or updating an existing one
     * @param competencyDTO the competency to save
     */
    saveCompetency(competencyDTO: StandardizedCompetencyDTO) {
        const competency = convertToStandardizedCompetency(competencyDTO);

        if (competency.id === undefined) {
            this.adminStandardizedCompetencyService
                .createStandardizedCompetency(competency)
                .pipe(map((response) => response.body!))
                .subscribe({
                    next: (resultCompetency) => {
                        resultCompetency = convertToStandardizedCompetencyDTO(resultCompetency);
                        this.alertService.success('artemisApp.standardizedCompetency.manage.successAlerts.create', { competencyTitle: resultCompetency.title });
                        this.updateTreeAfterCreate(resultCompetency);
                        //update the detail view
                        this.selectedCompetency = resultCompetency;
                    },
                    error: (error: HttpErrorResponse) => onError(this.alertService, error),
                });
        } else {
            this.adminStandardizedCompetencyService
                .updateStandardizedCompetency(competency)
                .pipe(map((response) => response.body!))
                .subscribe({
                    next: (resultCompetency) => {
                        resultCompetency = convertToStandardizedCompetencyDTO(resultCompetency);
                        this.alertService.success('artemisApp.standardizedCompetency.manage.successAlerts.update', { competencyTitle: resultCompetency.title });
                        this.updateTreeAfterUpdate(resultCompetency);
                        //update the detail view
                        this.selectedCompetency = resultCompetency;
                    },
                    error: (error: HttpErrorResponse) => onError(this.alertService, error),
                });
        }
    }

    //functions that update the tree structure in the user interface

    /**
     * Updates the tree after deleting the {@link selectedCompetency} by removing it from its parent knowledge area.
     * Shows an error alert if the re-structuring fails.
     *
     * @private
     */
    private updateTreeAfterDelete() {
        const knowledgeArea = this.getKnowledgeAreaByIdIfExists(this.selectedCompetency?.knowledgeAreaId);
        if (!knowledgeArea) {
            this.alertService.error('artemisApp.standardizedCompetency.manage.updateTreeError');
            return;
        }
        knowledgeArea.competencies = knowledgeArea.competencies?.filter((c) => c.id !== this.selectedCompetency?.id);
    }

    /**
     * Updates the tree after creating the given competency by inserting it into its parent knowledge area.
     * Shows an error alert if the re-structuring fails.
     *
     * @param competency the new competency
     * @private
     */
    private updateTreeAfterCreate(competency: StandardizedCompetencyDTO) {
        const competencyForTree = convertToStandardizedCompetencyForTree(competency, this.shouldBeVisible(competency));
        const knowledgeArea = this.getKnowledgeAreaByIdIfExists(competency.knowledgeAreaId);
        if (!knowledgeArea) {
            this.alertService.error('artemisApp.standardizedCompetency.manage.updateTreeError');
            return;
        }
        knowledgeArea.competencies = (knowledgeArea.competencies ?? []).concat(competencyForTree);
    }

    /**
     * Updates the tree after updating the {@link selectedCompetency} to the given competency values.
     * Shows an error alert if the re-structuring fails.
     *
     * @param competency the new competency values
     * @private
     */
    private updateTreeAfterUpdate(competency: StandardizedCompetencyDTO) {
        const competencyForTree = convertToStandardizedCompetencyForTree(competency, this.shouldBeVisible(competency));
        const previousKnowledgeArea = this.getKnowledgeAreaByIdIfExists(this.selectedCompetency?.knowledgeAreaId);
        if (previousKnowledgeArea?.competencies === undefined) {
            this.alertService.error('artemisApp.standardizedCompetency.manage.updateTreeError');
            return;
        }

        //if the knowledge area changed, move the competency to the new knowledge area
        if (competency.knowledgeAreaId !== previousKnowledgeArea.id) {
            const newKnowledgeArea = this.getKnowledgeAreaByIdIfExists(competency.knowledgeAreaId);
            if (newKnowledgeArea === undefined) {
                this.alertService.error('artemisApp.standardizedCompetency.manage.updateTreeError');
                return;
            }
            previousKnowledgeArea.competencies = previousKnowledgeArea.competencies.filter((c) => c.id !== competency.id);
            newKnowledgeArea.competencies = (newKnowledgeArea.competencies ?? []).concat(competencyForTree);
        } else {
            //if the knowledge area stayed the same insert the new competency/replace the existing one
            const index = previousKnowledgeArea.competencies.findIndex((c) => c.id === competency.id);
            if (index === -1) {
                this.alertService.error('artemisApp.standardizedCompetency.manage.updateTreeError');
                return;
            }
            previousKnowledgeArea.competencies.splice(index, 1, competencyForTree);
        }
    }

    //functions to initialize data structures

    /**
     * Recursively converts a KnowledgeAreaDTO and its descendants to KnowledgeAreaForTree objects
     *
     * @param knowledgeArea the KnowledgeAreaDTO to convert
     * @param level the initial level, defaults to 0
     * @private
     */
    private getSelfAndDescendantsAsKnowledgeAreaForTree(knowledgeArea: KnowledgeAreaDTO, level = 0): KnowledgeAreaForTree {
        const children = knowledgeArea.children?.map((child) => this.getSelfAndDescendantsAsKnowledgeAreaForTree(child, level + 1));
        const competencies = knowledgeArea.competencies?.map((competency) => convertToStandardizedCompetencyForTree(competency, true));
        return { ...knowledgeArea, children: children, competencies: competencies, level: level, isVisible: true };
    }

    /**
     * Recursively adds a knowledge area and its descendants to the {@link knowledgeAreaMap}
     *
     * @param knowledgeArea the knowledge area to add
     * @private
     */
    private addSelfAndDescendantsToMap(knowledgeArea: KnowledgeAreaForTree) {
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
    private addSelfAndDescendantsToSelectArray(knowledgeArea: KnowledgeAreaForTree) {
        this.knowledgeAreasForSelect.push({
            id: knowledgeArea.id,
            title: '\xa0'.repeat(knowledgeArea.level * 2) + knowledgeArea.title,
        });
        for (const child of knowledgeArea.children ?? []) {
            this.addSelfAndDescendantsToSelectArray(child);
        }
    }

    //utility functions

    private getKnowledgeAreaByIdIfExists(id: number | undefined) {
        if (id === undefined) {
            return undefined;
        }
        return this.knowledgeAreaMap.get(id);
    }

    private openCancelModal(entityTitle: string, callback: () => void) {
        const modalRef = this.modalService.open(ConfirmAutofocusModalComponent, { keyboard: true, size: 'md' });
        modalRef.componentInstance.title = 'artemisApp.standardizedCompetency.manage.cancelModal.title';
        modalRef.componentInstance.text = this.translateService.instant('artemisApp.standardizedCompetency.manage.cancelModal.text', { title: entityTitle });
        modalRef.result.then(() => callback());
    }

    /**
     * Recursively sets visible and expands a knowledge area aswell as all its ancestors.
     * This guarantees that it shows up as expanded in the tree structure, even when it is nested.
     *
     * @param knowledgeArea the knowledge area to set visible
     * @private
     */
    private setVisibleAndExpandSelfAndAncestors(knowledgeArea: KnowledgeAreaForTree) {
        knowledgeArea.isVisible = true;
        this.treeControl.expand(knowledgeArea);
        const parent = this.getKnowledgeAreaByIdIfExists(knowledgeArea.parentId);
        if (parent) {
            this.setVisibleAndExpandSelfAndAncestors(parent);
        }
    }

    /**
     * Recursively sets visible a knowledge area aswell as all its descendants.
     *
     * @param knowledgeArea the knowledge area to set visible
     * @private
     */
    private setVisibleSelfAndDescendants(knowledgeArea: KnowledgeAreaForTree) {
        knowledgeArea.isVisible = true;
        knowledgeArea.children?.forEach((knowledgeArea) => this.setVisibleSelfAndDescendants(knowledgeArea));
    }

    private setVisibilityOfAllKnowledgeAreas(isVisible: boolean) {
        this.knowledgeAreaMap.forEach((knowledgeArea) => (knowledgeArea.isVisible = isVisible));
    }

    private setVisiblityOfAllCompetencies(isVisible: boolean) {
        for (const knowledgeArea of this.knowledgeAreaMap.values()) {
            knowledgeArea.competencies?.forEach((competency) => (competency.isVisible = isVisible));
        }
    }

    /**
     * Checks if a competency should be visible, i.e. if its title contains the {@link competencyTitleFilter}
     *
     * @param competency the competency to check
     * @private
     */
    private shouldBeVisible(competency: StandardizedCompetencyDTO) {
        const trimmedFilter = this.competencyTitleFilter?.trim();
        if (!trimmedFilter) {
            return true;
        }
        return this.competencyMatchesFilter(competency, trimmedFilter);
    }

    /**
     * Checks if the title of a competency matches a filter.
     *
     * @param competency the competency to check
     * @param filter the filter string **It is expected to be not empty!**
     * @private
     */
    private competencyMatchesFilter(competency: StandardizedCompetencyDTO, filter: string) {
        if (!competency.title) {
            return false;
        }

        const titleLower = competency.title.toLowerCase();
        const filterLower = filter.toLowerCase();

        return titleLower.includes(filterLower);
    }
}
