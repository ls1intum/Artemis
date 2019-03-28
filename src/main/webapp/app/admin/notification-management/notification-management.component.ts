import { Component, OnDestroy, OnInit } from '@angular/core';
import { HttpErrorResponse, HttpHeaders, HttpResponse } from '@angular/common/http';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';

import { ActivatedRoute, Router } from '@angular/router';
import { JhiAlertService, JhiEventManager, JhiParseLinks } from 'ng-jhipster';
import { Subscription } from 'rxjs/Subscription';

import { ITEMS_PER_PAGE } from '../../shared';
import { AccountService, User, UserService } from '../../core';
import { SystemNotification, SystemNotificationService } from 'app/entities/system-notification';
import { UserMgmtDeleteDialogComponent } from 'app/admin';
import { NotificationMgmtDeleteDialogComponent } from 'app/admin/notification-management/notification-management-delete-dialog.component';
import * as moment from 'moment';

@Component({
    selector: 'jhi-notification-mgmt',
    templateUrl: './notification-management.component.html'
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

    constructor(
        private userService: UserService,
        private systemNotificationService: SystemNotificationService,
        private alertService: JhiAlertService,
        private accountService: AccountService,
        private parseLinks: JhiParseLinks,
        private activatedRoute: ActivatedRoute,
        private router: Router,
        private eventManager: JhiEventManager,
        private modalService: NgbModal
    ) {
        this.itemsPerPage = ITEMS_PER_PAGE;
        this.routeData = this.activatedRoute.data.subscribe(data => {
            this.page = data['pagingParams'].page;
            this.previousPage = data['pagingParams'].page;
            this.reverse = data['pagingParams'].ascending;
            this.predicate = data['pagingParams'].predicate;
        });
    }

    ngOnInit() {
        this.accountService.identity().then(user => {
            this.currentAccount = user;
            this.loadAll();
            this.registerChangeInUsers();
        });
    }

    ngOnDestroy() {
        this.routeData.unsubscribe();
    }

    registerChangeInUsers() {
        this.eventManager.subscribe('notificationListModification', () => this.loadAll());
    }

    deleteNotification(notification: SystemNotification) {
        const modalRef = this.modalService.open(NotificationMgmtDeleteDialogComponent, { size: 'lg', backdrop: 'static' });
        modalRef.componentInstance.notification = notification;
        modalRef.result.then(
            result => {
                // Left blank intentionally, nothing to do here
            },
            reason => {
                // Left blank intentionally, nothing to do here
            }
        );
    }

    loadAll() {
        this.systemNotificationService
            .query({
                page: this.page - 1,
                size: this.itemsPerPage,
                sort: this.sort()
            })
            .subscribe((res: HttpResponse<SystemNotification[]>) => this.onSuccess(res.body, res.headers), (res: HttpErrorResponse) => this.onError(res));
    }

    trackIdentity(index: number, item: User) {
        return item.id;
    }

    isNotificationActive(systemNotification: SystemNotification) {
        return systemNotification.notificationDate.isBefore(moment()) && systemNotification.expireDate.isAfter(moment());
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

    transition() {
        this.router.navigate(['/admin/notification-management'], {
            queryParams: {
                page: this.page,
                sort: this.predicate + ',' + (this.reverse ? 'asc' : 'desc')
            }
        });
        this.loadAll();
    }

    private onSuccess(data: SystemNotification[], headers: HttpHeaders) {
        this.links = this.parseLinks.parse(headers.get('link'));
        this.totalItems = headers.get('X-Total-Count');
        this.notifications = data;
    }

    private onError(error: HttpErrorResponse) {
        this.alertService.error(error.error, error.message, null);
    }
}
