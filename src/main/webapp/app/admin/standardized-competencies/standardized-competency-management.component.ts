import { Component, OnInit } from '@angular/core';
import { NestedTreeControl } from '@angular/cdk/tree';
import { MatTreeNestedDataSource } from '@angular/material/tree';
import { faChevronRight } from '@fortawesome/free-solid-svg-icons';
import { KnowledgeArea, KnowledgeAreaWithLevel, StandardizedCompetency } from 'app/entities/competency/standardized-competency.model';
import { onError } from 'app/shared/util/global.utils';
import { AdminStandardizedCompetencyService } from 'app/admin/standardized-competencies/admin-standardized-competency.service';
import { StandardizedCompetencyService } from 'app/admin/standardized-competencies/standardized-competency.service';
import { HttpErrorResponse } from '@angular/common/http';
import { AlertService } from 'app/core/util/alert.service';
import { map } from 'rxjs';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { ConfirmAutofocusModalComponent } from 'app/shared/components/confirm-autofocus-modal.component';
import { TranslateService } from '@ngx-translate/core';

@Component({
    selector: 'jhi-standardized-competency-management',
    templateUrl: './standardized-competency-management.component.html',
    styleUrls: ['standardized-competency-management.component.scss'],
})
export class StandardizedCompetencyManagementComponent implements OnInit {
    //TODO: add a debounce to the search
    //TODO: differentiate between tree and original data source, so i can filter =)
    //TODO: display hierarchy in the select

    competencyTitleFilter?: string;
    knowledgeAreaFilter?: KnowledgeArea;
    selectedCompetency?: StandardizedCompetency;
    isLoading = false;
    //true if a competency is getting edited
    isEditing = false;

    //TODO: do this with nesting? > rename
    knowledgeAreaArray: KnowledgeAreaWithLevel[];

    //TODO: maybe make this listen?
    //the original data (to restore after resetting filters)
    knowledgeAreas: KnowledgeArea[];

    treeControlNested = new NestedTreeControl<KnowledgeArea>((node) => node.children);
    dataSourceNested = new MatTreeNestedDataSource<KnowledgeArea>();

    //Icons
    protected readonly faChevronRight = faChevronRight;

    readonly trackBy = (_: number, node: KnowledgeArea) => node.id;

    constructor(
        private adminStandardizedCompetencyService: AdminStandardizedCompetencyService,
        private standardizedCompetencyService: StandardizedCompetencyService,
        private alertService: AlertService,
        private modalService: NgbModal,
        //TODO: see if I want translate service or not!
        private translateService: TranslateService,
    ) {}

    ngOnInit() {
        this.isLoading = true;
        this.standardizedCompetencyService
            .getAllForTreeView()
            .pipe(map((response) => response.body!))
            .subscribe({
                next: (knowledgeAreas) => {
                    this.knowledgeAreas = knowledgeAreas;
                    this.dataSourceNested.data = knowledgeAreas;
                    this.knowledgeAreaArray = knowledgeAreas.flatMap((knowledgeArea) => this.getSelfAndChildrenAsArrayMinimizedWithLevel(knowledgeArea, 0));
                    this.isLoading = false;
                },
                error: (errorResponse: HttpErrorResponse) => onError(this.alertService, errorResponse),
            });
    }

    filterByKnowledgeArea() {
        if (this.knowledgeAreaFilter == undefined || this.knowledgeAreaFilter.id == undefined) {
            this.dataSourceNested.data = this.knowledgeAreas;
        } else {
            //TODO: this code needs to be replaced!
            const foundKa: KnowledgeArea = this.knowledgeAreaArray.find((ka) => ka.id == this.knowledgeAreaFilter!.id)!;
            this.dataSourceNested.data = [foundKa];
            this.treeControlNested.expand(foundKa);
        }
    }

    filterByCompetencyName() {
        //TODO: if competency name is empty do nothing
        //TODO: if competency name is not empty ->
    }

    getSelfAndChildrenAsArrayMinimizedWithLevel(knowledgeArea: KnowledgeArea, level: number): KnowledgeAreaWithLevel[] {
        const knowledgeAreaWithLevel = this.minimizeAndAddLevel(knowledgeArea, level);
        if (knowledgeArea.children) {
            const childrenWithLevel = knowledgeArea.children.map((child) => this.getSelfAndChildrenAsArrayMinimizedWithLevel(child, level + 1)).flat();
            return [knowledgeAreaWithLevel, ...childrenWithLevel];
        }
        return [knowledgeAreaWithLevel];
    }

    minimizeAndAddLevel(knowledgeArea: KnowledgeArea, level: number): KnowledgeAreaWithLevel {
        return {
            id: knowledgeArea.id,
            title: knowledgeArea.title,
            level: level,
        };
    }

    refreshTree() {
        const _data = this.dataSourceNested.data;
        this.dataSourceNested.data = [];
        this.dataSourceNested.data = _data;
    }

    selectCompetency(competency: StandardizedCompetency) {
        if (this.selectedCompetency?.id === competency.id) {
            return;
        }
        if (this.selectedCompetency && this.isEditing) {
            const competencyTitle = this.selectedCompetency.title;
            const modalRef = this.modalService.open(ConfirmAutofocusModalComponent, { keyboard: true, size: 'md' });
            //TODO: change the strings.
            modalRef.componentInstance.title = 'artemisApp.competency.generate.deleteModalTitle';
            modalRef.componentInstance.text = this.translateService.instant('artemisApp.competency.generate.deleteModalText', { title: competencyTitle });
            modalRef.result.then(() => {
                this.isEditing = false;
                this.selectedCompetency = competency;
            });
        } else {
            this.selectedCompetency = competency;
        }
    }

    deleteCompetency(competencyId: number) {
        console.log(competencyId);
        //TODO: only if successful
        this.selectedCompetency = undefined;
        //TODO: also delete from other stuff and co (and de-select)
    }

    updateCompetency(competency: StandardizedCompetency) {
        //TODO: send to server
        //TODO: what if no knowledge area is given -> it may not be null so ignore it??
        competency.title = '^^';
        console.log(competency);
        this.refreshTree();
        //TODO: other stuff
    }
}
