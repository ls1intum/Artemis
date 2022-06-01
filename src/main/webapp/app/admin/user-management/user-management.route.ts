import { Route } from '@angular/router';
import { UserManagementDetailComponent } from 'app/admin/user-management/user-management-detail.component';
import { UserManagementComponent } from 'app/admin/user-management/user-management.component';
import { UserManagementUpdateComponent } from 'app/admin/user-management/user-management-update.component';
import { UserManagementResolve } from 'app/admin/user-management/user-management-resolve.service';

export const userManagementRoute: Route[] = [
    {
        path: 'user-management',
        component: UserManagementComponent,
        data: {
            pageTitle: 'artemisApp.userManagement.home.title',
            defaultSort: 'id,asc',
        },
    },
    {
        // Create a new path without a component defined to prevent resolver caching and the UserManagementComponent from being always rendered
        path: 'user-management',
        data: {
            pageTitle: 'artemisApp.userManagement.home.title',
        },
        children: [
            {
                path: 'new',
                component: UserManagementUpdateComponent,
                resolve: {
                    user: UserManagementResolve,
                },
                data: {
                    pageTitle: 'artemisApp.userManagement.home.createLabel',
                },
            },
            {
                path: ':login',
                component: UserManagementDetailComponent,
                resolve: {
                    user: UserManagementResolve,
                },
                data: {
                    pageTitle: 'artemisApp.userManagement.home.title',
                },
            },
            {
                // Create a new path without a component defined to prevent resolver caching and the UserManagementDetailComponent from being always rendered
                path: ':login',
                resolve: {
                    user: UserManagementResolve,
                },
                children: [
                    {
                        path: 'edit',
                        component: UserManagementUpdateComponent,
                        data: {
                            pageTitle: 'artemisApp.userManagement.home.createOrEditLabel',
                        },
                    },
                ],
            },
        ],
    },
];
