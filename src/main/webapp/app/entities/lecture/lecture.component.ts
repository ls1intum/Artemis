import { Component, OnInit, OnDestroy } from '@angular/core';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { Subscription } from 'rxjs';
import { filter, map } from 'rxjs/operators';
import { JhiEventManager, JhiAlertService } from 'ng-jhipster';

import { AccountService } from 'app/core';
import { LectureService } from './lecture.service';
import { Lecture } from 'app/entities/lecture';
import { ActivatedRoute } from '@angular/router';

@Component({
    selector: 'jhi-lecture',
    templateUrl: './lecture.component.html',
})
export class LectureComponent implements OnInit, OnDestroy {
    lectures: Lecture[];
    currentAccount: any;
    eventSubscriber: Subscription;
    courseId: number;

    constructor(
        protected lectureService: LectureService,
        private route: ActivatedRoute,
        protected jhiAlertService: JhiAlertService,
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
                (res: HttpErrorResponse) => this.onError(res.message),
            );
    }

    ngOnInit() {
        this.courseId = Number(this.route.snapshot.paramMap.get('courseId'));
        this.loadAll();
        this.accountService.identity().then(account => {
            this.currentAccount = account;
        });
        this.registerChangeInLectures();
    }

    ngOnDestroy() {
        this.eventManager.destroy(this.eventSubscriber);
    }

    trackId(index: number, item: Lecture) {
        return item.id;
    }

    registerChangeInLectures() {
        this.eventSubscriber = this.eventManager.subscribe('lectureListModification', () => this.loadAll());
    }

    protected onError(errorMessage: string) {
        this.jhiAlertService.error(errorMessage, null, undefined);
    }
}
