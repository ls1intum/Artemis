import { Component, OnDestroy, OnInit } from '@angular/core';
import { switchMap, tap } from 'rxjs/operators';
import { Subject, Subscription } from 'rxjs';
import { ProgrammingExercise } from 'app/entities/programming-exercise/programming-exercise.model';
import { ProgrammingExercisePagingService } from 'app/entities/programming-exercise/services';
import { NgbActiveModal, NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { ActivatedRoute, Router } from '@angular/router';
import { Course, CourseService } from 'app/entities/course';

export interface SearchResult<T> {
    resultsOnPage: T[];
    numberOfPages: number;
}

export enum SortingOrder {
    ASCENDING = 'ASCENDING',
    DESCENDING = 'DESCENDING',
}

export interface PageableSearch {
    page: number;
    pageSize: number;
    searchTerm: string;
    sortingOrder: SortingOrder;
    sortedColumn: string;
}

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

    course: Course;
    loading = false;
    content: SearchResult<ProgrammingExercise>;
    total = 0;
    state: PageableSearch = {
        page: 1,
        pageSize: 2,
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

    callback() {}

    trackId(index: number, item: ProgrammingExercise): number {
        return item.id;
    }

    clear() {
        this.activeModal.dismiss('cancel');
    }

    openImport(exercise: ProgrammingExercise) {
        this.activeModal.close(exercise);
    }
}

@Component({
    selector: 'jhi-programming-exercise-import-popup',
    template: '',
})
export class PorgrammingExerciseImportPopupComponent implements OnInit, OnDestroy {
    private routeSub: Subscription;
    private ngbModalRef: NgbModalRef | null = null;

    constructor(private route: ActivatedRoute, private modalService: NgbModal, private router: Router, private courseService: CourseService) {}

    ngOnInit() {
        this.routeSub = this.route.params.subscribe(params => {
            this.open(params['courseId']);
        });
    }

    private open(courseId: number): Promise<NgbModalRef> {
        return new Promise<NgbModalRef>((resolve, reject) => {
            if (this.ngbModalRef != null) {
                resolve(this.ngbModalRef);
            }

            setTimeout(() => {
                this.courseService.find(courseId).subscribe(res => {
                    const course = res.body!;
                    this.ngbModalRef = this.createImportModalRef(course);
                    resolve(this.ngbModalRef);
                });
            }, 0);
        });
    }

    private createImportModalRef(course: Course): NgbModalRef {
        const modalRef = this.modalService.open(ProgrammingExerciseImportComponent, { size: 'lg', backdrop: 'static' });
        modalRef.componentInstance.course = course;
        modalRef.result.then(
            (result: ProgrammingExercise) => {
                this.router.navigate(['/course', result.course!!.id, 'programming-exercise', result.id, 'import', course.id]);
                this.ngbModalRef = null;
            },
            reason => {
                this.router.navigate([{ outlets: { popup: null } }], { replaceUrl: true, queryParamsHandling: 'merge' });
                this.ngbModalRef = null;
            },
        );
        return modalRef;
    }

    ngOnDestroy() {
        this.routeSub.unsubscribe();
    }
}
