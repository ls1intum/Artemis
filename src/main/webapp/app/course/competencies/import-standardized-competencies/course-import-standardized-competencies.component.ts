import { getIcon } from 'app/entities/competency.model';
import { ButtonType } from 'app/shared/components/button.component';
import { KnowledgeAreaForTree, StandardizedCompetencyDTO, convertToKnowledgeAreaForTree } from 'app/entities/competency/standardized-competency.model';
import { MatTreeNestedDataSource } from '@angular/material/tree';
import { NestedTreeControl } from '@angular/cdk/tree';
import { faBan, faFileImport } from '@fortawesome/free-solid-svg-icons';
import { ActivatedRoute, Router } from '@angular/router';
import { Component, OnInit } from '@angular/core';
import { onError } from 'app/shared/util/global.utils';
import { StandardizedCompetencyService } from 'app/admin/standardized-competencies/standardized-competency.service';
import { map } from 'rxjs';
import { HttpErrorResponse } from '@angular/common/http';
import { AlertService } from 'app/core/util/alert.service';

@Component({
    selector: 'jhi-course-import-standardized-competencies',
    templateUrl: './course-import-standardized-competencies.component.html',
})
export class CourseImportStandardizedCompetenciesComponent implements OnInit {
    constructor(
        private router: Router,
        private activatedRoute: ActivatedRoute,
        private standardizedCompetencyService: StandardizedCompetencyService,
        private alertService: AlertService,
    ) {}

    ngOnInit(): void {
        this.isLoading = true;
        this.standardizedCompetencyService
            .getAllForTreeView()
            .pipe(map((response) => response.body!))
            .subscribe({
                next: (knowledgeAreas) => {
                    const knowledgeAreasForTree = knowledgeAreas.map((knowledgeArea) => convertToKnowledgeAreaForTree(knowledgeArea));
                    this.dataSource.data = knowledgeAreasForTree;
                    this.treeControl.dataNodes = knowledgeAreasForTree;
                    /** TODO: re-add with filter.
                    knowledgeAreasForTree.forEach((knowledgeArea) => {
                        this.addSelfAndDescendantsToSelectArray(knowledgeArea);
                    }); */
                    this.isLoading = false;
                },
                error: (errorResponse: HttpErrorResponse) => onError(this.alertService, errorResponse),
            });
    }

    protected competenciesToImport: StandardizedCompetencyDTO[] = [];
    protected isLoading = false;

    protected dataSource = new MatTreeNestedDataSource<KnowledgeAreaForTree>();
    protected treeControl = new NestedTreeControl<KnowledgeAreaForTree>((node) => node.children);

    // constants
    protected readonly getIcon = getIcon;
    protected readonly ButtonType = ButtonType;
    // icons
    protected readonly faBan = faBan;
    protected readonly faFileImport = faFileImport;

    protected importCompetencies() {
        //TODO: import
        this.router.navigate(['../'], { relativeTo: this.activatedRoute });
    }

    protected cancel() {
        this.router.navigate(['../'], { relativeTo: this.activatedRoute });
    }
}
