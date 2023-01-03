import { Component, Injector, Input, OnInit } from '@angular/core';
import { faCheck, faSort } from '@fortawesome/free-solid-svg-icons';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { Exercise, ExerciseType } from 'app/entities/exercise.model';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { ModelingExercisePagingService } from 'app/exercises/modeling/manage/modeling-exercise-paging.service';
import { ProgrammingExercisePagingService } from 'app/exercises/programming/manage/services/programming-exercise-paging.service';
import { QuizExercisePagingService } from 'app/exercises/quiz/manage/quiz-exercise-paging.service';
import { TextExercisePagingService } from 'app/exercises/text/manage/text-exercise/text-exercise-paging.service';
import { SortService } from 'app/shared/service/sort.service';
import { PageableSearch, SearchResult, SortingOrder } from 'app/shared/table/pageable-table';
import { Subject } from 'rxjs';
import { debounceTime, switchMap, tap } from 'rxjs/operators';

export enum TableColumn {
    ID = 'ID',
    TITLE = 'TITLE',
    COURSE_TITLE = 'COURSE_TITLE',
    PROGRAMMING_LANGUAGE = 'PROGRAMMING_LANGUAGE',
}

@Component({
    selector: 'jhi-exercise-import',
    templateUrl: './exercise-import.component.html',
})
export class ExerciseImportComponent implements OnInit {
    readonly ExerciseType = ExerciseType;
    readonly column = TableColumn;

    @Input()
    exerciseType?: ExerciseType;

    private search = new Subject<void>();
    private sort = new Subject<void>();

    private pagingService: TextExercisePagingService | ProgrammingExercisePagingService | QuizExercisePagingService | ModelingExercisePagingService;

    loading = false;
    content: SearchResult<Exercise>;
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

    isCourseFilter = true;
    isExamFilter = true;

    titleKey: string;

    constructor(private sortService: SortService, private activeModal: NgbActiveModal, private injector: Injector) {}

    ngOnInit(): void {
        if (!this.exerciseType || this.exerciseType == ExerciseType.FILE_UPLOAD) {
            return;
        }
        switch (this.exerciseType) {
            case ExerciseType.MODELING:
                this.pagingService = this.injector.get(ModelingExercisePagingService);
                break;
            case ExerciseType.PROGRAMMING:
                this.pagingService = this.injector.get(ProgrammingExercisePagingService);
                break;
            case ExerciseType.QUIZ:
                this.pagingService = this.injector.get(QuizExercisePagingService);
                break;
            case ExerciseType.TEXT:
                this.pagingService = this.injector.get(TextExercisePagingService);
                break;
            default:
                throw new Error('Unsupported exercise type: ' + this.exerciseType);
        }

        this.titleKey = `artemisApp.${this.exerciseType}Exercise.home.importLabel`;
        this.content = { resultsOnPage: [], numberOfPages: 0 };

        this.performSearch(this.sort, 0);
        this.performSearch(this.search, 300);
    }

    /** Method to perform the search based on a search subject
     *
     * @param searchSubject The search subject which we use to search.
     * @param debounce The delay we apply to delay the feedback / wait for input
     */
    protected performSearch(searchSubject: Subject<void>, debounce: number) {
        searchSubject
            .pipe(
                debounceTime(debounce),
                tap(() => (this.loading = true)),
                switchMap(() => this.pagingService.searchForExercises(this.state, this.isCourseFilter, this.isExamFilter)),
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

    onCourseFilterChange() {
        this.isCourseFilter = !this.isCourseFilter;
        this.search.next();
    }

    onExamFilterChange() {
        this.isExamFilter = !this.isExamFilter;
        this.search.next();
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

    asProgrammingExercise(exercise: Exercise): ProgrammingExercise | undefined {
        if (exercise.type == ExerciseType.PROGRAMMING) {
            return exercise as ProgrammingExercise;
        }
        return undefined;
    }
}
