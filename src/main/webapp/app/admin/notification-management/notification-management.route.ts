import { Injectable } from '@angular/core';
import { ActivatedRouteSnapshot, Resolve, Route, RouterStateSnapshot } from '@angular/router';
import { JhiResolvePagingParams } from 'ng-jhipster';
import { NotificationMgmtUpdateComponent } from 'app/admin/notification-management/notification-management-update.component';
import { SystemNotification } from 'app/entities/system-notification/system-notification.model';
import { SystemNotificationService } from 'app/entities/system-notification/system-notification.service';
import { NotificationMgmtComponent } from 'app/admin/notification-management/notification-management.component';
import { NotificationMgmtDetailComponent } from 'app/admin/notification-management/notification-management-detail.component';

@Injectable({ providedIn: 'root' })
export class NotificationMgmtResolve implements Resolve<any> {
    constructor(private service: SystemNotificationService) {}

    /**
     * Resolves the route and initializes system notification from id route param
     * @param route
     * @param state
     */
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
        pageTitle: 'artemisApp.notificationManagement.home.title',
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
        pageTitle: 'artemisApp.notificationManagement.home.title',
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
