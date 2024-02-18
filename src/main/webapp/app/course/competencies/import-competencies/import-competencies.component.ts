import { Component, HostListener, OnInit } from '@angular/core';
import { faBan, faFileImport, faSave } from '@fortawesome/free-solid-svg-icons';
import { ButtonType } from 'app/shared/components/button.component';
import { ActivatedRoute, Router } from '@angular/router';
import { ComponentCanDeactivate } from 'app/shared/guard/can-deactivate.model';
import { TranslateService } from '@ngx-translate/core';
import { CompetencyService } from 'app/course/competencies/competency.service';
import { HttpErrorResponse } from '@angular/common/http';
import { onError } from 'app/shared/util/global.utils';
import { AlertService } from 'app/core/util/alert.service';
import { Competency } from 'app/entities/competency.model';
import { BasePageableSearch, CompetencyFilter, CompetencyPageableSearch, SearchResult, SortingOrder } from 'app/shared/table/pageable-table';

@Component({
    selector: 'jhi-import-competencies',
    templateUrl: './import-competencies.component.html',
})
export class ImportCompetenciesComponent implements OnInit, ComponentCanDeactivate {
    isLoading = false;
    importRelations = true;
    showAdvancedSearch = false;
    courseId: number;

    searchedCompetencies: SearchResult<Competency> = { resultsOnPage: [], numberOfPages: 0 };
    //TODO: from this course, or in selectedCompetencies.
    disabledIds: number[] = [];
    //TODO: if i use this i need to solve: sorting, pagination
    // (or just say display 999 elements per page or smth > pagination then still shows and its kinda cringe :D)
    selectedCompetencies: SearchResult<Competency>;

    filter: CompetencyFilter = {
        courseTitle: '',
        description: '',
        semester: '',
        title: '',
    };

    search: BasePageableSearch = {
        page: 1,
        pageSize: 10,
        sortingOrder: SortingOrder.DESCENDING,
        sortedColumn: 'ID',
    };

    //Icons
    protected readonly faBan = faBan;
    protected readonly faSave = faSave;
    protected readonly faFileImport = faFileImport;
    //Other constants
    protected readonly ButtonType = ButtonType;

    constructor(
        private activatedRoute: ActivatedRoute,
        private router: Router,
        private translateService: TranslateService,
        private competencyService: CompetencyService,
        private alertService: AlertService,
    ) {}

    ngOnInit(): void {
        this.activatedRoute.params.subscribe((params) => {
            this.courseId = Number(params['courseId']);
            this.performSearch({ ...this.filter, ...this.search });
        });
    }

    filterChange(filter: CompetencyFilter) {
        this.filter = filter;
        this.performSearch({ ...this.filter, ...this.search });
    }

    searchChange(search: BasePageableSearch) {
        this.search = search;
        this.performSearch({ ...this.filter, ...this.search });
    }

    performSearch(search: CompetencyPageableSearch) {
        this.isLoading = true;
        this.competencyService.getForImport(search).subscribe({
            next: (res) => {
                this.searchedCompetencies = res;
                this.isLoading = false;
            },
            error: (error: HttpErrorResponse) => onError(this.alertService, error),
        });
    }

    isSubmitPossible() {
        //TODO: submit will be possible if: at least 1 competency is selected.
        return true;
    }

    onSubmit() {
        //TODO: service call to save.
    }

    /**
     * Cancels the import and navigates back
     */
    onCancel() {
        this.router.navigate(['../'], { relativeTo: this.activatedRoute });
    }

    canDeactivate() {
        //TODO: implement canDeactivate logic. > if loading || competencies selected || search input(?)
        return true;
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
