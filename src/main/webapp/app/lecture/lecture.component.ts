import { Component, OnDestroy, OnInit } from '@angular/core';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { Subject, Subscription } from 'rxjs';
import { filter, map } from 'rxjs/operators';
import { JhiEventManager } from 'ng-jhipster';

import { AccountService } from 'app/core/auth/account.service';
import { LectureService } from './lecture.service';
import { Lecture } from 'app/entities/lecture.model';
import { ActivatedRoute } from '@angular/router';
import { onError } from 'app/shared/util/global.utils';
import { AlertService } from 'app/core/alert/alert.service';

@Component({
    selector: 'jhi-lecture',
    templateUrl: './lecture.component.html',
})
export class LectureComponent implements OnInit, OnDestroy {
    lectures: Lecture[];
    currentAccount: any;
    eventSubscriber: Subscription;
    courseId: number;

    private dialogErrorSource = new Subject<string>();
    dialogError$ = this.dialogErrorSource.asObservable();

    constructor(
        protected lectureService: LectureService,
        private route: ActivatedRoute,
        private jhiAlertService: AlertService,
        protected eventManager: JhiEventManager,
        protected accountService: AccountService,
    ) {}

    /**
     * loads all lectures with the given course id from the lecture service
     */
    loadAll() {
        this.lectureService
            .findAllByCourseId(this.courseId)
            .pipe(
                filter((res: HttpResponse<Lecture[]>) => res.ok),
                map((res: HttpResponse<Lecture[]>) => res.body),
            )
            .subscribe(
                (res: Lecture[]) => {
                    this.lectures = res;
                },
                (res: HttpErrorResponse) => onError(this.jhiAlertService, res),
            );
    }

    /**
     * On init get the course id for the relevant course and the user identity from the account service
     * Initialize component by calling loadAll and registerChangeInLectures
     */
    ngOnInit() {
        this.courseId = Number(this.route.snapshot.paramMap.get('courseId'));
        this.loadAll();
        this.accountService.identity().then((account) => {
            this.currentAccount = account;
        });
        this.registerChangeInLectures();
    }

    /**
     * On destroy unsubscribe the subscriptions
     */
    ngOnDestroy() {
        this.eventManager.destroy(this.eventSubscriber);
        this.dialogErrorSource.unsubscribe();
    }

    /**
     * Return id of the passed lecture object
     * @param index - not used
     * @param item - the Lecture object
     */
    trackId(index: number, item: Lecture) {
        return item.id;
    }

    /**
     * Subscribes to the changes of 'lectureListModification' event and load all lectures if it is broadcast
     */
    registerChangeInLectures() {
        this.eventSubscriber = this.eventManager.subscribe('lectureListModification', () => this.loadAll());
    }

    /**
     * Deletes Lecture
     * @param lectureId the id of the lecture
     */
    deleteLecture(lectureId: number) {
        this.lectureService.delete(lectureId).subscribe(
            () => {
                this.eventManager.broadcast({
                    name: 'lectureListModification',
                    content: 'Deleted an lecture',
                });
                this.dialogErrorSource.next('');
            },
            (error: HttpErrorResponse) => this.dialogErrorSource.next(error.message),
        );
    }
}
