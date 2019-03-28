import { Injectable } from '@angular/core';
import { ActivatedRouteSnapshot, Resolve, RouterStateSnapshot, Routes } from '@angular/router';
import { JhiResolvePagingParams } from 'ng-jhipster';

import { NotificationMgmtComponent } from './notification-management.component';
import { NotificationMgmtDetailComponent } from './notification-management-detail.component';
import { NotificationMgmtUpdateComponent } from './notification-management-update.component';
import { SystemNotification, SystemNotificationService } from 'app/entities/system-notification';

@Injectable({ providedIn: 'root' })
export class NotificationMgmtResolve implements Resolve<any> {
    constructor(private service: SystemNotificationService) {}

    resolve(route: ActivatedRouteSnapshot, state: RouterStateSnapshot) {
        const id = route.params['id'] ? route.params['id'] : null;
        if (id) {
            return this.service.find(parseInt(id));
        }
        return new SystemNotification();
    }
}

export const notificationMgmtRoute: Routes = [
    {
        path: 'notification-management',
        component: NotificationMgmtComponent,
        resolve: {
            pagingParams: JhiResolvePagingParams
        },
        data: {
            pageTitle: 'notificationManagement.home.title',
            defaultSort: 'id,asc'
        }
    },
    {
        path: 'notification-management/:id/view',
        component: NotificationMgmtDetailComponent,
        resolve: {
            notification: NotificationMgmtResolve
        },
        data: {
            pageTitle: 'notificationManagement.home.title'
        }
    },
    {
        path: 'notification-management/new',
        component: NotificationMgmtUpdateComponent,
        resolve: {
            notification: NotificationMgmtResolve
        }
    },
    {
        path: 'notification-management/:id/edit',
        component: NotificationMgmtUpdateComponent,
        resolve: {
            notification: NotificationMgmtResolve
        }
    }
];
