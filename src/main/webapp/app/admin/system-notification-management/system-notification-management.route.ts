import { Injectable } from '@angular/core';
import { ActivatedRouteSnapshot, Resolve, Route } from '@angular/router';
import { SystemNotificationManagementUpdateComponent } from 'app/admin/system-notification-management/system-notification-management-update.component';
import { SystemNotification } from 'app/entities/system-notification.model';
import { SystemNotificationService } from 'app/shared/notification/system-notification/system-notification.service';
import { SystemNotificationManagementComponent } from 'app/admin/system-notification-management/system-notification-management.component';
import { SystemNotificationManagementDetailComponent } from 'app/admin/system-notification-management/system-notification-management-detail.component';
import { HttpResponse } from '@angular/common/http';
import { filter, map } from 'rxjs/operators';

@Injectable({ providedIn: 'root' })
export class SystemNotificationManagementResolve implements Resolve<SystemNotification> {
    constructor(private service: SystemNotificationService) {}

    /**
     * Resolves the route and initializes system notification from id route param
     * @param route
     */
    resolve(route: ActivatedRouteSnapshot) {
        if (route.params['id']) {
            return this.service.find(parseInt(route.params['id'], 10)).pipe(
                filter((response: HttpResponse<SystemNotification>) => response.ok),
                map((exam: HttpResponse<SystemNotification>) => exam.body!),
            );
        }
        return new SystemNotification();
    }
}

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
