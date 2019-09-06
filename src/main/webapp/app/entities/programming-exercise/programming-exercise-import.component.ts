import { Component, OnDestroy, OnInit } from '@angular/core';
import { debounceTime, switchMap, tap } from 'rxjs/operators';
import { Subject, Subscription } from 'rxjs';
import { ProgrammingExercise } from 'app/entities/programming-exercise/programming-exercise.model';
import { ProgrammingExercisePagingService } from 'app/entities/programming-exercise/services';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';

export interface SearchResult {
    exercisesOnPage: ProgrammingExercise[];
    numberOfPages: number;
}

export enum SortingOrder {
    ASCENDING = 'ASCENDING',
    DESCENDING = 'DESCENDING',
}

export interface PageableSearch {
    page: number;
    pageSize: number;
    partialTitle: string;
    sortingOrder: SortingOrder;
    sortColumn: string;
}

@Component({
    selector: 'jhi-programming-exercise-import',
    templateUrl: './programming-exercise-import.component.html',
    styles: [],
})
export class ProgrammingExerciseImportComponent implements OnInit {
    private search = new Subject<void>();

    loading = false;
    content: SearchResult;
    total = 0;
    state: PageableSearch = {
        page: 1,
        pageSize: 10,
        partialTitle: '',
        sortingOrder: SortingOrder.DESCENDING,
        sortColumn: 'id',
    };

    constructor(private pagingService: ProgrammingExercisePagingService) {}

    ngOnInit() {
        this.content = { exercisesOnPage: [], numberOfPages: 1 };

        this.search
            .pipe(
                tap(() => (this.loading = true)),
                debounceTime(200),
                switchMap(() => this.pagingService.searchForExercises(this.state)),
            )
            .subscribe(resp => {
                console.log('RESP = ' + resp);
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

    set searchTerm(partialTitle: string) {
        this.setSearchParam({ partialTitle });
    }

    get searchTerm(): string {
        return this.state.partialTitle;
    }

    set listSorting(ascending: boolean) {
        const sortingOrder = ascending ? SortingOrder.ASCENDING : SortingOrder.DESCENDING;
        this.setSearchParam({ sortingOrder });
    }

    get listSorting(): boolean {
        return this.state.sortingOrder === SortingOrder.ASCENDING;
    }

    set sortedColumn(sortColumn: string) {
        this.setSearchParam({ sortColumn });
    }

    get sortedColumn(): string {
        return this.state.sortColumn;
    }

    private setSearchParam(patch: Partial<PageableSearch>) {
        Object.assign(this.state, patch);
        this.search.next();
    }

    callback() {}

    trackId(index: number, item: ProgrammingExercise): number {
        return item.id;
    }
}

@Component({
    selector: 'jhi-programming-exercise-import-popup',
    template: '',
})
export class PorgrammingExerciseImportPopupComponent implements OnInit, OnDestroy {
    routeSub: Subscription;

    constructor(private modalRef: NgbModal) {}

    ngOnInit() {
        this.modalRef.open(ProgrammingExerciseImportComponent as Component, { size: 'lg', backdrop: 'static' });
    }

    ngOnDestroy() {
        this.routeSub.unsubscribe();
    }
}
