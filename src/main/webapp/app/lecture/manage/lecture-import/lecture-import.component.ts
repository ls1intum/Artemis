import { SearchHighlightComponent } from 'app/admin/shared/search-highlight.component';
import { Component, DestroyRef, OnInit, inject, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Subject, debounceTime, switchMap, tap } from 'rxjs';
import { TumAetUiButtonDirective, TumAetUiInputDirective, TumAetUiPaginatorComponent, TumAetUiTableDirective, TumAetUiTableSortableColumnComponent } from '@tumaet/ui-angular';
import { Lecture } from 'app/lecture/shared/entities/lecture.model';
import { LecturePagingService } from 'app/lecture/manage/services/lecture-paging.service';
import { SearchResult, SearchTermPageableSearch, SortingOrder } from 'app/foundation/pagination/pageable-table';
import { TranslateDirective } from 'app/foundation/language/translate.directive';

/** Lecture-specific picker. The caller owns its dialog and receives the chosen lecture. */
@Component({
    selector: 'jhi-lecture-import',
    templateUrl: './lecture-import.component.html',
    imports: [
        FormsModule,
        TumAetUiButtonDirective,
        TumAetUiInputDirective,
        TumAetUiPaginatorComponent,
        TumAetUiTableDirective,
        TumAetUiTableSortableColumnComponent,
        TranslateDirective,
        SearchHighlightComponent,
    ],
})
export class LectureImportComponent implements OnInit {
    private readonly pagingService = inject(LecturePagingService);
    private readonly destroyRef = inject(DestroyRef);

    readonly selected = output<Lecture>();
    readonly loading = signal(false);
    readonly content = signal<SearchResult<Lecture>>({ resultsOnPage: [], numberOfPages: 0 });
    readonly total = signal(0);
    readonly columns = [
        { name: 'TITLE', getProperty: (lecture: Lecture) => lecture.title },
        { name: 'COURSE_TITLE', getProperty: (lecture: Lecture) => lecture.course?.title },
        { name: 'SEMESTER', getProperty: (lecture: Lecture) => lecture.course?.semester },
    ];
    readonly state: SearchTermPageableSearch = { page: 1, pageSize: 10, searchTerm: '', sortingOrder: SortingOrder.DESCENDING, sortedColumn: 'ID' };
    private readonly search = new Subject<void>();
    private readonly sort = new Subject<void>();

    get searchTerm(): string {
        return this.state.searchTerm;
    }
    set searchTerm(value: string) {
        this.state.searchTerm = value;
        this.search.next();
    }
    get sortedColumn(): string {
        return this.state.sortedColumn;
    }
    set sortedColumn(value: string) {
        this.state.sortedColumn = value;
        this.sort.next();
    }
    get listSorting(): boolean {
        return this.state.sortingOrder === SortingOrder.ASCENDING;
    }
    set listSorting(value: boolean) {
        this.state.sortingOrder = value ? SortingOrder.ASCENDING : SortingOrder.DESCENDING;
        this.sort.next();
    }

    ngOnInit(): void {
        this.performSearch(this.sort, 0);
        this.performSearch(this.search, 300);
        this.sort.next();
    }

    onPageChange(page: number): void {
        this.state.page = page;
        this.sort.next();
    }

    selectImport(lecture: Lecture): void {
        this.selected.emit(lecture);
    }

    private performSearch(trigger: Subject<void>, delay: number): void {
        trigger
            .pipe(
                debounceTime(delay),
                tap(() => this.loading.set(true)),
                switchMap(() => this.pagingService.search(this.state)),
                takeUntilDestroyed(this.destroyRef),
            )
            .subscribe((result) => {
                this.content.set(result);
                this.total.set(result.numberOfPages * this.state.pageSize);
                this.loading.set(false);
            });
    }
}
