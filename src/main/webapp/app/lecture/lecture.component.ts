import dayjs from 'dayjs/esm';
import { Component, OnDestroy, OnInit } from '@angular/core';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { Subject, Subscription } from 'rxjs';
import { filter, map } from 'rxjs/operators';
import { AccountService } from 'app/core/auth/account.service';
import { LectureService } from './lecture.service';
import { Lecture } from 'app/entities/lecture.model';
import { ActivatedRoute, Router } from '@angular/router';
import { faFilter } from '@fortawesome/free-solid-svg-icons';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { onError } from 'app/shared/util/global.utils';
import { AlertService } from 'app/core/util/alert.service';
import { EventManager } from 'app/core/util/event-manager.service';
import { faFile, faPencilAlt, faPlus, faPuzzlePiece, faTimes } from '@fortawesome/free-solid-svg-icons';
import { LectureImportComponent } from 'app/lecture/lecture-import.component';

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

    // Icons
    faPlus = faPlus;
    faTimes = faTimes;
    faPencilAlt = faPencilAlt;
    faFile = faFile;
    faPuzzlePiece = faPuzzlePiece;
    faFilter = faFilter;
    // Filter options checkbox state (checked by default)
    private filterPast = true;
    private filterCurrent = true;
    private filterFuture = true;

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

    public applyFilters(filterChecked: string): void {
        // Get the current system time
        const currentTime = dayjs();
        // Initialize empty arrays for filtered Lectures
        let filteredLectures: Array<Lecture> = [];
        let pastLectures: Array<Lecture> = [];
        let currentLectures: Array<Lecture> = [];
        let futureLectures: Array<Lecture> = [];

        // handle checkbox toggle
        switch (filterChecked) {
            case 'filterPast':
                this.filterPast = !this.filterPast;
                break;
            case 'filterCurrent':
                this.filterCurrent = !this.filterCurrent;
                break;
            case 'filterFuture':
                this.filterFuture = !this.filterFuture;
                break;
        }

        // update filteredLectures based on the selected filter option checkboxes
        pastLectures = this.lectures.filter((lecture) => lecture.endDate?.isBefore(dayjs(currentTime)));
        currentLectures = this.lectures.filter((lecture) => lecture.startDate?.isSameOrBefore(dayjs(currentTime)) && lecture.endDate?.isAfter(dayjs(currentTime)));
        futureLectures = this.lectures.filter((lecture) => lecture.startDate?.isAfter(dayjs(currentTime)));

        filteredLectures = this.filterPast ? filteredLectures.concat(pastLectures) : filteredLectures;
        filteredLectures = this.filterCurrent ? filteredLectures.concat(currentLectures) : filteredLectures;
        filteredLectures = this.filterFuture ? filteredLectures.concat(futureLectures) : filteredLectures;

        this.filteredLectures = filteredLectures;
    }
}
