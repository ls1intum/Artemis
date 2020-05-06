import { Injectable } from '@angular/core';
import { ActivatedRouteSnapshot, Resolve, Route } from '@angular/router';
import { JhiResolvePagingParams } from 'ng-jhipster';
import { SystemNotificationManagementUpdateComponent } from 'app/admin/system-notification-management/system-notification-management-update.component';
import { SystemNotification } from 'app/entities/system-notification.model';
import { SystemNotificationService } from 'app/core/system-notification/system-notification.service';
import { SystemNotificationManagementComponent } from 'app/admin/system-notification-management/system-notification-management.component';
import { SystemNotificationManagementDetailComponent } from 'app/admin/system-notification-management/system-notification-management-detail.component';

@Injectable({ providedIn: 'root' })
export class SystemNotificationManagementResolve implements Resolve<any> {
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

export const systemNotificationManagementRoutes1: Route = {
    path: 'system-notification-management',
    component: SystemNotificationManagementComponent,
    resolve: {
        pagingParams: JhiResolvePagingParams,
    },
    data: {
        pageTitle: 'artemisApp.systemNotification.systemNotifications',
        defaultSort: 'id,asc',
    },
};

export const systemNotificationManagementRoutes2: Route = {
    path: 'system-notification-management/:id/view',
    component: SystemNotificationManagementDetailComponent,
    resolve: {
        notification: SystemNotificationManagementResolve,
    },
    data: {
        pageTitle: 'artemisApp.systemNotification.systemNotifications',
    },
};

export const systemNotificationManagementRoutes3: Route = {
    path: 'system-notification-management/new',
    component: SystemNotificationManagementUpdateComponent,
    resolve: {
        notification: SystemNotificationManagementResolve,
    },
    data: {
        pageTitle: 'artemisApp.systemNotification.systemNotifications',
    },
};

export const systemNotificationManagementRoutes4: Route = {
    path: 'system-notification-management/:id/edit',
    component: SystemNotificationManagementUpdateComponent,
    resolve: {
        notification: SystemNotificationManagementResolve,
    },
    data: {
        pageTitle: 'artemisApp.systemNotification.systemNotifications',
    },
};
