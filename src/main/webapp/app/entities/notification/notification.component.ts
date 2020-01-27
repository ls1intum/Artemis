import { Component, OnDestroy, OnInit } from '@angular/core';
import { HttpErrorResponse, HttpHeaders, HttpResponse } from '@angular/common/http';
import { Subscription } from 'rxjs';
import { JhiAlertService, JhiEventManager, JhiParseLinks } from 'ng-jhipster';

import { User } from 'app/core/user/user.model';
import { Lecture } from 'app/entities/lecture';
import { ActivatedRoute, Router } from '@angular/router';
import { NotificationService } from 'app/entities/notification/notification.service';
import { Notification } from 'app/entities/notification/notification.model';
import { ITEMS_PER_PAGE } from 'app/shared';
import { SystemNotification } from 'app/entities/system-notification';
import * as moment from 'moment';
import { AccountService } from 'app/core/auth/account.service';

@Component({
    selector: 'jhi-notification',
    templateUrl: './notification.component.html',
})
export class NotificationComponent implements OnInit, OnDestroy {
    lectures: Lecture[];
    notifications: Notification[];
    currentAccount: any;
    eventSubscriber: Subscription;
    courseId: number;
    routeData: Subscription;
    links: any;
    totalItems: string;
    itemsPerPage: number;
    page: number;
    predicate: string;
    previousPage: number;
    reverse: boolean;

    constructor(
        public notificationService: NotificationService,
        private route: ActivatedRoute,
        protected jhiAlertService: JhiAlertService,
        private router: Router,
        private activatedRoute: ActivatedRoute,
        private alertService: JhiAlertService,
        private parseLinks: JhiParseLinks,
        protected eventManager: JhiEventManager,
        protected accountService: AccountService,
    ) {
        this.itemsPerPage = ITEMS_PER_PAGE;
        this.routeData = this.activatedRoute.data.subscribe(data => {
            this.page = data['pagingParams'].page;
            this.previousPage = data['pagingParams'].page;
            this.reverse = data['pagingParams'].ascending;
            this.predicate = data['pagingParams'].predicate;
        });
    }

    registerChangeInUsers() {
        this.eventManager.subscribe('notificationListModification', () => this.loadAll());
    }

    loadAll() {
        this.notificationService
            .query({
                page: this.page - 1,
                size: this.itemsPerPage,
                sort: this.sort(),
            })
            .subscribe(
                (res: HttpResponse<SystemNotification[]>) => this.onSuccess(res.body!, res.headers),
                (res: HttpErrorResponse) => this.onError(res),
            );
    }

    trackIdentity(index: number, item: User) {
        return item.id;
    }

    isNotificationActive(systemNotification: SystemNotification) {
        return systemNotification.notificationDate!.isBefore(moment()) && systemNotification.expireDate!.isAfter(moment());
    }

    sort() {
        const result = [this.predicate + ',' + (this.reverse ? 'asc' : 'desc')];
        if (this.predicate !== 'id') {
            result.push('id');
        }
        return result;
    }

    loadPage(page: number) {
        if (page !== this.previousPage) {
            this.previousPage = page;
            this.transition();
        }
    }

    private onSuccess(data: SystemNotification[], headers: HttpHeaders) {
        this.links = this.parseLinks.parse(headers.get('link')!);
        this.totalItems = headers.get('X-Total-Count')!;
        this.notifications = data;
    }

    private onError(error: HttpErrorResponse) {
        this.alertService.error(error.error, error.message, undefined);
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
        this.routeData.unsubscribe();
        this.eventManager.destroy(this.eventSubscriber);
    }

    transition() {
        this.router.navigate(['/notifications'], {
            queryParams: {
                page: this.page,
                sort: this.predicate + ',' + (this.reverse ? 'asc' : 'desc'),
            },
        });
        this.loadAll();
    }

    trackId(index: number, item: Lecture) {
        return item.id;
    }

    registerChangeInLectures() {
        this.eventSubscriber = this.eventManager.subscribe('lectureListModification', () => this.loadAll());
    }

    getTargetMessage(target: string): string | null {
        if (!target) {
            return null;
        }
        return JSON.parse(target).message;
    }
}
