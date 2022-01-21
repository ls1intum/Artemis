import { Route } from '@angular/router';
import { SystemNotificationManagementUpdateComponent } from 'app/admin/system-notification-management/system-notification-management-update.component';
import { SystemNotificationManagementComponent } from 'app/admin/system-notification-management/system-notification-management.component';
import { SystemNotificationManagementDetailComponent } from 'app/admin/system-notification-management/system-notification-management-detail.component';
import { SystemNotificationManagementResolve } from 'app/admin/system-notification-management/system-notification-management-resolve.service';

export const systemNotificationManagementRoute: Route[] = [
    {
        path: 'system-notification-management',
        component: SystemNotificationManagementComponent,
        data: {
            pageTitle: 'artemisApp.systemNotification.systemNotifications',
            defaultSort: 'id,asc',
        },
    },
    {
        // Create a new path without a component defined to prevent resolver caching and the SystemNotificationManagementComponent from being always rendered
        path: 'system-notification-management',
        data: {
            pageTitle: 'artemisApp.systemNotification.systemNotifications',
        },
        children: [
            {
                path: 'new',
                component: SystemNotificationManagementUpdateComponent,
                data: {
                    pageTitle: 'global.generic.create',
                },
            },
            {
                path: ':id',
                component: SystemNotificationManagementDetailComponent,
                resolve: {
                    notification: SystemNotificationManagementResolve,
                },
                data: {
                    pageTitle: 'artemisApp.systemNotification.systemNotifications',
                    breadcrumbLabelVariable: 'notification.body.id',
                },
            },
            {
                // Create a new path without a component defined to prevent resolver caching and the SystemNotificationManagementDetailComponent from being always rendered
                path: ':id',
                resolve: {
                    notification: SystemNotificationManagementResolve,
                },
                data: {
                    breadcrumbLabelVariable: 'notification.body.id',
                },
                children: [
                    {
                        path: 'edit',
                        component: SystemNotificationManagementUpdateComponent,
                        data: {
                            pageTitle: 'global.generic.edit',
                            breadcrumbLabelVariable: '',
                        },
                    },
                ],
            },
        ],
    },
];
