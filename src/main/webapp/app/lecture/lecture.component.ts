import dayjs from 'dayjs/esm';
import { Component, OnDestroy, OnInit } from '@angular/core';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { Subject, Subscription } from 'rxjs';
import { filter, map } from 'rxjs/operators';
import { AccountService } from 'app/core/auth/account.service';
import { LectureService } from './lecture.service';
import { Lecture } from 'app/entities/lecture.model';
import { ActivatedRoute, Router } from '@angular/router';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { onError } from 'app/shared/util/global.utils';
import { AlertService } from 'app/core/util/alert.service';
import { EventManager } from 'app/core/util/event-manager.service';
import { faFile, faPencilAlt, faPlus, faPuzzlePiece, faTimes, faFilter } from '@fortawesome/free-solid-svg-icons';
import { LectureImportComponent } from 'app/lecture/lecture-import.component';

export enum LectureDateFilter {
    PAST = 'filterPast',
    CURRENT = 'filterCurrent',
    FUTURE = 'filterFuture',
    UNSPECIFIED = 'filterUnspecifiedDates',
}

@Component({
    selector: 'jhi-lecture',
    templateUrl: './lecture.component.html',
})
export class LectureComponent implements OnInit, OnDestroy {
    lectures: Lecture[];
    filteredLectures: Lecture[];
    currentAccount: any;
    eventSubscriber: Subscription;
    courseId: number;

    private dialogErrorSource = new Subject<string>();
    dialogError$ = this.dialogErrorSource.asObservable();

    activeFilters: Set<LectureDateFilter>;
    readonly filterType = LectureDateFilter;

    // Icons
    faPlus = faPlus;
    faTimes = faTimes;
    faPencilAlt = faPencilAlt;
    faFile = faFile;
    faPuzzlePiece = faPuzzlePiece;
    faFilter = faFilter;

    constructor(
        protected lectureService: LectureService,
        private route: ActivatedRoute,
        private router: Router,
        private alertService: AlertService,
        protected eventManager: EventManager,
        protected accountService: AccountService,
        private modalService: NgbModal,
    ) {}

    loadAll() {
        this.lectureService
            .findAllByCourseId(this.courseId)
            .pipe(
                filter((res: HttpResponse<Lecture[]>) => res.ok),
                map((res: HttpResponse<Lecture[]>) => res.body),
            )
            .subscribe({
                next: (res: Lecture[]) => {
                    this.lectures = res;
                    this.filteredLectures = res;
                },
                error: (res: HttpErrorResponse) => onError(this.alertService, res),
            });
    }

    ngOnInit() {
        this.courseId = Number(this.route.snapshot.paramMap.get('courseId'));
        this.loadAll();
        this.accountService.identity().then((account) => {
            this.currentAccount = account;
        });
        this.activeFilters = new Set([LectureDateFilter.PAST, LectureDateFilter.CURRENT, LectureDateFilter.FUTURE, LectureDateFilter.UNSPECIFIED]);
        this.registerChangeInLectures();
    }

    ngOnDestroy() {
        this.eventManager.destroy(this.eventSubscriber);
        this.dialogErrorSource.unsubscribe();
    }

    trackId(index: number, item: Lecture) {
        return item.id;
    }

    registerChangeInLectures() {
        this.eventSubscriber = this.eventManager.subscribe('lectureListModification', () => this.loadAll());
    }

    openImportModal() {
        const modalRef = this.modalService.open(LectureImportComponent, { size: 'lg', backdrop: 'static' });
        modalRef.result.then(
            (result: Lecture) => {
                this.lectureService
                    .import(this.courseId, result.id!)
                    .pipe(
                        filter((res: HttpResponse<Lecture>) => res.ok),
                        map((res: HttpResponse<Lecture>) => res.body),
                    )
                    .subscribe({
                        next: (res: Lecture) => {
                            this.lectures.push(res);
                        },
                        error: (res: HttpErrorResponse) => onError(this.alertService, res),
                    });
            },
            () => {},
        );
    }

    /**
     * Deletes Lecture
     * @param lectureId the id of the lecture
     */
    deleteLecture(lectureId: number) {
        this.lectureService.delete(lectureId).subscribe({
            next: () => {
                this.eventManager.broadcast({
                    name: 'lectureListModification',
                    content: 'Deleted an lecture',
                });
                this.dialogErrorSource.next('');
            },
            error: (error: HttpErrorResponse) => this.dialogErrorSource.next(error.message),
        });
    }

    /**
     * Filters Lectures
     * @param filterChecked the filter checkbox that was clicked
     */
    applyFilters(): void {
        // Get the current system time
        const currentTime = dayjs();
        // Initialize empty arrays for filtered Lectures
        let filteredLectures: Array<Lecture> = [];

        // update filteredLectures based on the selected filter option checkboxes
        const pastLectures = this.lectures.filter((lecture) => lecture.startDate !== undefined && lecture.endDate?.isBefore(dayjs(currentTime)));
        const currentLectures = this.lectures.filter((lecture) => lecture.startDate?.isSameOrBefore(dayjs(currentTime)) && lecture.endDate?.isAfter(dayjs(currentTime)));
        const futureLectures = this.lectures.filter((lecture) => lecture.endDate !== undefined && lecture.startDate?.isAfter(dayjs(currentTime)));
        const unspecifiedDatesLectures = this.lectures.filter((lecture) => lecture.startDate === undefined || lecture.endDate === undefined);

        filteredLectures = this.activeFilters.has(LectureDateFilter.PAST) ? filteredLectures.concat(pastLectures) : filteredLectures;
        filteredLectures = this.activeFilters.has(LectureDateFilter.CURRENT) ? filteredLectures.concat(currentLectures) : filteredLectures;
        filteredLectures = this.activeFilters.has(LectureDateFilter.FUTURE) ? filteredLectures.concat(futureLectures) : filteredLectures;
        filteredLectures = this.activeFilters.has(LectureDateFilter.UNSPECIFIED) ? filteredLectures.concat(unspecifiedDatesLectures) : filteredLectures;
        filteredLectures.sort((first, second) => 0 - (first.id! < second.id! ? 1 : -1));
        this.filteredLectures = filteredLectures;
    }

    /**
     * Filters all displayed lectures by applying the selected activeFilters
     * @param filters: The filters which should be applied.
     */
    toggleFilters(filters: LectureDateFilter[]) {
        filters.forEach((f) => (this.activeFilters.has(f) ? this.activeFilters.delete(f) : this.activeFilters.add(f)));
        this.applyFilters();
    }
}
