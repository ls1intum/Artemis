import { Component, OnDestroy, OnInit } from '@angular/core';
import { NestedTreeControl } from '@angular/cdk/tree';
import { MatTreeNestedDataSource } from '@angular/material/tree';
import { faChevronRight, faPlus } from '@fortawesome/free-solid-svg-icons';
import {
    KnowledgeArea,
    KnowledgeAreaDTO,
    StandardizedCompetencyDTO,
    convertToStandardizedCompetency,
    convertToStandardizedCompetencyDTO,
} from 'app/entities/competency/standardized-competency.model';
import { onError } from 'app/shared/util/global.utils';
import { AdminStandardizedCompetencyService } from 'app/admin/standardized-competencies/admin-standardized-competency.service';
import { StandardizedCompetencyService } from 'app/admin/standardized-competencies/standardized-competency.service';
import { HttpErrorResponse } from '@angular/common/http';
import { AlertService } from 'app/core/util/alert.service';
import { Subject, map } from 'rxjs';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { ConfirmAutofocusModalComponent } from 'app/shared/components/confirm-autofocus-modal.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { getIcon } from 'app/entities/competency.model';
import { ButtonSize, ButtonType } from 'app/shared/components/button.component';

@Component({
    selector: 'jhi-standardized-competency-management',
    templateUrl: './standardized-competency-management.component.html',
    styleUrls: ['standardized-competency-management.component.scss'],
})
export class StandardizedCompetencyManagementComponent implements OnInit, OnDestroy {
    //TODO: add a debounce to the search -> no. Do a button since at least the title search is very expensive!
    //TODO: add a new type that is basically dto + visible. transform everything into this.
    //TODO: filters set visible = true/false. (and while going through the tree we also mark if we had a hit in this subtree and then open it up.)

    competencyTitleFilter?: string;
    knowledgeAreaFilter?: KnowledgeAreaDTO;
    selectedCompetency?: StandardizedCompetencyDTO;
    isLoading = false;
    //true if a competency is getting edited
    isEditing = false;

    knowledgeAreasForSelect: KnowledgeAreaDTO[] = [];
    knowledgeAreaMap = new Map<number, KnowledgeAreaDTO>();
    knowledgeAreas: KnowledgeAreaDTO[] = [];

    treeControl = new NestedTreeControl<KnowledgeAreaDTO>((node) => node.children);
    dataSource = new MatTreeNestedDataSource<KnowledgeAreaDTO>();
    dialogErrorSource = new Subject<string>();
    dialogError = this.dialogErrorSource.asObservable();

    //Icons
    protected readonly faChevronRight = faChevronRight;
    protected readonly faPlus = faPlus;
    //Other constants for template
    protected readonly ButtonType = ButtonType;
    protected readonly ButtonSize = ButtonSize;
    protected readonly getIcon = getIcon;

    //TODO: also check if I need trackBy.
    readonly trackBy = (_: number, node: KnowledgeAreaDTO) => node.id;

    constructor(
        private adminStandardizedCompetencyService: AdminStandardizedCompetencyService,
        private standardizedCompetencyService: StandardizedCompetencyService,
        private alertService: AlertService,
        private modalService: NgbModal,
        private artemisTranslatePipe: ArtemisTranslatePipe,
    ) {}

    ngOnInit() {
        this.isLoading = true;
        this.standardizedCompetencyService
            .getAllForTreeView()
            .pipe(map((response) => response.body!))
            .subscribe({
                next: (knowledgeAreas) => {
                    this.dataSource.data = knowledgeAreas;
                    this.knowledgeAreas = knowledgeAreas;
                    knowledgeAreas.forEach((knowledgeArea) => {
                        this.addSelfAndChildrenToMap(knowledgeArea);
                        this.addSelfAndChildrenToSelectArray(knowledgeArea, 0);
                    });
                    this.isLoading = false;
                },
                error: (errorResponse: HttpErrorResponse) => onError(this.alertService, errorResponse),
            });
    }

    ngOnDestroy(): void {
        this.dialogErrorSource.unsubscribe();
    }

    private addSelfAndChildrenToMap(knowledgeArea: KnowledgeAreaDTO) {
        if (knowledgeArea.id !== undefined) {
            this.knowledgeAreaMap.set(knowledgeArea.id, knowledgeArea);
        }
        for (const child of knowledgeArea.children ?? []) {
            this.addSelfAndChildrenToMap(child);
        }
    }

    private addSelfAndChildrenToSelectArray(knowledgeArea: KnowledgeAreaDTO, level: number) {
        this.knowledgeAreasForSelect.push({
            id: knowledgeArea.id,
            title: '\xa0'.repeat(level * 2) + knowledgeArea.title,
        });
        for (const child of knowledgeArea.children ?? []) {
            this.addSelfAndChildrenToSelectArray(child, level + 1);
        }
    }

    filterByKnowledgeArea() {
        const filteredKnowledgeArea = this.getKnowledgeAreaByIdIfExists(this.knowledgeAreaFilter?.id);
        if (!filteredKnowledgeArea) {
            this.dataSource.data = this.knowledgeAreas;
            this.treeControl.collapseAll();
        } else {
            this.dataSource.data = [filteredKnowledgeArea];
            this.treeControl.expand(filteredKnowledgeArea);
        }
    }

    filterByCompetencyTitle() {
        const trimmedFilter = this.competencyTitleFilter?.trim();

        if (!trimmedFilter) {
            this.dataSource.data = this.knowledgeAreas;
            this.treeControl.collapseAll();
        }
    }

    private filterCompetenciesByTitleForSelfAndChildren(knowledgeArea: KnowledgeAreaDTO, titleFilter: string) {
        if (knowledgeArea && titleFilter) {
            console.log('i just do this so i can commit:)))');
        }
        //TODO: implement title filer
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
                this.updateTreeAfterDelete();
                this.dialogErrorSource.next('');
            },
            error: (error: HttpErrorResponse) => this.dialogErrorSource.next(error.message),
        });
    }

    saveCompetency(competencyDTO: StandardizedCompetencyDTO) {
        const competency = convertToStandardizedCompetency(competencyDTO);

        if (competency.id === undefined) {
            this.adminStandardizedCompetencyService
                .createStandardizedCompetency(competency)
                .pipe(map((response) => response.body!))
                .subscribe({
                    next: (resultCompetency) => {
                        this.updateTreeAfterCreate(convertToStandardizedCompetencyDTO(resultCompetency));
                    },
                    error: (error: HttpErrorResponse) => onError(this.alertService, error),
                });
        } else {
            this.adminStandardizedCompetencyService
                .updateStandardizedCompetency(competency)
                .pipe(map((response) => response.body!))
                .subscribe({
                    next: (resultCompetency) => {
                        this.updateTreeAfterUpdate(convertToStandardizedCompetencyDTO(resultCompetency));
                    },
                    error: (error: HttpErrorResponse) => onError(this.alertService, error),
                });
        }
    }

    //functions that update the tree in the user interface

    private updateTreeAfterDelete() {
        const knowledgeArea = this.getKnowledgeAreaByIdIfExists(this.selectedCompetency?.knowledgeAreaId);
        if (!knowledgeArea) {
            this.alertService.error('artemisApp.standardizedCompetency.manage.updateTreeError');
            return;
        }
        knowledgeArea.competencies = knowledgeArea.competencies?.filter((c) => c.id !== this.selectedCompetency?.id);
        this.isEditing = false;
        this.selectedCompetency = undefined;
    }

    private updateTreeAfterCreate(competency: StandardizedCompetencyDTO) {
        const knowledgeArea = this.getKnowledgeAreaByIdIfExists(competency.knowledgeAreaId);
        if (!knowledgeArea) {
            this.alertService.error('artemisApp.standardizedCompetency.manage.updateTreeError');
            return;
        }
        knowledgeArea.competencies = (knowledgeArea.competencies ?? []).concat(competency);
        this.selectedCompetency = competency;
    }

    private updateTreeAfterUpdate(competency: StandardizedCompetencyDTO) {
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
            newKnowledgeArea.competencies = (newKnowledgeArea.competencies ?? []).concat(competency);
        } else {
            //if the knowlege area stayed the same insert the new competency/replace the existing one
            const index = previousKnowledgeArea.competencies.findIndex((c) => c.id === competency.id);
            if (index === -1) {
                this.alertService.error('artemisApp.standardizedCompetency.manage.updateTreeError');
                return;
            }
            previousKnowledgeArea.competencies.splice(index, 1, competency);
        }
        this.selectedCompetency = competency;
        return true;
    }

    //TODO: probably remove this :)
    refreshTree() {
        const _data = this.dataSource.data;
        this.dataSource.data = [];
        this.dataSource.data = _data;
    }

    //utility functions

    getKnowledgeAreaByIdIfExists(id: number | undefined) {
        if (id === undefined) {
            return undefined;
        }
        return this.knowledgeAreaMap.get(id);
    }

    private openCancelModal(entityName: string, callback: () => void) {
        const modalRef = this.modalService.open(ConfirmAutofocusModalComponent, { keyboard: true, size: 'md' });
        modalRef.componentInstance.title = 'artemisApp.standardizedCompetency.manage.cancelModalTitle';
        modalRef.componentInstance.text = this.artemisTranslatePipe.transform('artemisApp.standardizedCompetency.manage.cancelModalText', { entityName: entityName });
        modalRef.result.then(() => callback());
    }

    //TODO: make both components re-sizeable?
    //TODO: make knowledge areas mandatory! > discuss this with max.
}
