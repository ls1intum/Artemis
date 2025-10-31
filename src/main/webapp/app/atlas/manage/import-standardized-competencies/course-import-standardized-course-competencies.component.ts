import { getIcon } from 'app/atlas/shared/entities/competency.model';
import { ButtonSize, ButtonType } from 'app/shared/components/buttons/button/button.component';
import {
    KnowledgeAreaDTO,
    KnowledgeAreaForTree,
    Source,
    StandardizedCompetencyDTO,
    StandardizedCompetencyForTree,
    sourceToString,
} from 'app/atlas/shared/entities/standardized-competency.model';
import { faBan, faDownLeftAndUpRightToCenter, faFileImport, faSort, faTrash, faUpRightAndDownLeftFromCenter } from '@fortawesome/free-solid-svg-icons';
import { ActivatedRoute, Router } from '@angular/router';
import { Component, HostListener, OnInit, inject } from '@angular/core';
import { onError } from 'app/shared/util/global.utils';
import { forkJoin, map } from 'rxjs';
import { HttpErrorResponse } from '@angular/common/http';
import { AlertService } from 'app/shared/service/alert.service';
import { ComponentCanDeactivate } from 'app/shared/guard/can-deactivate.model';
import { TranslateService } from '@ngx-translate/core';
import { SortService } from 'app/shared/service/sort.service';
import { CompetencyService } from 'app/atlas/manage/services/competency.service';
import { DocumentationType } from 'app/shared/components/buttons/documentation-button/documentation-button.component';
import { PrerequisiteService } from 'app/atlas/manage/services/prerequisite.service';
import { StandardizedCompetencyFilterPageComponent } from 'app/atlas/shared/standardized-competencies/standardized-competency-filter-page.component';
import { StandardizedCompetencyService } from 'app/atlas/shared/standardized-competencies/standardized-competency.service';

interface StandardizedCompetencyForImport extends StandardizedCompetencyForTree {
    selected?: boolean;
    knowledgeAreaTitle?: string;
}

interface KnowledgeAreaForImport extends KnowledgeAreaForTree {
    children?: KnowledgeAreaForImport[];
    competencies?: StandardizedCompetencyForImport[];
}

@Component({
    template: '',
})
export abstract class CourseImportStandardizedCourseCompetenciesComponent extends StandardizedCompetencyFilterPageComponent implements OnInit, ComponentCanDeactivate {
    protected router = inject(Router);
    protected activatedRoute = inject(ActivatedRoute);
    protected standardizedCompetencyService = inject(StandardizedCompetencyService);
    protected alertService = inject(AlertService);
    protected translateService = inject(TranslateService);
    protected sortService = inject(SortService);

    protected selectedCompetencies: StandardizedCompetencyForImport[] = [];
    protected selectedCompetency?: StandardizedCompetencyForImport;
    protected sourceString = '';
    protected courseId: number;
    protected sources: Source[] = [];
    protected isLoading = false;
    protected isSubmitted = false;

    // constants
    protected readonly getIcon = getIcon;
    protected readonly ButtonType = ButtonType;
    protected readonly ButtonSize = ButtonSize;
    readonly documentationType: DocumentationType = 'StandardizedCompetencies';
    // icons
    protected readonly faBan = faBan;
    protected readonly faFileImport = faFileImport;
    protected readonly faMinimize = faDownLeftAndUpRightToCenter;
    protected readonly faMaximize = faUpRightAndDownLeftFromCenter;
    protected readonly faTrash = faTrash;
    protected readonly faSort = faSort;

    ngOnInit(): void {
        this.isLoading = true;
        const getKnowledgeAreasObservable = this.standardizedCompetencyService.getAllForTreeView();
        const getSourcesObservable = this.standardizedCompetencyService.getSources();
        forkJoin([getKnowledgeAreasObservable, getSourcesObservable]).subscribe({
            next: ([knowledgeAreasResponse, sourcesResponse]) => {
                const knowledgeAreas = knowledgeAreasResponse.body!;
                const knowledgeAreasForImport = knowledgeAreas.map((knowledgeArea) => this.convertToKnowledgeAreaForImport(knowledgeArea));
                this.dataSource.data = knowledgeAreasForImport;
                knowledgeAreasForImport.forEach((knowledgeArea) => {
                    this.addSelfAndDescendantsToMap(knowledgeArea);
                    this.addSelfAndDescendantsToSelectArray(knowledgeArea);
                });

                this.sources = sourcesResponse.body!;
            },
            error: (errorResponse: HttpErrorResponse) => onError(this.alertService, errorResponse),
            complete: () => {
                this.isLoading = false;
            },
        });
        this.courseId = Number(this.activatedRoute.snapshot.paramMap.get('courseId'));
    }

    protected openCompetencyDetails(competency: StandardizedCompetencyForImport) {
        const source = this.sources.find((source) => source.id === competency.sourceId);
        this.sourceString = source ? sourceToString(source) : '';
        this.selectedCompetency = competency;
    }

    protected closeCompetencyDetails() {
        this.sourceString = '';
        this.selectedCompetency = undefined;
    }

    protected toggleSelect(selectedCompetency: StandardizedCompetencyForImport) {
        if (selectedCompetency.selected) {
            this.selectedCompetencies.push(selectedCompetency);
        } else {
            this.selectedCompetencies = this.selectedCompetencies.filter((competency) => competency.id !== selectedCompetency.id);
        }
    }

    protected deselectCompetency(selectedCompetency: StandardizedCompetencyForImport) {
        selectedCompetency.selected = false;
        if (!selectedCompetency.id) {
            return;
        }
        this.selectedCompetencies = this.selectedCompetencies.filter((competency) => competency.id !== selectedCompetency.id);
    }

    protected importCompetencies(service: CompetencyService | PrerequisiteService) {
        if (!this.selectedCompetencies.length) {
            return;
        }

        const idsToImport = this.selectedCompetencies.map((competency) => competency.id!);

        this.isLoading = true;
        service
            .importStandardizedCompetencies(idsToImport, this.courseId)
            .pipe(map((response) => response.body!.length))
            .subscribe({
                next: (countImportedCompetencies) => {
                    this.isSubmitted = true;
                    this.alertService.success('artemisApp.standardizedCompetency.courseImport.success', { count: countImportedCompetencies });
                    this.router.navigate(['../'], { relativeTo: this.activatedRoute });
                },
                error: (errorResponse: HttpErrorResponse) => onError(this.alertService, errorResponse),
                complete: () => {
                    this.isLoading = false;
                },
            });
    }

    protected cancel() {
        this.router.navigate(['../'], { relativeTo: this.activatedRoute });
    }

    /**
     * Callback that sorts the selected competencies
     *
     * @param sort the search object with the updated search predicate and sorting direction
     */
    sortSelected(sort: { predicate: string; ascending: boolean }) {
        this.selectedCompetencies = this.sortService.sortByProperty(this.selectedCompetencies, sort.predicate, sort.ascending);
    }

    /**
     * Only allow to leave page after submitting or if no pending changes exist
     */
    canDeactivate() {
        return this.isSubmitted || (!this.isLoading && this.selectedCompetencies.length === 0);
    }

    get canDeactivateWarning(): string {
        return this.translateService.instant('pendingChanges');
    }

    private convertToKnowledgeAreaForImport(knowledgeAreaDTO: KnowledgeAreaDTO, isVisible = true, level = 0, selected = false): KnowledgeAreaForImport {
        const children = knowledgeAreaDTO.children?.map((child) => this.convertToKnowledgeAreaForImport(child, isVisible, level + 1));
        const competencies = knowledgeAreaDTO.competencies?.map((competency) =>
            this.convertToStandardizedCompetencyForImport(competency, knowledgeAreaDTO.title, isVisible, selected),
        );
        return { ...knowledgeAreaDTO, children: children, competencies: competencies, level: level, isVisible: isVisible };
    }

    private convertToStandardizedCompetencyForImport(competencyDTO: StandardizedCompetencyDTO, knowledgeAreaTitle?: string, isVisible = true, selected = false) {
        const competencyForTree: StandardizedCompetencyForImport = { ...competencyDTO, isVisible: isVisible, knowledgeAreaTitle: knowledgeAreaTitle, selected: selected };
        return competencyForTree;
    }

    /**
     * Displays the alert for confirming refreshing or closing the page if there are unsaved changes
     * NOTE: while the beforeunload event might be deprecated in the future, it is currently the only way to display a confirmation dialog when the user tries to leave the page
     * @param event the beforeunload event
     */
    @HostListener('window:beforeunload', ['$event'])
    unloadNotification(event: BeforeUnloadEvent) {
        if (!this.canDeactivate()) {
            event.preventDefault();
            return this.canDeactivateWarning;
        }
        return true;
    }
}
