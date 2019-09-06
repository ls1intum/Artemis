import { Component, OnDestroy, OnInit } from '@angular/core';
import { debounceTime, switchMap, tap } from 'rxjs/operators';
import { Subject, Subscription } from 'rxjs';
import { ProgrammingExercise } from 'app/entities/programming-exercise/programming-exercise.model';
import { ProgrammingExercisePagingService } from 'app/entities/programming-exercise/services';
import { ActivatedRoute } from '@angular/router';
import { ProgrammingExercisePopupService } from 'app/entities/programming-exercise/programming-exercise-popup.service';

export interface SearchResult {
    elements: ProgrammingExercise[];
    totalPages: number;
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
        this.search
            .pipe(
                tap(() => (this.loading = true)),
                debounceTime(200),
                switchMap(() => this.pagingService.searchForExercises(this.state)),
            )
            .subscribe(resp => {
                this.content = resp;
                this.total = resp.totalPages * this.state.pageSize;
            });
    }

    set page(page: number) {
        this.setSearchParam({ page });
    }

    get page(): number {
        return this.state.page;
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

    constructor(private route: ActivatedRoute, private programmingExercisePopupService: ProgrammingExercisePopupService) {}

    ngOnInit() {
        this.routeSub = this.route.params.subscribe(() => {
            this.programmingExercisePopupService.open(ProgrammingExerciseImportComponent as Component);
        });
    }

    ngOnDestroy() {
        this.routeSub.unsubscribe();
    }
}
