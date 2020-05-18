import { Routes } from '@angular/router';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { JhiResolvePagingParams } from 'ng-jhipster';
import { NotificationComponent } from './notification.component';

export const notificationRoute: Routes = [
    {
        path: 'notifications',
        component: NotificationComponent,
        resolve: {
            pagingParams: JhiResolvePagingParams,
        },
        data: {
            pageTitle: 'artemisApp.notification.notifications',
            defaultSort: 'notificationDate,desc',
        },
        canActivate: [UserRouteAccessService],
    },
];
