import { Injectable } from '@angular/core';
import { ActivatedRouteSnapshot, Resolve, Route } from '@angular/router';
import { JhiResolvePagingParams } from 'ng-jhipster';
import { SystemNotificationMgmtUpdateComponent } from 'app/admin/system-notification-management/system-notification-management-update.component';
import { SystemNotification } from 'app/entities/system-notification.model';
import { SystemNotificationService } from 'app/core/system-notification/system-notification.service';
import { SystemNotificationMgmtComponent } from 'app/admin/system-notification-management/system-notification-management.component';
import { SystemNotificationMgmtDetailComponent } from 'app/admin/system-notification-management/system-notification-management-detail.component';

@Injectable({ providedIn: 'root' })
export class SystemNotificationMgmtResolve implements Resolve<any> {
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

export const systemNotificationMgmtRoutes1: Route = {
    path: 'system-notification-management',
    component: SystemNotificationMgmtComponent,
    resolve: {
        pagingParams: JhiResolvePagingParams,
    },
    data: {
        pageTitle: 'artemisApp.systemNotification.systemNotifications',
        defaultSort: 'id,asc',
    },
};

export const systemNotificationMgmtRoutes2: Route = {
    path: 'system-notification-management/:id/view',
    component: SystemNotificationMgmtDetailComponent,
    resolve: {
        notification: SystemNotificationMgmtResolve,
    },
    data: {
        pageTitle: 'artemisApp.systemNotification.systemNotifications',
    },
};

export const systemNotificationMgmtRoutes3: Route = {
    path: 'system-notification-management/new',
    component: SystemNotificationMgmtUpdateComponent,
    resolve: {
        notification: SystemNotificationMgmtResolve,
    },
    data: {
        pageTitle: 'artemisApp.systemNotification.systemNotifications',
    },
};

export const systemNotificationMgmtRoutes4: Route = {
    path: 'system-notification-management/:id/edit',
    component: SystemNotificationMgmtUpdateComponent,
    resolve: {
        notification: SystemNotificationMgmtResolve,
    },
    data: {
        pageTitle: 'artemisApp.systemNotification.systemNotifications',
    },
};
