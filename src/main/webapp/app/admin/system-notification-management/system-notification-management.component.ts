import { Component, OnDestroy, OnInit } from '@angular/core';
import { HttpErrorResponse, HttpHeaders, HttpResponse } from '@angular/common/http';
import { ActivatedRoute, Router } from '@angular/router';
import { JhiEventManager, JhiParseLinks } from 'ng-jhipster';
import { Subscription } from 'rxjs';
import { User } from 'app/core/user/user.model';
import { UserService } from 'app/core/user/user.service';
import { AccountService } from 'app/core/auth/account.service';
import * as moment from 'moment';
import { onError } from 'app/shared/util/global.utils';
import { Subject } from 'rxjs';
import { SystemNotification } from 'app/entities/system-notification.model';
import { ITEMS_PER_PAGE } from 'app/shared/constants/pagination.constants';
import { SystemNotificationService } from 'app/shared/notification/system-notification/system-notification.service';
import { JhiAlertService } from 'ng-jhipster';

@Component({
    selector: 'jhi-system-notification-management',
    templateUrl: './system-notification-management.component.html',
})
export class SystemNotificationManagementComponent implements OnInit, OnDestroy {
    currentAccount: User;
    notifications: SystemNotification[];
    error: string;
    success: string;
    routeData: Subscription;
    links: any;
    totalItems: number;
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
        this.routeData = this.activatedRoute.data.subscribe((data) => {
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
        this.accountService.identity().then((user: User) => {
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
     * @param item current notification
     */
    trackIdentity(index: number, item: SystemNotification) {
        return item.id ?? -1;
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
        this.router.navigate(['/admin/system-notification-management'], {
            queryParams: {
                page: this.page,
                sort: this.predicate + ',' + (this.reverse ? 'asc' : 'desc'),
            },
        });
        this.loadAll();
    }

    private onSuccess(data: SystemNotification[], headers: HttpHeaders) {
        this.links = this.parseLinks.parse(headers.get('link')!);
        this.totalItems = Number(headers.get('X-Total-Count')!);
        this.notifications = data;
    }
}
