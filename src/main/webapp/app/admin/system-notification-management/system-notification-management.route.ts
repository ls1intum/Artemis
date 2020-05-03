import { Injectable } from '@angular/core';
import { ActivatedRouteSnapshot, Resolve, Route } from '@angular/router';
import { JhiResolvePagingParams } from 'ng-jhipster';
import { NotificationMgmtUpdateComponent } from 'app/admin/system-notification-management/system-notification-management-update.component';
import { SystemNotification } from 'app/entities/system-notification.model';
import { SystemNotificationService } from 'app/core/system-notification/system-notification.service';
import { NotificationMgmtComponent } from 'app/admin/system-notification-management/system-notification-management.component';
import { NotificationMgmtDetailComponent } from 'app/admin/system-notification-management/system-notification-management-detail.component';

@Injectable({ providedIn: 'root' })
export class NotificationMgmtResolve implements Resolve<any> {
    constructor(private service: SystemNotificationService) {}

    /**
     * Resolves the route and initializes system notification from id route param
     * @param route
     */
    resolve(route: ActivatedRouteSnapshot) {
        const id = route.params['id'] ? route.params['id'] : null;
        if (id) {
            return this.service.find(parseInt(id, 10));
        }
        return new SystemNotification();
    }
}

export const notificationMgmtRoutes1: Route = {
    path: 'system-notification-management',
    component: NotificationMgmtComponent,
    resolve: {
        pagingParams: JhiResolvePagingParams,
    },
    data: {
        pageTitle: 'artemisApp.systemNotification.systemNotifications',
        defaultSort: 'id,asc',
    },
};

export const notificationMgmtRoutes2: Route = {
    path: 'system-notification-management/:id/view',
    component: NotificationMgmtDetailComponent,
    resolve: {
        notification: NotificationMgmtResolve,
    },
    data: {
        pageTitle: 'artemisApp.systemNotification.systemNotifications',
    },
};

export const notificationMgmtRoutes3: Route = {
    path: 'system-notification-management/new',
    component: NotificationMgmtUpdateComponent,
    resolve: {
        notification: NotificationMgmtResolve,
    },
    data: {
        pageTitle: 'artemisApp.systemNotification.systemNotifications',
    },
};

export const notificationMgmtRoutes4: Route = {
    path: 'system-notification-management/:id/edit',
    component: NotificationMgmtUpdateComponent,
    resolve: {
        notification: NotificationMgmtResolve,
    },
    data: {
        pageTitle: 'artemisApp.systemNotification.systemNotifications',
    },
};
