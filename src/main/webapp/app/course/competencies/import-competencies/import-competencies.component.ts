import { Component, HostListener, OnInit } from '@angular/core';
import { faBan, faFileImport, faSave, faTrash } from '@fortawesome/free-solid-svg-icons';
import { ButtonType } from 'app/shared/components/button.component';
import { ActivatedRoute, Router } from '@angular/router';
import { ComponentCanDeactivate } from 'app/shared/guard/can-deactivate.model';
import { TranslateService } from '@ngx-translate/core';
import { CompetencyService } from 'app/course/competencies/competency.service';
import { HttpErrorResponse } from '@angular/common/http';
import { onError } from 'app/shared/util/global.utils';
import { AlertService } from 'app/core/util/alert.service';
import { Competency } from 'app/entities/competency.model';
import { CompetencyFilter, PageableSearch, SearchResult, SortingOrder } from 'app/shared/table/pageable-table';
import { SortService } from 'app/shared/service/sort.service';

@Component({
    selector: 'jhi-import-competencies',
    templateUrl: './import-competencies.component.html',
})
export class ImportCompetenciesComponent implements OnInit, ComponentCanDeactivate {
    courseId: number;
    isLoading = false;
    isSubmitted = false;
    importRelations = true;
    showAdvancedSearch = false;
    disabledIds: number[] = [];
    searchedCompetencies: SearchResult<Competency> = { resultsOnPage: [], numberOfPages: 0 };
    selectedCompetencies: SearchResult<Competency> = { resultsOnPage: [], numberOfPages: 0 };

    //filter and search objects for the competency search.
    filter: CompetencyFilter = {
        courseTitle: '',
        description: '',
        semester: '',
        title: '',
    };
    search: PageableSearch = {
        page: 1,
        pageSize: 10,
        sortingOrder: SortingOrder.DESCENDING,
        sortedColumn: 'ID',
    };

    //search object for the selected competencies. As we don't want pagination page and pageSize are 0
    selectedCompetenciesSearch: PageableSearch = {
        page: 0,
        pageSize: 0,
        sortingOrder: SortingOrder.DESCENDING,
        sortedColumn: 'ID',
    };

    //Icons
    protected readonly faBan = faBan;
    protected readonly faSave = faSave;
    protected readonly faFileImport = faFileImport;
    protected readonly faTrash = faTrash;
    //Other constants
    protected readonly ButtonType = ButtonType;
    //used for sorting of the selected competencies
    protected readonly columnMapping = {
        ID: 'id',
        TITLE: 'title',
        DESCRIPTION: 'description',
        COURSE_TITLE: 'course.title',
        SEMESTER: 'course.semester',
    };

    constructor(
        private activatedRoute: ActivatedRoute,
        private router: Router,
        private translateService: TranslateService,
        private competencyService: CompetencyService,
        private alertService: AlertService,
        private sortingService: SortService,
    ) {}

    ngOnInit(): void {
        this.activatedRoute.params.subscribe((params) => {
            this.courseId = Number(params['courseId']);
            this.performSearch();
            //load competencies of this course to disable their import buttons
            this.competencyService.getAllForCourse(this.courseId).subscribe({
                next: (res) => {
                    if (res.body) {
                        this.disabledIds = res.body.map((competency) => competency.id).filter((id): id is number => !!id);
                    }
                },
                error: (error: HttpErrorResponse) => onError(this.alertService, error),
            });
        });
    }

    /**
     * Callback that updates the filter for the competency search and fetches new data from the server.
     *
     * @param filter the new filter
     */
    filterChange(filter: CompetencyFilter) {
        this.filter = filter;
        //navigate back to the first page when the filter changes
        this.search.page = 1;
        this.performSearch();
    }

    /**
     * Callback that updates the pagination/sorting for the competency search and fetches new data from the server.
     *
     * @param search the new pagination/sorting
     */
    searchChange(search: PageableSearch) {
        this.search = search;
        this.performSearch();
    }

    /**
     * Fetches a page of competencies matching a PageableSearch from the server.
     *
     */
    performSearch() {
        this.isLoading = true;
        this.competencyService.getForImport({ ...this.filter, ...this.search }).subscribe({
            next: (res) => {
                this.searchedCompetencies = res;
                this.isLoading = false;
            },
            error: (error: HttpErrorResponse) => onError(this.alertService, error),
        });
    }

    /**
     * Callback that sorts the selected competencies
     *
     * @param search the PageableSearch object with the updated sorting data
     */
    sortSelected(search: PageableSearch) {
        this.selectedCompetencies.resultsOnPage = this.sortingService.sortByProperty(
            this.selectedCompetencies.resultsOnPage,
            this.columnMapping[search.sortedColumn],
            search.sortingOrder === SortingOrder.ASCENDING,
        );
    }

    /**
     * Callback to add a competency to the selected list
     *
     * @param competency the competency to add
     */
    selectCompetency(competency: Competency) {
        if (competency.id) {
            this.disabledIds.push(competency.id);
        }
        this.selectedCompetencies.resultsOnPage.push(competency);
        this.sortSelected(this.selectedCompetenciesSearch);
    }

    /**
     * Callback to remove a competency from the selected list
     *
     * @param competency the competency to remove
     */
    removeCompetency(competency: Competency) {
        if (competency.id) {
            this.disabledIds = this.disabledIds.filter((id) => id !== competency.id);
        }
        this.selectedCompetencies.resultsOnPage = this.selectedCompetencies.resultsOnPage.filter((c) => c.id !== competency.id);
    }

    /**
     * Only allows submitting if at least one competency has been selected for import
     */
    isSubmitPossible() {
        return this.selectedCompetencies.resultsOnPage.length > 0;
    }

    /**
     * Submits the competencies to import and if successful, navigates back
     */
    onSubmit() {
        this.competencyService.importBulk(this.selectedCompetencies.resultsOnPage, this.courseId, this.importRelations).subscribe({
            next: (res) => {
                this.alertService.success('artemisApp.competency.import.success', { noOfCompetencies: res.body?.length ?? 0 });
                this.isSubmitted = true;
                this.router.navigate(['../'], { relativeTo: this.activatedRoute });
            },
            error: (error: HttpErrorResponse) => onError(this.alertService, error),
        });
    }

    /**
     * Cancels the import and navigates back
     */
    onCancel() {
        this.router.navigate(['../'], { relativeTo: this.activatedRoute });
    }

    /**
     * Only allow to leave page after submitting or if no pending changes exist
     */
    canDeactivate() {
        return this.isSubmitted || (!this.isLoading && this.selectedCompetencies.resultsOnPage.length === 0);
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
