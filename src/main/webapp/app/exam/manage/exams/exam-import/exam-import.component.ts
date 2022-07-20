import { Component, Input, OnInit, ViewChild } from '@angular/core';
import { debounceTime, switchMap, tap } from 'rxjs/operators';
import { Subject } from 'rxjs';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { PageableSearch, SearchResult, SortingOrder } from 'app/shared/table/pageable-table';
import { SortService } from 'app/shared/service/sort.service';
import { faCheck, faSort } from '@fortawesome/free-solid-svg-icons';
import { Exam } from 'app/entities/exam.model';
import { ExamImportPagingService } from 'app/exam/manage/exams/exam-import/exam-import-paging.service';
import { ExamManagementService } from 'app/exam/manage/exam-management.service';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { onError } from 'app/shared/util/global.utils';
import { AlertService } from 'app/core/util/alert.service';
import { ExamExerciseImportComponent } from 'app/exam/manage/exams/exam-exercise-import/exam-exercise-import.component';
import { ExerciseGroup } from 'app/entities/exercise-group.model';

enum TableColumn {
    ID = 'ID',
    TITLE = 'TITLE',
    COURSE_TITLE = 'COURSE_TITLE',
    EXAM_MODE = 'EXAM_MODE',
}

@Component({
    selector: 'jhi-exam-import',
    templateUrl: './exam-import.component.html',
})
export class ExamImportComponent implements OnInit {
    readonly column = TableColumn;

    private search = new Subject<void>();
    private sort = new Subject<void>();

    // boolean to indicate, if the import modal should include the exerciseGroup selection subsequently.
    @Input() subsequentExerciseGroupSelection: boolean;
    // Values to specify the target of the exercise group import
    @Input() targetCourseId?: number;
    @Input() targetExamId?: number;

    @ViewChild(ExamExerciseImportComponent)
    examExerciseImportComponent: ExamExerciseImportComponent;

    exam?: Exam;
    loading = false;
    isImportingExercises = false;

    content: SearchResult<Exam>;
    total = 0;
    state: PageableSearch = {
        page: 1,
        pageSize: 10,
        searchTerm: '',
        sortingOrder: SortingOrder.DESCENDING,
        sortedColumn: TableColumn.ID,
    };

    // Icons
    faSort = faSort;
    faCheck = faCheck;

    constructor(
        private pagingService: ExamImportPagingService,
        private sortService: SortService,
        private activeModal: NgbActiveModal,
        private examManagementService: ExamManagementService,
        private alertService: AlertService,
    ) {}

    ngOnInit(): void {
        this.content = { resultsOnPage: [], numberOfPages: 0 };
        this.performSearch(this.sort, 0);
        this.performSearch(this.search, 300);
    }

    /**
     * After the user has chosen an Exam, this method is called to load the exercise groups for the selected exam
     * @param exam the exam for which the exercise groups should be loaded
     */
    openExerciseSelection(exam: Exam) {
        this.examManagementService.findWithExercisesAndWithoutCourseId(exam.id!).subscribe({
            next: (examRes: HttpResponse<Exam>) => {
                this.exam = examRes.body!;
            },
            error: (res: HttpErrorResponse) => onError(this.alertService, res),
        });
    }

    /**
     * Method to map the Map<ExerciseGroup, Set<Exercises>> selectedExercises to an ExerciseGroup[] with Exercises[] each
     * and to perform the REST-Call to import the ExerciseGroups to the specified exam.
     * Called once when user is importing the exam
     */
    performImportOfExerciseGroups() {
        if (this.subsequentExerciseGroupSelection && this.exam && this.targetExamId && this.targetCourseId) {
            // The validation of the selected exercises is only called when the user desires to import the exam
            if (!this.examExerciseImportComponent.validateUserInput()) {
                this.alertService.error('artemisApp.examManagement.exerciseGroup.importModal.invalidExerciseConfiguration');
                return;
            }
            this.isImportingExercises = true;
            // The child component provides us with the selected exercise groups and exercises
            this.exam.exerciseGroups = this.examExerciseImportComponent.mapSelectedExercisesToExerciseGroups();
            this.examManagementService.importExerciseGroup(this.targetCourseId, this.targetExamId, this.exam.exerciseGroups!).subscribe({
                next: (httpResponse: HttpResponse<ExerciseGroup[]>) => {
                    this.isImportingExercises = false;
                    // Close-Variant 2: Provide the component with all the exercise groups and exercises of the exam
                    this.activeModal.close(httpResponse.body!);
                },
                error: (httpErrorResponse: HttpErrorResponse) => {
                    // Case: Server-Site Validation of the Programming Exercises failed
                    if (httpErrorResponse.error?.errorKey === 'examContainsProgrammingExercisesWithInvalidKey') {
                        // The Server sends back all the exercise groups and exercises and removed the shortName / title for all conflicting programming exercises
                        this.exam!.exerciseGroups = httpErrorResponse.error.params.exerciseGroups!;
                        // The updateMapsAfterRejectedImport Method is called to update the displayed exercises in the child component
                        this.examExerciseImportComponent.updateMapsAfterRejectedImport();
                        const numberOfInvalidProgrammingExercises = httpErrorResponse.error.numberOfInvalidProgrammingExercises;
                        this.alertService.error('artemisApp.examManagement.exerciseGroup.importModal.invalidKey', { number: numberOfInvalidProgrammingExercises });
                    } else {
                        onError(this.alertService, httpErrorResponse);
                    }
                    this.isImportingExercises = false;
                },
            });
        }
    }

    /** Method to perform the search based on a search subject
     *
     * @param searchSubject The search subject which we use to search.
     * @param debounce The delay we apply to delay the feedback / wait for input
     */
    private performSearch(searchSubject: Subject<void>, debounce: number) {
        searchSubject
            .pipe(
                debounceTime(debounce),
                tap(() => (this.loading = true)),
                switchMap(() => this.pagingService.searchForExams(this.state, this.subsequentExerciseGroupSelection)),
            )
            .subscribe((response) => {
                this.content = response;
                this.loading = false;
                this.total = response.numberOfPages * this.state.pageSize;
            });
    }

    set page(page: number) {
        this.setSearchParam({ page });
    }

    get page(): number {
        return this.state.page;
    }

    sortRows() {
        this.sortService.sortByProperty(this.content.resultsOnPage, this.sortedColumn, this.listSorting);
    }

    /**
     * Gives the ID for any exam in the table, so that it can be tracked/identified by ngFor
     *
     * @param index The index of the element in the ngFor
     * @param exam The exercise itself
     * @returns The ID of the exam
     */
    trackId(index: number, exam: Exam): number {
        return exam.id!;
    }

    /** Set the list sorting direction
     *
     * @param ascending {boolean} Ascending order set
     */
    set listSorting(ascending: boolean) {
        const sortingOrder = ascending ? SortingOrder.ASCENDING : SortingOrder.DESCENDING;
        this.setSearchParam({ sortingOrder });
    }

    get listSorting(): boolean {
        return this.state.sortingOrder === SortingOrder.ASCENDING;
    }

    private setSearchParam(patch: Partial<PageableSearch>) {
        Object.assign(this.state, patch);
        this.sort.next();
    }

    set sortedColumn(sortedColumn: string) {
        this.setSearchParam({ sortedColumn });
    }

    get sortedColumn(): string {
        return this.state.sortedColumn;
    }

    set searchTerm(searchTerm: string) {
        this.state.searchTerm = searchTerm;
        this.search.next();
    }

    get searchTerm(): string {
        return this.state.searchTerm;
    }

    /**
     * Closes the modal in which the import component is opened. Gives the selected exercise as a result.
     *
     * @param exam The exercise which was selected by the user for the import.
     */
    forwardSelectedExam(exam: Exam) {
        this.activeModal.close(exam);
    }

    /**
     * Closes the modal in which the import component is opened by dismissing it
     */
    clear() {
        this.activeModal.dismiss('cancel');
    }

    /** Callback function when the user navigates through the page results
     *
     * @param pagenumber The current page number
     */
    onPageChange(pagenumber: number) {
        if (pagenumber) {
            this.page = pagenumber;
        }
    }
}
