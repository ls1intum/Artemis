import { Component, OnInit } from '@angular/core';
import { switchMap, tap } from 'rxjs/operators';
import { Subject } from 'rxjs';
import { ProgrammingExercise } from 'app/entities/programming-exercise/programming-exercise.model';
import { ProgrammingExercisePagingService } from 'app/entities/programming-exercise/services';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { PageableSearch, SearchResult, SortingOrder } from 'app/components/table';

enum TableColumn {
    ID = 'ID',
    TITLE = 'TITLE',
    PROGRAMMING_LANGUAGE = 'PROGRAMMING_LANGUAGE',
    COURSE_TITLE = 'COURSE_TITLE',
}

@Component({
    selector: 'jhi-programming-exercise-import',
    templateUrl: './programming-exercise-import.component.html',
    styles: [],
})
export class ProgrammingExerciseImportComponent implements OnInit {
    readonly column = TableColumn;

    private search = new Subject<void>();

    loading = false;
    content: SearchResult<ProgrammingExercise>;
    total = 0;
    state: PageableSearch = {
        page: 1,
        pageSize: 10,
        searchTerm: '',
        sortingOrder: SortingOrder.DESCENDING,
        sortedColumn: TableColumn.ID,
    };

    constructor(private pagingService: ProgrammingExercisePagingService, private activeModal: NgbActiveModal) {}

    ngOnInit() {
        this.content = { resultsOnPage: [], numberOfPages: 0 };

        this.search
            .pipe(
                tap(() => (this.loading = true)),
                switchMap(() => this.pagingService.searchForExercises(this.state)),
            )
            .subscribe(resp => {
                this.content = resp;
                this.loading = false;
                this.total = resp.numberOfPages * this.state.pageSize;
            });
    }

    set page(page: number) {
        page = page - 1;
        this.setSearchParam({ page });
    }

    get page(): number {
        return this.state.page + 1;
    }

    set searchTerm(searchTerm: string) {
        this.setSearchParam({ searchTerm });
    }

    get searchTerm(): string {
        return this.state.searchTerm;
    }

    set listSorting(ascending: boolean) {
        const sortingOrder = ascending ? SortingOrder.ASCENDING : SortingOrder.DESCENDING;
        this.setSearchParam({ sortingOrder });
    }

    get listSorting(): boolean {
        return this.state.sortingOrder === SortingOrder.ASCENDING;
    }

    set sortedColumn(sortedColumn: string) {
        this.setSearchParam({ sortedColumn });
    }

    get sortedColumn(): string {
        return this.state.sortedColumn;
    }

    private setSearchParam(patch: Partial<PageableSearch>) {
        Object.assign(this.state, patch);
        this.search.next();
    }

    /**
     * Callback after sorting the table. Since no callback is needed atm, this is empty
     */
    callback() {}

    /**
     * Gives the ID for any programming exercise in the table, so that it can be tracked/identified by ngFor
     *
     * @param index The index of the elemnt in the ngFor
     * @param item The exercise itself
     * @returns The ID of the programming exercise
     */
    trackId(index: number, item: ProgrammingExercise): number {
        return item.id;
    }

    /**
     * Closes the modal in which the import component is opened by dismissing it
     */
    clear() {
        this.activeModal.dismiss('cancel');
    }

    /**
     * Closes the modal in which the import component is opened. Gives the selected exercise as a result.
     *
     * @param exercise The exercise which was selected by the user for the import.
     */
    openImport(exercise: ProgrammingExercise) {
        this.activeModal.close(exercise);
    }
}
