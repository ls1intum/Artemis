import { Routes } from '@angular/router';
import { UserRouteAccessService } from 'app/core';
import { JhiResolvePagingParams } from 'ng-jhipster';
import { NotificationComponent } from './notification.component';
import { NotificationMgmtComponent } from 'app/admin';

export const notificationRoute: Routes = [
    {
        path: 'notifications',
        component: NotificationComponent,
        resolve: {
            pagingParams: JhiResolvePagingParams,
        },
        data: {
            pageTitle: 'artemisApp.notificationManagement.home.title',
            defaultSort: 'notificationDate,desc',
        },
        canActivate: [UserRouteAccessService],
    },
];
