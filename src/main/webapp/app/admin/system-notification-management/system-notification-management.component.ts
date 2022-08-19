import { Component, OnDestroy, OnInit } from '@angular/core';
import { HttpErrorResponse, HttpHeaders, HttpResponse } from '@angular/common/http';
import { ActivatedRoute, Router } from '@angular/router';
import { Subscription } from 'rxjs';
import { User } from 'app/core/user/user.model';
import { AccountService } from 'app/core/auth/account.service';
import dayjs from 'dayjs/esm';
import { onError } from 'app/shared/util/global.utils';
import { Subject } from 'rxjs';
import { SystemNotification } from 'app/entities/system-notification.model';
import { ITEMS_PER_PAGE } from 'app/shared/constants/pagination.constants';
import { SystemNotificationService } from 'app/shared/notification/system-notification/system-notification.service';
import { AlertService } from 'app/core/util/alert.service';
import { EventManager } from 'app/core/util/event-manager.service';
import { ParseLinks } from 'app/core/util/parse-links.service';
import { faEye, faPlus, faSort, faTimes, faWrench } from '@fortawesome/free-solid-svg-icons';
import { AdminSystemNotificationService } from 'app/shared/notification/system-notification/admin-system-notification.service';

enum NotificationState {
    SCHEDULED = 'SCHEDULED',
    ACTIVE = 'ACTIVE',
    EXPIRED = 'EXPIRED',
}

@Component({
    selector: 'jhi-system-notification-management',
    templateUrl: './system-notification-management.component.html',
})
export class SystemNotificationManagementComponent implements OnInit, OnDestroy {
    readonly SCHEDULED = NotificationState.SCHEDULED;
    readonly ACTIVE = NotificationState.ACTIVE;
    readonly EXPIRED = NotificationState.EXPIRED;

    currentAccount: User;
    notifications: SystemNotification[];
    error: string;
    success: string;
    routeData: Subscription;
    links: any;
    predicate = 'notificationDate';
    previousPage: number;
    reverse: boolean;

    // page information
    page = 1; // We are at page 1 by default.
    itemsPerPage = ITEMS_PER_PAGE;
    totalItems = 0;

    private dialogErrorSource = new Subject<string>();
    dialogError$ = this.dialogErrorSource.asObservable();

    // Icons
    faSort = faSort;
    faPlus = faPlus;
    faTimes = faTimes;
    faEye = faEye;
    faWrench = faWrench;

    constructor(
        private systemNotificationService: SystemNotificationService,
        private adminSystemNotificationService: AdminSystemNotificationService,
        private alertService: AlertService,
        private accountService: AccountService,
        private parseLinks: ParseLinks,
        private activatedRoute: ActivatedRoute,
        private router: Router,
        private eventManager: EventManager,
    ) {
        this.routeData = this.activatedRoute.data.subscribe((data) => {
            const pagingParams = data['pagingParams'];
            if (pagingParams) {
                this.page = pagingParams.page;
                this.previousPage = pagingParams.page;
                this.reverse = pagingParams.ascending;
                this.predicate = pagingParams.predicate;
            }
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
        this.adminSystemNotificationService.delete(notificationId).subscribe({
            next: () => {
                this.eventManager.broadcast({
                    name: 'notificationListModification',
                    content: 'Deleted a system notification',
                });
                this.dialogErrorSource.next('');
            },
            error: (error: HttpErrorResponse) => this.dialogErrorSource.next(error.message),
        });
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
            .subscribe({
                next: (res: HttpResponse<SystemNotification[]>) => this.onSuccess(res.body!, res.headers),
                error: (res: HttpErrorResponse) => onError(this.alertService, res),
            });
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
     * Checks if notification is currently active, expired or scheduled
     * @param systemNotification which relevance will be checked
     */
    getNotificationState(systemNotification: SystemNotification): NotificationState {
        if (systemNotification.notificationDate!.isAfter(dayjs())) {
            return NotificationState.SCHEDULED;
        } else if (systemNotification.expireDate?.isAfter(dayjs()) ?? true) {
            return NotificationState.ACTIVE;
        } else {
            return NotificationState.EXPIRED;
        }
    }

    /**
     * Sorts parameters by specified order
     */
    sort() {
        const result = [];
        if (this.predicate) {
            result.push(this.predicate + ',' + (this.reverse ? 'asc' : 'desc'));
        }
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
