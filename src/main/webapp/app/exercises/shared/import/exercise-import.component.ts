import { Component, Injector, Input, OnInit } from '@angular/core';
import { faCheck, faSort } from '@fortawesome/free-solid-svg-icons';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { Exercise, ExerciseType } from 'app/entities/exercise.model';
import { ProgrammingExercise, ProgrammingLanguage } from 'app/entities/programming-exercise.model';
import { ModelingExercisePagingService } from 'app/exercises/modeling/manage/modeling-exercise-paging.service';
import { CodeAnalysisPagingService } from 'app/exercises/programming/manage/services/code-analysis-paging.service';
import { ProgrammingExercisePagingService } from 'app/exercises/programming/manage/services/programming-exercise-paging.service';
import { QuizExercisePagingService } from 'app/exercises/quiz/manage/quiz-exercise-paging.service';
import { ExercisePagingService } from 'app/exercises/shared/manage/exercise-paging.service';
import { TextExercisePagingService } from 'app/exercises/text/manage/text-exercise/text-exercise-paging.service';
import { SortService } from 'app/shared/service/sort.service';
import { PageableSearch, SearchResult, SortingOrder } from 'app/shared/table/pageable-table';
import { Subject } from 'rxjs';
import { debounceTime, switchMap, tap } from 'rxjs/operators';
import { FileUploadExercisePagingService } from 'app/exercises/file-upload/manage/file-upload-exercise-paging.service';

export type TableColumn = 'ID' | 'TITLE' | 'COURSE_TITLE' | 'EXAM_TITLE' | 'PROGRAMMING_LANGUAGE';

@Component({
    selector: 'jhi-exercise-import',
    templateUrl: './exercise-import.component.html',
})
export class ExerciseImportComponent implements OnInit {
    readonly ExerciseType = ExerciseType;
    private readonly DEFAULT_SORT_COLUMN: TableColumn = 'ID';

    @Input() exerciseType?: ExerciseType;

    /**
     * The programming language is only set when filtering for exercises with SCA enabled.
     * In this case we only want to display exercises with the given language
     */
    @Input()
    programmingLanguage?: ProgrammingLanguage;

    private search = new Subject<void>();
    private sort = new Subject<void>();

    private pagingService: ExercisePagingService<Exercise>;

    loading = false;
    content: SearchResult<Exercise>;
    total = 0;
    state: PageableSearch = {
        page: 1,
        pageSize: 10,
        searchTerm: '',
        sortingOrder: SortingOrder.DESCENDING,
        sortedColumn: this.DEFAULT_SORT_COLUMN,
    };

    // Icons
    faSort = faSort;
    faCheck = faCheck;

    isCourseFilter = true;
    isExamFilter = true;

    titleKey: string;

    constructor(
        private sortService: SortService,
        private activeModal: NgbActiveModal,
        private injector: Injector,
    ) {}

    ngOnInit(): void {
        if (!this.exerciseType) {
            return;
        }
        this.pagingService = this.getPagingService();
        if (this.programmingLanguage) {
            this.titleKey = 'artemisApp.programmingExercise.configureGrading.categories.importLabel';
        } else {
            this.titleKey =
                this.exerciseType === ExerciseType.FILE_UPLOAD ? `artemisApp.fileUploadExercise.home.importLabel` : `artemisApp.${this.exerciseType}Exercise.home.importLabel`;
        }
        this.content = { resultsOnPage: [], numberOfPages: 0 };

        this.performSearch(this.sort, 0);
        this.performSearch(this.search, 300);
    }

    private getPagingService(): ExercisePagingService<Exercise> {
        switch (this.exerciseType) {
            case ExerciseType.MODELING:
                return this.injector.get(ModelingExercisePagingService);
            case ExerciseType.PROGRAMMING:
                if (this.programmingLanguage) {
                    return this.injector.get(CodeAnalysisPagingService);
                }
                return this.injector.get(ProgrammingExercisePagingService);
            case ExerciseType.QUIZ:
                return this.injector.get(QuizExercisePagingService);
            case ExerciseType.TEXT:
                return this.injector.get(TextExercisePagingService);
            case ExerciseType.FILE_UPLOAD:
                return this.injector.get(FileUploadExercisePagingService);
            default:
                throw new Error('Unsupported exercise type: ' + this.exerciseType);
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
                switchMap(() => this.pagingService.searchForExercises(this.state, this.isCourseFilter, this.isExamFilter, this.programmingLanguage)),
            )
            .subscribe((resp: SearchResult<Exercise>) => {
                this.content = resp;
                this.loading = false;
                this.total = resp.numberOfPages * this.state.pageSize;
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
     * Gives the ID for any exercise in the table, so that it can be tracked/identified by ngFor
     *
     * @param index The index of the element in the ngFor
     * @param item The exercise itself
     * @returns The ID of the exercise
     */
    trackId(index: number, item: Exercise): number {
        return item.id!;
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

    set sortedColumn(sortedColumn: TableColumn) {
        if (sortedColumn === 'COURSE_TITLE') {
            if (this.isExamFilter && !this.isCourseFilter) {
                sortedColumn = 'EXAM_TITLE';
            }
            // sort by course / exam title is not possible if course and exam exercises are mixed
        }
        this.setSearchParam({ sortedColumn });
    }

    get sortedColumn(): TableColumn {
        return this.state.sortedColumn as TableColumn;
    }

    set searchTerm(searchTerm: string) {
        this.state.searchTerm = searchTerm;
        this.search.next();
    }

    get searchTerm(): string {
        return this.state.searchTerm;
    }

    onCourseFilterChange() {
        this.isCourseFilter = !this.isCourseFilter;
        this.resetSortOnFilterChange();
        this.search.next();
    }

    onExamFilterChange() {
        this.isExamFilter = !this.isExamFilter;
        this.resetSortOnFilterChange();
        this.search.next();
    }

    // reset to default search option when mixing course and exam exercises.
    // This avoids exercises still being filtered out by the sortedColum even if the filter is not set.
    private resetSortOnFilterChange() {
        if (this.sortedColumn === 'COURSE_TITLE' || this.sortedColumn === 'EXAM_TITLE') {
            this.sortedColumn = this.DEFAULT_SORT_COLUMN;
        }
    }

    /**
     * Closes the modal in which the import component is opened. Gives the selected exercise as a result.
     *
     * @param exercise The exercise which was selected by the user for the import.
     */
    openImport(exercise: Exercise) {
        this.activeModal.close(exercise);
    }

    /**
     * Closes the modal in which the import component is opened by dismissing it
     */
    clear() {
        this.activeModal.dismiss('cancel');
    }

    /** Callback function when the user navigates through the page results
     *
     * @param pageNumber The current page number
     */
    onPageChange(pageNumber: number) {
        if (pageNumber) {
            this.page = pageNumber;
        }
    }

    asProgrammingExercise(exercise: Exercise): ProgrammingExercise {
        return exercise as ProgrammingExercise;
    }
}
