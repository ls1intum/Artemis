import { Component, OnDestroy, OnInit } from '@angular/core';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { Subject, Subscription } from 'rxjs';
import { filter, map } from 'rxjs/operators';
import { JhiEventManager } from 'ng-jhipster';

import { AccountService } from 'app/core/auth/account.service';
import { LectureService } from './lecture.service';
import { Lecture } from 'app/entities/lecture.model';
import { ActivatedRoute, Router, NavigationEnd } from '@angular/router';
import { onError } from 'app/shared/util/global.utils';
import { JhiAlertService } from 'ng-jhipster';

@Component({
    selector: 'jhi-lecture',
    templateUrl: './lecture.component.html',
})
export class LectureComponent implements OnInit, OnDestroy {
    lectures: Lecture[];
    currentAccount: any;
    eventSubscriber: Subscription;
    routerEventSubscription?: Subscription;
    courseId: number;
    isVisible: boolean;

    private dialogErrorSource = new Subject<string>();
    dialogError$ = this.dialogErrorSource.asObservable();

    constructor(
        protected lectureService: LectureService,
        private route: ActivatedRoute,
        private router: Router,
        private jhiAlertService: JhiAlertService,
        protected eventManager: JhiEventManager,
        protected accountService: AccountService,
    ) {}

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

    ngOnInit() {
        this.courseId = Number(this.route.snapshot.paramMap.get('courseId'));
        this.loadAll();
        this.accountService.identity().then((account) => {
            this.currentAccount = account;
        });
        this.registerChangeInLectures();
        this.isVisible = this.route.children.length === 0;
        this.routerEventSubscription = this.router.events.pipe(filter((event) => event instanceof NavigationEnd)).subscribe(() => {
            this.isVisible = this.route.children.length === 0;
            if (this.isVisible) {
                this.loadAll();
            }
        });
    }

    ngOnDestroy() {
        this.eventManager.destroy(this.eventSubscriber);
        this.dialogErrorSource.unsubscribe();
        if (this.routerEventSubscription) {
            this.eventManager.destroy(this.routerEventSubscription);
        }
    }

    trackId(index: number, item: Lecture) {
        return item.id;
    }

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
