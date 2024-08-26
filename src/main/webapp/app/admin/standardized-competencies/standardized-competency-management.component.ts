import { ChangeDetectionStrategy, ChangeDetectorRef, Component, HostListener, OnDestroy, OnInit } from '@angular/core';
import { faChevronRight, faDownLeftAndUpRightToCenter, faEye, faFileExport, faFileImport, faPlus, faUpRightAndDownLeftFromCenter } from '@fortawesome/free-solid-svg-icons';
import {
    KnowledgeAreaDTO,
    KnowledgeAreaForTree,
    Source,
    StandardizedCompetencyDTO,
    convertToKnowledgeAreaForTree,
    convertToStandardizedCompetencyForTree,
} from 'app/entities/competency/standardized-competency.model';
import { onError } from 'app/shared/util/global.utils';
import { AdminStandardizedCompetencyService } from 'app/admin/standardized-competencies/admin-standardized-competency.service';
import { HttpErrorResponse } from '@angular/common/http';
import { AlertService } from 'app/core/util/alert.service';
import { Subject, forkJoin, map } from 'rxjs';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { ConfirmAutofocusModalComponent } from 'app/shared/components/confirm-autofocus-modal.component';
import { getIcon } from 'app/entities/competency.model';
import { ButtonSize, ButtonType } from 'app/shared/components/button.component';
import { TranslateService } from '@ngx-translate/core';
import { StandardizedCompetencyFilterPageComponent } from 'app/shared/standardized-competencies/standardized-competency-filter-page.component';
import { ComponentCanDeactivate } from 'app/shared/guard/can-deactivate.model';
import { StandardizedCompetencyService } from 'app/shared/standardized-competencies/standardized-competency.service';
import { DocumentationType } from 'app/shared/components/documentation-button/documentation-button.component';

@Component({
    selector: 'jhi-standardized-competency-management',
    templateUrl: './standardized-competency-management.component.html',
    styleUrls: ['standardized-competency-management.component.scss'],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class StandardizedCompetencyManagementComponent extends StandardizedCompetencyFilterPageComponent implements OnInit, OnDestroy, ComponentCanDeactivate {
    protected isLoading = false;
    // true if a competency is getting edited in the detail component
    protected isEditing = false;
    // the competency displayed in the detail component
    protected selectedCompetency?: StandardizedCompetencyDTO;
    // the knowledge area displayed in the detail component
    protected selectedKnowledgeArea?: KnowledgeAreaDTO;
    // the list of sources used for the select in the detail component
    protected sourcesForSelect: Source[] = [];

    // observable for the error button
    private dialogErrorSource = new Subject<string>();
    protected dialogError = this.dialogErrorSource.asObservable();

    // Icons
    protected readonly faChevronRight = faChevronRight;
    protected readonly faPlus = faPlus;
    protected readonly faMinimize = faDownLeftAndUpRightToCenter;
    protected readonly faMaximize = faUpRightAndDownLeftFromCenter;
    protected readonly faEye = faEye;
    protected readonly faFileImport = faFileImport;
    protected readonly faFileExport = faFileExport;
    // Other constants for template
    protected readonly ButtonType = ButtonType;
    protected readonly ButtonSize = ButtonSize;
    protected readonly getIcon = getIcon;
    readonly documentationType: DocumentationType = 'StandardizedCompetencies';

    constructor(
        private adminStandardizedCompetencyService: AdminStandardizedCompetencyService,
        private standardizedCompetencyService: StandardizedCompetencyService,
        private alertService: AlertService,
        private modalService: NgbModal,
        private translateService: TranslateService,
        private changeDetectorRef: ChangeDetectorRef,
    ) {
        super();
    }

    ngOnInit() {
        this.isLoading = true;
        const getKnowledgeAreasObservable = this.standardizedCompetencyService.getAllForTreeView();
        const getSourcesObservable = this.standardizedCompetencyService.getSources();
        forkJoin([getKnowledgeAreasObservable, getSourcesObservable]).subscribe({
            next: ([knowledgeAreasResponse, sourcesResponse]) => {
                this.sourcesForSelect = sourcesResponse.body!;

                const knowledgeAreas = knowledgeAreasResponse.body!;
                const knowledgeAreasForTree = knowledgeAreas.map((knowledgeArea) => convertToKnowledgeAreaForTree(knowledgeArea));
                this.dataSource.data = knowledgeAreasForTree;
                this.treeControl.dataNodes = knowledgeAreasForTree;
                knowledgeAreasForTree.forEach((knowledgeArea) => {
                    this.addSelfAndDescendantsToMap(knowledgeArea);
                    this.addSelfAndDescendantsToSelectArray(knowledgeArea);
                });

                this.isLoading = false;
                this.changeDetectorRef.detectChanges();
            },
            error: (errorResponse: HttpErrorResponse) => onError(this.alertService, errorResponse),
        });
    }

    ngOnDestroy(): void {
        this.dialogErrorSource.unsubscribe();
    }

    exportStandardizedCompetencyCatalog() {
        const url = `/api/admin/standardized-competencies/export`;
        window.open(url, '_blank');
    }

    // methods handling the knowledge area detail component
    openNewKnowledgeArea(parentId?: number) {
        const newKnowledgeArea: KnowledgeAreaDTO = {
            parentId: parentId,
        };
        this.setSelectedKnowledgeAreaAndEditing(newKnowledgeArea, true);
    }

    selectKnowledgeArea(knowledgeArea: KnowledgeAreaDTO) {
        if (this.selectedKnowledgeArea?.id === knowledgeArea.id) {
            return;
        }
        this.setSelectedKnowledgeAreaAndEditing(knowledgeArea, false);
    }

    closeKnowledgeArea() {
        this.setSelectedKnowledgeAreaAndEditing(undefined, false);
    }

    deleteKnowledgeArea(id: number) {
        const deletedKnowledgeArea = this.selectedKnowledgeArea?.id === id ? this.selectedKnowledgeArea : undefined;
        this.adminStandardizedCompetencyService.deleteKnowledgeArea(id).subscribe({
            next: () => {
                this.alertService.success('artemisApp.knowledgeArea.manage.successAlerts.delete', { title: this.selectedKnowledgeArea?.title });
                this.dialogErrorSource.next('');
                this.updateAfterDeleteKnowledgeArea(deletedKnowledgeArea);
                // close the detail component if it is still open
                if (this.selectedKnowledgeArea?.id === id) {
                    this.isEditing = false;
                    this.selectedKnowledgeArea = undefined;
                }
                this.changeDetectorRef.detectChanges();
            },
            error: (error: HttpErrorResponse) => this.dialogErrorSource.next(error.message),
        });
    }

    /**
     * Saves the given knowledge area by either creating a new one (if it has no id) or updating an existing one
     * @param knowledgeArea the knowledgeArea to save
     */
    saveKnowledgeArea(knowledgeArea: KnowledgeAreaDTO) {
        if (knowledgeArea.id === undefined) {
            this.adminStandardizedCompetencyService
                .createKnowledgeArea(knowledgeArea)
                .pipe(map((response) => response.body!))
                .subscribe({
                    next: (resultKnowledgeArea) => {
                        this.alertService.success('artemisApp.knowledgeArea.manage.successAlerts.create', { title: resultKnowledgeArea.title });
                        this.updateAfterCreateKnowledgeArea(resultKnowledgeArea);
                        // update the detail view if no other was opened
                        if (!(this.selectedKnowledgeArea?.id || this.selectedCompetency) && !this.isEditing) {
                            this.selectedKnowledgeArea = resultKnowledgeArea;
                        }
                        this.changeDetectorRef.detectChanges();
                    },
                    error: (error: HttpErrorResponse) => onError(this.alertService, error),
                });
        } else {
            this.adminStandardizedCompetencyService
                .updateKnowledgeArea(knowledgeArea)
                .pipe(map((response) => response.body!))
                .subscribe({
                    next: (resultKnowledgeArea) => {
                        this.alertService.success('artemisApp.knowledgeArea.manage.successAlerts.update', { title: resultKnowledgeArea.title });
                        this.updateAfterUpdateKnowledgeArea(resultKnowledgeArea);
                        // update the detail view if it is still open
                        if (resultKnowledgeArea.id === this.selectedKnowledgeArea?.id) {
                            this.selectedKnowledgeArea = resultKnowledgeArea;
                        }
                        this.changeDetectorRef.detectChanges();
                    },
                    error: (error: HttpErrorResponse) => onError(this.alertService, error),
                });
        }
    }

    // methods handling the competency detail component

    openNewCompetency(knowledgeAreaId: number) {
        const newCompetency: StandardizedCompetencyDTO = {
            knowledgeAreaId: knowledgeAreaId,
        };
        this.setSelectedCompetencyAndEditing(newCompetency, true);
    }

    selectCompetency(competency: StandardizedCompetencyDTO) {
        if (this.selectedCompetency?.id === competency.id) {
            return;
        }
        this.setSelectedCompetencyAndEditing(competency, false);
    }

    closeCompetency() {
        this.setSelectedCompetencyAndEditing(undefined, false);
    }

    deleteCompetency(id: number) {
        const deletedCompetency = this.selectedCompetency?.id === id ? this.selectedCompetency : undefined;
        this.adminStandardizedCompetencyService.deleteStandardizedCompetency(id).subscribe({
            next: () => {
                this.alertService.success('artemisApp.standardizedCompetency.manage.successAlerts.delete', { title: deletedCompetency?.title });
                this.updateAfterDeleteCompetency(deletedCompetency);
                this.dialogErrorSource.next('');
                // close the detail component if it is still open
                if (id === this.selectedCompetency?.id) {
                    this.isEditing = false;
                    this.selectedCompetency = undefined;
                }
                this.changeDetectorRef.detectChanges();
            },
            error: (error: HttpErrorResponse) => this.dialogErrorSource.next(error.message),
        });
    }

    /**
     * Saves the given standardized competency by either creating a new one (if it has no id) or updating an existing one
     * @param competency the competency to save
     */
    saveCompetency(competency: StandardizedCompetencyDTO) {
        if (competency.id === undefined) {
            this.adminStandardizedCompetencyService
                .createStandardizedCompetency(competency)
                .pipe(map((response) => response.body!))
                .subscribe({
                    next: (resultCompetency) => {
                        this.alertService.success('artemisApp.standardizedCompetency.manage.successAlerts.create', { title: resultCompetency.title });
                        this.updateAfterCreateCompetency(resultCompetency);
                        // update the detail view if no other was opened
                        if (!(this.selectedCompetency?.id || this.selectedKnowledgeArea) && !this.isEditing) {
                            this.selectedCompetency = resultCompetency;
                        }
                        this.changeDetectorRef.detectChanges();
                    },
                    error: (error: HttpErrorResponse) => onError(this.alertService, error),
                });
        } else {
            // save the previous competency values to update the tree afterward
            const previousCompetency = this.selectedCompetency?.id === competency.id ? this.selectedCompetency : undefined;
            this.adminStandardizedCompetencyService
                .updateStandardizedCompetency(competency)
                .pipe(map((response) => response.body!))
                .subscribe({
                    next: (resultCompetency) => {
                        this.alertService.success('artemisApp.standardizedCompetency.manage.successAlerts.update', { title: resultCompetency.title });
                        this.updateAfterUpdateCompetency(resultCompetency, previousCompetency);
                        // update the detail view if it is still open
                        if (resultCompetency.id === this.selectedCompetency?.id) {
                            this.selectedCompetency = resultCompetency;
                        }
                        this.changeDetectorRef.detectChanges();
                    },
                    error: (error: HttpErrorResponse) => onError(this.alertService, error),
                });
        }
    }

    private updateAfterDeleteKnowledgeArea(knowledgeArea: KnowledgeAreaDTO | undefined) {
        const parent = this.getKnowledgeAreaByIdIfExists(knowledgeArea?.parentId);
        if (!knowledgeArea || (!parent && knowledgeArea.parentId !== undefined)) {
            this.alertService.error('artemisApp.standardizedCompetency.manage.updateTreeError');
            return;
        }

        if (parent) {
            parent.children = parent.children?.filter((ka) => ka.id !== knowledgeArea.id);
            this.refreshTree();
        } else {
            this.dataSource.data = this.dataSource.data.filter((ka) => ka.id !== knowledgeArea.id);
            this.treeControl.dataNodes = this.dataSource.data;
        }
        const descendantIds = this.getIdsOfSelfAndAllDescendants(knowledgeArea);
        descendantIds.forEach((id) => this.knowledgeAreaMap.delete(id));
        this.knowledgeAreasForSelect = this.knowledgeAreasForSelect.filter((ka) => ka.id === undefined || !descendantIds.includes(ka.id));
    }

    private updateAfterCreateKnowledgeArea(knowledgeArea: KnowledgeAreaDTO) {
        const parent = this.getKnowledgeAreaByIdIfExists(knowledgeArea.parentId);
        if (!parent && knowledgeArea.parentId !== undefined) {
            this.alertService.error('artemisApp.standardizedCompetency.manage.updateTreeError');
        }

        const isVisible = !this.knowledgeAreaFilter || this.isAncestorOf(this.knowledgeAreaFilter, knowledgeArea);
        const level = parent ? parent.level + 1 : 0;
        const knowledgeAreaForTree: KnowledgeAreaForTree = convertToKnowledgeAreaForTree(knowledgeArea, isVisible, level);

        if (parent) {
            parent.children = this.insertBasedOnTitle(knowledgeAreaForTree, parent.children);
            this.refreshTree();
        } else {
            this.dataSource.data = this.insertBasedOnTitle(knowledgeAreaForTree, this.dataSource.data);
            this.treeControl.dataNodes = this.dataSource.data;
        }

        this.knowledgeAreaMap.set(knowledgeArea.id!, knowledgeAreaForTree);
        this.knowledgeAreasForSelect = [];
        this.dataSource.data.forEach((knowledgeArea) => this.addSelfAndDescendantsToSelectArray(knowledgeArea));
    }

    private updateAfterUpdateKnowledgeArea(knowledgeArea: KnowledgeAreaDTO) {
        const previousKnowledgeArea = this.getKnowledgeAreaByIdIfExists(knowledgeArea.id);
        if (!previousKnowledgeArea) {
            this.alertService.error('artemisApp.standardizedCompetency.manage.updateTreeError');
            return;
        }

        const parent = this.getKnowledgeAreaByIdIfExists(knowledgeArea.parentId);
        const previousParent = this.getKnowledgeAreaByIdIfExists(previousKnowledgeArea.parentId);
        // fail if a parent exists but could not be found
        if ((!parent && knowledgeArea.parentId !== undefined) || (!previousParent && previousKnowledgeArea.parentId !== undefined)) {
            this.alertService.error('artemisApp.standardizedCompetency.manage.updateTreeError');
            return;
        }
        // set children and competencies to previous values as we do not get all descendants from the server
        const knowledgeAreaForTree: KnowledgeAreaForTree = {
            ...knowledgeArea,
            level: parent ? parent.level + 1 : 0,
            isVisible: true,
            children: previousKnowledgeArea.children,
            competencies: previousKnowledgeArea.competencies,
        };
        // update level of descendants
        this.updateLevelOfSelfAndDescendants(knowledgeAreaForTree, knowledgeAreaForTree.level);

        if (previousParent) {
            previousParent.children = previousParent.children?.filter((ka) => ka.id !== knowledgeArea.id);
        } else {
            this.dataSource.data = this.dataSource.data?.filter((ka) => ka.id !== knowledgeArea.id);
        }
        if (parent) {
            parent.children = this.insertBasedOnTitle(knowledgeAreaForTree, parent.children);
        } else {
            this.dataSource.data = this.insertBasedOnTitle(knowledgeAreaForTree, this.dataSource.data);
        }

        this.knowledgeAreaMap.set(knowledgeArea.id!, knowledgeAreaForTree);
        this.knowledgeAreasForSelect = [];
        this.dataSource.data.forEach((knowledgeArea) => this.addSelfAndDescendantsToSelectArray(knowledgeArea));
        this.treeControl.dataNodes = this.dataSource.data;

        // refresh tree if dataSource.data was not modified directly
        if (previousParent || parent) {
            this.refreshTree();
        }
        // filter again if the knowledge area was moved.
        if (previousParent?.id !== parent?.id && this.knowledgeAreaFilter) {
            this.filterByKnowledgeArea(this.knowledgeAreaFilter);
        }
    }

    // functions that update the tree structure in the user interface

    /**
     * Updates the tree after deleting the {@link selectedCompetency} by removing it from its parent knowledge area.
     * Shows an error alert if the re-structuring fails.
     *
     * @private
     */
    private updateAfterDeleteCompetency(competency: StandardizedCompetencyDTO | undefined) {
        const knowledgeArea = this.getKnowledgeAreaByIdIfExists(competency?.knowledgeAreaId);
        if (!competency || !knowledgeArea) {
            this.alertService.error('artemisApp.standardizedCompetency.manage.updateTreeError');
            return;
        }
        knowledgeArea.competencies = knowledgeArea.competencies?.filter((c) => c.id !== competency.id);
    }

    /**
     * Updates the tree after creating the given competency by inserting it into its parent knowledge area.
     * Shows an error alert if the re-structuring fails.
     *
     * @param competency the new competency
     * @private
     */
    private updateAfterCreateCompetency(competency: StandardizedCompetencyDTO) {
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
     * @param previousCompetency the old competency values
     * @private
     */
    private updateAfterUpdateCompetency(competency: StandardizedCompetencyDTO, previousCompetency: StandardizedCompetencyDTO | undefined) {
        if (!previousCompetency) {
            this.alertService.error('artemisApp.standardizedCompetency.manage.updateTreeError');
            return;
        }
        const competencyForTree = convertToStandardizedCompetencyForTree(competency, this.shouldBeVisible(competency));
        const previousKnowledgeArea = this.getKnowledgeAreaByIdIfExists(previousCompetency?.knowledgeAreaId);
        if (previousKnowledgeArea?.competencies === undefined) {
            this.alertService.error('artemisApp.standardizedCompetency.manage.updateTreeError');
            return;
        }

        // if the knowledge area changed, move the competency to the new knowledge area
        if (competency.knowledgeAreaId !== previousCompetency.knowledgeAreaId) {
            const newKnowledgeArea = this.getKnowledgeAreaByIdIfExists(competency.knowledgeAreaId);
            if (newKnowledgeArea === undefined) {
                this.alertService.error('artemisApp.standardizedCompetency.manage.updateTreeError');
                return;
            }
            previousKnowledgeArea.competencies = previousKnowledgeArea.competencies.filter((c) => c.id !== competency.id);
            newKnowledgeArea.competencies = (newKnowledgeArea.competencies ?? []).concat(competencyForTree);
        } else {
            // if the knowledge area stayed the same insert the new competency/replace the existing one
            const index = previousKnowledgeArea.competencies.findIndex((c) => c.id === competency.id);
            if (index === -1) {
                this.alertService.error('artemisApp.standardizedCompetency.manage.updateTreeError');
                return;
            }
            previousKnowledgeArea.competencies.splice(index, 1, competencyForTree);
        }
    }

    // utility functions

    private openCancelModal(title: string, entityType: 'standardizedCompetency' | 'knowledgeArea', callback: () => void) {
        const modalRef = this.modalService.open(ConfirmAutofocusModalComponent, { keyboard: true, size: 'md' });
        modalRef.componentInstance.textIsMarkdown = true;
        modalRef.componentInstance.title = `artemisApp.${entityType}.manage.cancelModal.title`;
        modalRef.componentInstance.text = this.translateService.instant(`artemisApp.${entityType}.manage.cancelModal.text`, { title: title });
        modalRef.result.then(() => callback());
    }

    private refreshTree() {
        const _data = this.dataSource.data;
        this.dataSource.data = [];
        this.dataSource.data = _data;
    }

    private isAncestorOf(ancestor: KnowledgeAreaDTO, knowledgeArea: KnowledgeAreaDTO): boolean {
        if (ancestor.id === knowledgeArea.id) {
            return true;
        }
        const parent = this.getKnowledgeAreaByIdIfExists(knowledgeArea.parentId);
        if (!parent) {
            return false;
        }
        return this.isAncestorOf(ancestor, parent);
    }

    /**
     * Gets the id of a knowledge area and all its descendants
     *
     * @param knowledgeArea the knowledge area
     * @private
     */
    private getIdsOfSelfAndAllDescendants(knowledgeArea: KnowledgeAreaDTO): number[] {
        const childrenIds = (knowledgeArea.children ?? []).map((child) => this.getIdsOfSelfAndAllDescendants(child)).flat();
        if (knowledgeArea.id !== undefined) {
            return childrenIds.concat(knowledgeArea.id);
        }
        return childrenIds;
    }

    /**
     * Updates the level of the given knowledge area to the new level. Also updates and all its descendants to match the new level.
     *
     * @param knowledgeArea the knowledge area to update
     * @param newLevel the new level to set
     * @private
     */
    private updateLevelOfSelfAndDescendants(knowledgeArea: KnowledgeAreaForTree, newLevel: number) {
        knowledgeArea.level = newLevel;
        knowledgeArea.children?.forEach((child) => this.updateLevelOfSelfAndDescendants(child, newLevel + 1));
    }

    /**
     * Inserts the given knowledgeArea into an array that is sorted alphabetically (ascending)
     *
     * @param knowledgeArea the knowledge area to insert
     * @param array the sorted array
     * @private
     */
    private insertBasedOnTitle(knowledgeArea: KnowledgeAreaForTree, array: KnowledgeAreaForTree[] | undefined) {
        if (!knowledgeArea.title || !array) {
            return (array ?? []).concat(knowledgeArea);
        }

        // find the index of the first knowledge area with a "larger" title
        const insertIndex = array.findIndex((ka) => (ka.title ? ka.title.localeCompare(knowledgeArea.title!) > -1 : false));
        if (insertIndex === -1) {
            return array.concat(knowledgeArea);
        }

        array.splice(insertIndex, 0, knowledgeArea);
        return array;
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

    // utility functions to handle the detail component

    /**
     * Sets the selectedKnowledgeArea and isEditing properties. Also sets selectedCompetency to undefined
     *
     * @param knowledgeArea the new value for selectedKnowledgeArea
     * @param isEditing the new value for isEditing
     * @private
     */
    private setSelectedKnowledgeAreaAndEditing(knowledgeArea: KnowledgeAreaDTO | undefined, isEditing: boolean) {
        this.setSelectedObjectsAndEditing(undefined, knowledgeArea, isEditing);
    }

    /**
     * Sets the selectedCompetency and isEditing properties. Also sets selectedKnowledgeArea to undefined
     *
     * @param competency the new value for selectedCompetency
     * @param isEditing the new value for isEditing
     * @private
     */
    private setSelectedCompetencyAndEditing(competency: StandardizedCompetencyDTO | undefined, isEditing: boolean) {
        this.setSelectedObjectsAndEditing(competency, undefined, isEditing);
    }

    /**
     * **Never call this method directly!** Always use {@link setSelectedCompetencyAndEditing} or {@link setSelectedKnowledgeAreaAndEditing}
     * Sets the selected competency and knowledge area, as well as the isEditing property.
     *
     * @param competency the new value for selectedCompetency
     * @param knowledgeArea the new value for selectedKnowledgeArea
     * @param isEditing the new value for isEditing
     * @private
     */
    private setSelectedObjectsAndEditing(competency: StandardizedCompetencyDTO | undefined, knowledgeArea: KnowledgeAreaDTO | undefined, isEditing: boolean) {
        if ((this.selectedCompetency || this.selectedKnowledgeArea) && this.isEditing) {
            const title = this.selectedCompetency?.title ?? this.selectedKnowledgeArea?.title ?? '';
            const entityType = this.selectedCompetency ? 'standardizedCompetency' : 'knowledgeArea';
            this.openCancelModal(title, entityType, () => {
                this.isEditing = isEditing;
                this.selectedCompetency = competency;
                this.selectedKnowledgeArea = knowledgeArea;
            });
        } else {
            this.isEditing = isEditing;
            this.selectedCompetency = competency;
            this.selectedKnowledgeArea = knowledgeArea;
        }
    }

    // Functions that handle unsaved changes

    /**
     * Only allow to leave page after submitting or if no pending changes exist
     */
    canDeactivate() {
        return !this.isEditing;
    }

    get canDeactivateWarning(): string {
        return this.translateService.instant('pendingChanges');
    }

    /**
     * Displays the alert for confirming refreshing or closing the page if there are unsaved changes
     */
    @HostListener('window:beforeunload', ['$event'])
    unloadNotification(event: any) {
        if (!this.canDeactivate()) {
            event.returnValue = this.canDeactivateWarning;
        }
    }
}
