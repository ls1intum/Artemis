import { Injectable } from '@angular/core';
import { ActivatedRouteSnapshot, Resolve, RouterStateSnapshot, Route } from '@angular/router';
import { JhiResolvePagingParams } from 'ng-jhipster';

import { NotificationMgmtComponent, NotificationMgmtDetailComponent, NotificationMgmtUpdateComponent } from 'app/admin';
import { SystemNotification, SystemNotificationService } from 'app/entities/system-notification';

@Injectable({ providedIn: 'root' })
export class NotificationMgmtResolve implements Resolve<any> {
    constructor(private service: SystemNotificationService) {}

    resolve(route: ActivatedRouteSnapshot, state: RouterStateSnapshot) {
        const id = route.params['id'] ? route.params['id'] : null;
        if (id) {
            return this.service.find(parseInt(id, 10));
        }
        return new SystemNotification();
    }
}

export const notificationMgmtRoutes1: Route = {
    path: 'notification-management',
    component: NotificationMgmtComponent,
    resolve: {
        pagingParams: JhiResolvePagingParams,
    },
    data: {
        pageTitle: 'notificationManagement.home.title',
        defaultSort: 'id,asc',
    },
};

export const notificationMgmtRoutes2: Route = {
    path: 'notification-management/:id/view',
    component: NotificationMgmtDetailComponent,
    resolve: {
        notification: NotificationMgmtResolve,
    },
    data: {
        pageTitle: 'notificationManagement.home.title',
    },
};

export const notificationMgmtRoutes3: Route = {
    path: 'notification-management/new',
    component: NotificationMgmtUpdateComponent,
    resolve: {
        notification: NotificationMgmtResolve,
    },
};

export const notificationMgmtRoutes4: Route = {
    path: 'notification-management/:id/edit',
    component: NotificationMgmtUpdateComponent,
    resolve: {
        notification: NotificationMgmtResolve,
    },
};
