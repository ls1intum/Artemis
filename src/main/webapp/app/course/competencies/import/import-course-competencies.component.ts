import { ComponentCanDeactivate } from 'app/shared/guard/can-deactivate.model';
import { CourseCompetencyFilter, PageableSearch, SearchResult, SortingOrder } from 'app/shared/table/pageable-table';
import { CourseCompetency, CourseCompetencyType } from 'app/entities/competency.model';
import { AlertService } from 'app/core/util/alert.service';
import { SortService } from 'app/shared/service/sort.service';
import { onError } from 'app/shared/util/global.utils';
import { Component, HostListener, OnInit, inject } from '@angular/core';
import { faBan, faFileImport, faSave, faTrash } from '@fortawesome/free-solid-svg-icons';
import { ButtonType } from 'app/shared/components/button.component';
import { ActivatedRoute, Router } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { HttpErrorResponse } from '@angular/common/http';
import { CourseCompetencyService } from 'app/course/competencies/course-competency.service';

/**
 * An abstract component used to import course competencies. Its concrete implementations are
 * {@link ImportCompetenciesComponent} and {@link ImportPrerequisitesComponent}
 */
@Component({ template: '' })
export abstract class ImportCourseCompetenciesComponent implements OnInit, ComponentCanDeactivate {
    // this attribute has to be set when using the common template (import-course-competencies.component.html)
    abstract entityType: string;
    // set this attribute to hide the options to import relation
    allowRelationImport: boolean = false;

    courseId: number;
    isLoading = false;
    isSubmitted = false;
    importRelations = true;
    showAdvancedSearch = false;
    disabledIds: number[] = [];
    searchedCourseCompetencies: SearchResult<CourseCompetency> = { resultsOnPage: [], numberOfPages: 0 };
    selectedCourseCompetencies: SearchResult<CourseCompetency> = { resultsOnPage: [], numberOfPages: 0 };

    //filter and search objects for the course competency search.
    filter: CourseCompetencyFilter = {
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

    //search object for the selected course competencies. As we don't want pagination page and pageSize are 0
    selectedCourseCompetenciesSearch: PageableSearch = {
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
    //used for sorting of the selected course competencies
    protected readonly columnMapping: { [key: string]: string } = {
        ID: 'id',
        TITLE: 'title',
        DESCRIPTION: 'description',
        COURSE_TITLE: 'course.title',
        SEMESTER: 'course.semester',
    };

    protected readonly activatedRoute: ActivatedRoute = inject(ActivatedRoute);
    protected readonly router: Router = inject(Router);
    protected readonly courseCompetencyService: CourseCompetencyService = inject(CourseCompetencyService);
    protected readonly alertService: AlertService = inject(AlertService);
    private readonly translateService: TranslateService = inject(TranslateService);
    private readonly sortingService: SortService = inject(SortService);

    ngOnInit(): void {
        this.courseId = Number(this.activatedRoute.snapshot.paramMap.get('courseId'));
        // load competencies and prerequisites of this course to disable their import buttons
        const courseCompetencySubscription = this.courseCompetencyService.getAllForCourse(this.courseId);
        courseCompetencySubscription.subscribe({
            next: (courseCompetenciesResponse) => {
                const courseCompetencies = courseCompetenciesResponse.body ?? [];
                this.disabledIds = courseCompetencies
                    .flatMap((courseCompetency) => {
                        switch (courseCompetency.type) {
                            case CourseCompetencyType.COMPETENCY:
                                return [courseCompetency.id];
                            case CourseCompetencyType.PREREQUISITE:
                                return [courseCompetency.id, courseCompetency.linkedCourseCompetency?.id];
                        }
                    })
                    .filter((id): id is number => !!id);
                this.performSearch();
            },
            error: (error: HttpErrorResponse) => onError(this.alertService, error),
        });
    }

    /**
     * Submits the course competencies to import and if successful, should navigate back
     */
    abstract onSubmit(): void;

    /**
     * Callback that updates the filter for the competency search and fetches new data from the server.
     *
     * @param filter the new filter
     */
    filterChange(filter: CourseCompetencyFilter) {
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
     * Fetches a page of course competencies matching a PageableSearch from the server.
     *
     */
    performSearch() {
        this.isLoading = true;
        this.courseCompetencyService.getForImport({ ...this.filter, ...this.search }).subscribe({
            next: (res) => {
                this.searchedCourseCompetencies = res;
                this.isLoading = false;
            },
            error: (error: HttpErrorResponse) => onError(this.alertService, error),
        });
    }

    /**
     * Callback that sorts the selected course competencies
     *
     * @param search the PageableSearch object with the updated sorting data
     */
    sortSelected(search: PageableSearch) {
        this.selectedCourseCompetencies.resultsOnPage = this.sortingService.sortByProperty(
            this.selectedCourseCompetencies.resultsOnPage,
            this.columnMapping[search.sortedColumn],
            search.sortingOrder === SortingOrder.ASCENDING,
        );
    }

    /**
     * Callback to add a course competency to the selected list
     *
     * @param competency the competency to add
     */
    selectCompetency(competency: CourseCompetency) {
        if (competency.id) {
            this.disabledIds.push(competency.id);
        }
        this.selectedCourseCompetencies.resultsOnPage.push(competency);
        this.sortSelected(this.selectedCourseCompetenciesSearch);
    }

    /**
     * Callback to remove a course competency from the selected list
     *
     * @param competency the competency to remove
     */
    removeCompetency(competency: CourseCompetency) {
        if (competency.id) {
            this.disabledIds = this.disabledIds.filter((id) => id !== competency.id);
        }
        this.selectedCourseCompetencies.resultsOnPage = this.selectedCourseCompetencies.resultsOnPage.filter((c) => c.id !== competency.id);
    }

    /**
     * Only allows submitting if at least one competency has been selected for import
     */
    isSubmitPossible() {
        return this.selectedCourseCompetencies.resultsOnPage.length > 0;
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
        return this.isSubmitted || (!this.isLoading && this.selectedCourseCompetencies.resultsOnPage.length === 0);
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
