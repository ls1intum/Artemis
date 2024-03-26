import { Component, OnInit } from '@angular/core';
import { NestedTreeControl } from '@angular/cdk/tree';
import { MatTreeNestedDataSource } from '@angular/material/tree';
import { faChevronRight } from '@fortawesome/free-solid-svg-icons';
import { KnowledgeArea, StandardizedCompetency } from 'app/entities/competency/standardized-competency.model';
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
    knowledgeAreaArray: KnowledgeArea[];
    knowledgeAreaMap = new Map<number, KnowledgeArea>();

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
                    //set the knowledgeArea for all competencies (since it has to be @JsonIgnored)
                    //this is needed to detect if a competency was moved on update
                    for (const knowledgeArea of knowledgeAreas) {
                        const minimalKnowledgeArea: KnowledgeArea = {
                            id: knowledgeArea.id,
                        };
                        knowledgeArea.competencies?.forEach((competency) => (competency.knowledgeArea = minimalKnowledgeArea));
                    }
                    this.knowledgeAreas = knowledgeAreas;
                    this.dataSourceNested.data = knowledgeAreas;
                    this.knowledgeAreaArray = knowledgeAreas.flatMap((knowledgeArea) => this.getSelfAndChildrenAsArrayMinimizedWithIndent(knowledgeArea, 0));
                    knowledgeAreas.forEach((knowledgeArea) => this.addSelfAndChildrenToMap(knowledgeArea));
                    this.isLoading = false;
                    console.log(knowledgeAreas);
                },
                error: (errorResponse: HttpErrorResponse) => onError(this.alertService, errorResponse),
            });
    }

    private addSelfAndChildrenToMap(knowledgeArea: KnowledgeArea) {
        if (knowledgeArea.id !== undefined) {
            this.knowledgeAreaMap.set(knowledgeArea.id, knowledgeArea);
        }
        for (const child of knowledgeArea.children ?? []) {
            this.addSelfAndChildrenToMap(child);
        }
    }

    filterByKnowledgeArea() {
        //TODO: redo with map!
        /*if (this.knowledgeAreaFilter == undefined || this.knowledgeAreaFilter.id == undefined) {
            this.dataSourceNested.data = this.knowledgeAreas;
        } else {
            //TODO: this code needs to be replaced!
            const foundKa: KnowledgeArea = this.knowledgeAreaArray.find((ka) => ka.id == this.knowledgeAreaFilter!.id)!;
            this.dataSourceNested.data = [foundKa];
            this.treeControlNested.expand(foundKa);
        }*/
    }

    filterByCompetencyName() {
        //TODO: if competency name is empty do nothing
        //TODO: if competency name is not empty ->
    }

    getSelfAndChildrenAsArrayMinimizedWithIndent(knowledgeArea: KnowledgeArea, level: number): KnowledgeArea[] {
        const knowledgeAreaMinimizedWithIndent = {
            id: knowledgeArea.id,
            title: '\xa0'.repeat(level * 2) + knowledgeArea.title,
        };
        if (knowledgeArea.children) {
            const childrenWithLevel = knowledgeArea.children.map((child) => this.getSelfAndChildrenAsArrayMinimizedWithIndent(child, level + 1)).flat();
            return [knowledgeAreaMinimizedWithIndent, ...childrenWithLevel];
        }
        return [knowledgeAreaMinimizedWithIndent];
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
        console.log(this.selectedCompetency);
    }

    deleteCompetency() {
        //TODO: call server

        if (this.selectedCompetency?.knowledgeArea?.id === undefined) {
            //TODO: error :)
            return;
        }
        const knowldgeArea = this.knowledgeAreaMap.get(this.selectedCompetency?.knowledgeArea.id);

        if (!knowldgeArea) {
            //TODO: error
            return;
        }
        knowldgeArea.competencies = knowldgeArea.competencies?.filter((c) => c.id !== this.selectedCompetency?.id);
        this.isEditing = false;
        this.selectedCompetency = undefined;
    }

    updateCompetency(competency: StandardizedCompetency) {
        if (competency.knowledgeArea?.id === undefined || this.selectedCompetency?.knowledgeArea?.id === undefined) {
            console.log('competency has no knowledge area set, cannot update.');
            //TODO: alert service
            return;
        }

        //TODO call server.
        //TODO: exclamation marks ^^

        const previousKnowledgeArea = this.knowledgeAreaMap.get(this.selectedCompetency!.knowledgeArea!.id!)!;
        //if the knowledge area changed, move the competency to the new knowledge area
        if (competency.knowledgeArea.id !== previousKnowledgeArea?.id) {
            const newKnowledgeArea = this.knowledgeAreaMap.get(competency.knowledgeArea.id);
            if (newKnowledgeArea?.id === undefined) {
                //TODO: alert service
                return;
            }
            if (previousKnowledgeArea === undefined) {
                return;
            }
            previousKnowledgeArea.competencies = previousKnowledgeArea?.competencies?.filter((c) => c.id !== competency.id);
            newKnowledgeArea.competencies = (newKnowledgeArea.competencies ?? []).concat(competency);
            this.selectedCompetency = competency;
        } else {
            //if the knowlege area stayed the same insert the new competency/replace the existing one
            const index = previousKnowledgeArea.competencies?.findIndex((c) => c.id === competency.id);
            if (index === undefined || index === -1) {
                previousKnowledgeArea.competencies = (previousKnowledgeArea.competencies ?? []).concat(competency);
            } else {
                previousKnowledgeArea.competencies!.splice(index, 1, competency);
            }
            this.selectedCompetency = competency;
        }
        console.log(competency);
        console.log(this.selectedCompetency);
        this.refreshTree();
    }

    closeCompetency() {
        if (this.isEditing) {
            //TODO: also display closing warning!
            //maybe do private method performCloseCompetency, which gets handed to method that creates dialog.
        }
        this.selectedCompetency = undefined;
        this.isEditing = false;
    }

    //TODO: maybe utility method that checks it has id and it is save in map
    //TODO: kaDTO: parentId, competencyDTO
    //TODO: compDTO: kaId, sourceId
    //TODO: add create button
    //TODO: make competencies look nice on the left side
    //TODO: check things about ka with level and stuff
    //TODO: do something about all the errors
    //TODO: make both components re-sizeable?
}
