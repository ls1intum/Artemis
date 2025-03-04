import { Route } from '@angular/router';

import { OrganizationManagementResolve } from 'app/admin/organization-management/organization-management-resolve.service';

export const organizationMgmtRoute: Route[] = [
    {
        path: 'organization-management',
        loadComponent: () => import('app/admin/organization-management/organization-management.component').then((m) => m.OrganizationManagementComponent),
        data: {
            pageTitle: 'artemisApp.organizationManagement.title',
        },
    },
    {
        path: 'organization-management',
        data: {
            pageTitle: 'artemisApp.organizationManagement.title',
        },
        children: [
            {
                path: 'new',
                loadComponent: () => import('app/admin/organization-management/organization-management-update.component').then((m) => m.OrganizationManagementUpdateComponent),
                resolve: {
                    organization: OrganizationManagementResolve,
                },
                data: {
                    pageTitle: 'artemisApp.organizationManagement.addLabel',
                },
            },
            {
                path: ':id',
                loadComponent: () => import('app/admin/organization-management/organization-management-detail.component').then((m) => m.OrganizationManagementDetailComponent),
                resolve: {
                    organization: OrganizationManagementResolve,
                },
                data: {
                    pageTitle: 'artemisApp.organizationManagement.title',
                    breadcrumbLabelVariable: 'organization.id',
                },
            },
            {
                path: ':id',
                resolve: {
                    organization: OrganizationManagementResolve,
                },
                data: {
                    breadcrumbLabelVariable: 'organization.id',
                },
                children: [
                    {
                        path: 'edit',
                        loadComponent: () =>
                            import('app/admin/organization-management/organization-management-update.component').then((m) => m.OrganizationManagementUpdateComponent),
                        data: {
                            pageTitle: 'artemisApp.organizationManagement.addOrEditLabel',
                            breadcrumbLabelVariable: 'organization.id',
                        },
                    },
                ],
            },
        ],
    },
];
