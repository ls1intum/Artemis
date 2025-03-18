import { Route } from '@angular/router';

import { UserManagementResolve } from 'app/core/admin/user-management/user-management-resolve.service';

export const userManagementRoute: Route[] = [
    {
        path: 'user-management',
        loadComponent: () => import('app/core/admin/user-management/user-management.component').then((m) => m.UserManagementComponent),
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
                loadComponent: () => import('app/core/admin/user-management/user-management-update.component').then((m) => m.UserManagementUpdateComponent),
                resolve: {
                    user: UserManagementResolve,
                },
                data: {
                    pageTitle: 'artemisApp.userManagement.home.createLabel',
                },
            },
            {
                path: ':login',
                loadComponent: () => import('app/core/admin/user-management/user-management-detail.component').then((m) => m.UserManagementDetailComponent),
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
                        loadComponent: () => import('app/core/admin/user-management/user-management-update.component').then((m) => m.UserManagementUpdateComponent),
                        data: {
                            pageTitle: 'artemisApp.userManagement.home.editLabel',
                        },
                    },
                ],
            },
        ],
    },
];
