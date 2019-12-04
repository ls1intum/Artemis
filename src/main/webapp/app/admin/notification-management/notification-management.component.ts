import { Component, OnDestroy, OnInit } from '@angular/core';
import { HttpErrorResponse, HttpHeaders, HttpResponse } from '@angular/common/http';

import { ActivatedRoute, Router } from '@angular/router';
import { JhiAlertService, JhiEventManager, JhiParseLinks } from 'ng-jhipster';
import { Subscription } from 'rxjs/Subscription';

import { ITEMS_PER_PAGE } from 'app/shared';
import { User } from 'app/core';
import { UserService } from 'app/core/user/user.service';
import { AccountService } from 'app/core/auth/account.service';
import { SystemNotification, SystemNotificationService } from 'app/entities/system-notification';
import * as moment from 'moment';
import { onError } from 'app/utils/global.utils';
import { Subject } from 'rxjs';

@Component({
    selector: 'jhi-notification-mgmt',
    templateUrl: './notification-management.component.html',
})
export class NotificationMgmtComponent implements OnInit, OnDestroy {
    currentAccount: User;
    notifications: SystemNotification[];
    error: string;
    success: string;
    routeData: Subscription;
    links: any;
    totalItems: string;
    itemsPerPage: number;
    page: number;
    predicate: string;
    previousPage: number;
    reverse: boolean;

    private dialogErrorSource = new Subject<string>();
    dialogError$ = this.dialogErrorSource.asObservable();

    constructor(
        private userService: UserService,
        private systemNotificationService: SystemNotificationService,
        private alertService: JhiAlertService,
        private accountService: AccountService,
        private parseLinks: JhiParseLinks,
        private activatedRoute: ActivatedRoute,
        private router: Router,
        private eventManager: JhiEventManager,
    ) {
        this.itemsPerPage = ITEMS_PER_PAGE;
        this.routeData = this.activatedRoute.data.subscribe(data => {
            this.page = data['pagingParams'].page;
            this.previousPage = data['pagingParams'].page;
            this.reverse = data['pagingParams'].ascending;
            this.predicate = data['pagingParams'].predicate;
        });
    }

    /**
     * Initializes current account and system notifications
     */
    ngOnInit() {
        this.accountService.identity().then(user => {
            this.currentAccount = user!;
            this.loadAll();
            this.registerChangeInUsers();
        });
    }

    /**
     * Unsubscribe from routeData on component destruction
     */
    ngOnDestroy() {
        this.routeData.unsubscribe();
        this.dialogErrorSource.unsubscribe();
    }

    /**
     * Reloads notifications changes whenever notification list is modified
     */
    registerChangeInUsers() {
        this.eventManager.subscribe('notificationListModification', () => this.loadAll());
    }

    /**
     * Deletes notification
     * @param notificationId the id of the notification that we want to delete
     */
    deleteNotification(notificationId: number) {
        this.systemNotificationService.delete(notificationId).subscribe(
            () => {
                this.eventManager.broadcast({
                    name: 'notificationListModification',
                    content: 'Deleted a system notification',
                });
                this.dialogErrorSource.next('');
            },
            (error: HttpErrorResponse) => this.dialogErrorSource.next(error.message),
        );
    }

    /**
     * Loads system notifications
     */
    loadAll() {
        this.systemNotificationService
            .query({
                page: this.page - 1,
                size: this.itemsPerPage,
                sort: this.sort(),
            })
            .subscribe(
                (res: HttpResponse<SystemNotification[]>) => this.onSuccess(res.body!, res.headers),
                (res: HttpErrorResponse) => onError(this.alertService, res),
            );
    }

    /**
     * Returns the unique identifier for items in the collection
     * @param index of a user in the collection
     * @param item current user
     */
    trackIdentity(index: number, item: User) {
        return item.id;
    }

    /**
     * Checks if notification is still relevant
     * @param systemNotification which relevance will be checked
     */
    isNotificationActive(systemNotification: SystemNotification) {
        return systemNotification.notificationDate!.isBefore(moment()) && systemNotification.expireDate!.isAfter(moment());
    }

    /**
     * Sorts parameters by specified order
     */
    sort() {
        const result = [this.predicate + ',' + (this.reverse ? 'asc' : 'desc')];
        if (this.predicate !== 'id') {
            result.push('id');
        }
        return result;
    }

    /**
     * Loads specified page, if it is not the same as previous one
     * @param page number of the page that will be loaded
     */
    loadPage(page: number) {
        if (page !== this.previousPage) {
            this.previousPage = page;
            this.transition();
        }
    }

    /**
     * Transitions to another page and/or sorting order
     */
    transition() {
        this.router.navigate(['/admin/notification-management'], {
            queryParams: {
                page: this.page,
                sort: this.predicate + ',' + (this.reverse ? 'asc' : 'desc'),
            },
        });
        this.loadAll();
    }

    private onSuccess(data: SystemNotification[], headers: HttpHeaders) {
        this.links = this.parseLinks.parse(headers.get('link')!);
        this.totalItems = headers.get('X-Total-Count')!;
        this.notifications = data;
    }
}
