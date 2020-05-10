import { Component, OnDestroy, OnInit } from '@angular/core';
import { HttpErrorResponse, HttpHeaders, HttpResponse } from '@angular/common/http';
import { Subscription } from 'rxjs';
import { JhiEventManager, JhiParseLinks } from 'ng-jhipster';
import { AlertService } from 'app/core/alert/alert.service';
import { User } from 'app/core/user/user.model';
import { Lecture } from 'app/entities/lecture.model';
import { ActivatedRoute, Router } from '@angular/router';
import { NotificationService } from 'app/overview/notification/notification.service';
import { Notification } from 'app/entities/notification.model';
import * as moment from 'moment';
import { AccountService } from 'app/core/auth/account.service';
import { SystemNotification } from 'app/entities/system-notification.model';
import { ITEMS_PER_PAGE } from 'app/shared/constants/pagination.constants';

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
        protected jhiAlertService: AlertService,
        private router: Router,
        private activatedRoute: ActivatedRoute,
        private alertService: AlertService,
        private parseLinks: JhiParseLinks,
        protected eventManager: JhiEventManager,
        protected accountService: AccountService,
    ) {
        this.itemsPerPage = ITEMS_PER_PAGE;
        this.routeData = this.activatedRoute.data.subscribe((data) => {
            this.page = data['pagingParams'].page;
            this.previousPage = data['pagingParams'].page;
            this.reverse = data['pagingParams'].ascending;
            this.predicate = data['pagingParams'].predicate;
        });
    }

    /**
     * subscribe to changes for notificationListModification
     */
    registerChangeInUsers() {
        this.eventManager.subscribe('notificationListModification', () => this.loadAll());
    }

    /**
     * load all notifications for this page
     */
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

    /**
     * get id of the item
     * @param {number} index
     * @param {User} item
     * @return id of item
     */
    trackIdentity(index: number, item: User) {
        return item.id;
    }

    /**
     * if systemNotification is active
     * @param {SystemNotification} systemNotification
     * @return {boolean}
     */
    isNotificationActive(systemNotification: SystemNotification) {
        return systemNotification.notificationDate!.isBefore(moment()) && systemNotification.expireDate!.isAfter(moment());
    }

    /**
     * sort by id either ascending or descending depending on reverse attribute
     */
    sort() {
        const result = [this.predicate + ',' + (this.reverse ? 'asc' : 'desc')];
        if (this.predicate !== 'id') {
            result.push('id');
        }
        return result;
    }

    /**
     * navigate to page
     * @param {number} page
     */
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

    /**
     * set courseId form route
     * get current account
     * subscribe to lecture changes
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
     * unsubscribe from all listener on destroy
     */
    ngOnDestroy() {
        this.routeData.unsubscribe();
        this.eventManager.destroy(this.eventSubscriber);
    }

    /**
     * navigate to /notifications
     */
    transition() {
        this.router.navigate(['/notifications'], {
            queryParams: {
                page: this.page,
                sort: this.predicate + ',' + (this.reverse ? 'asc' : 'desc'),
            },
        });
        this.loadAll();
    }

    /**
     * return id of lecture
     * @param {number} index
     * @param {Lecture} item
     */
    trackId(index: number, item: Lecture) {
        return item.id;
    }

    /**
     * subscribe to changes in lectures
     */
    registerChangeInLectures() {
        this.eventSubscriber = this.eventManager.subscribe('lectureListModification', () => this.loadAll());
    }

    /**
     * return message from target if present
     * @param {string} target
     * @return {string | null}
     */
    getTargetMessage(target: string): string | null {
        if (!target) {
            return null;
        }
        return JSON.parse(target).message;
    }
}
