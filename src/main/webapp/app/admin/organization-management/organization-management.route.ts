import { Route } from '@angular/router';
import { OrganizationManagementComponent } from 'app/admin/organization-management/organization-management.component';
import { OrganizationManagementUpdateComponent } from 'app/admin/organization-management/organization-management-update.component';
import { OrganizationManagementDetailComponent } from 'app/admin/organization-management/organization-management-detail.component';
import { OrganizationManagementResolve } from 'app/admin/organization-management/organization-management-resolve.service';

export const organizationMgmtRoute: Route[] = [
    {
        path: 'organization-management',
        component: OrganizationManagementComponent,
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
                component: OrganizationManagementUpdateComponent,
                resolve: {
                    organization: OrganizationManagementResolve,
                },
                data: {
                    pageTitle: 'artemisApp.organizationManagement.addLabel',
                },
            },
            {
                path: ':id',
                component: OrganizationManagementDetailComponent,
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
                        component: OrganizationManagementUpdateComponent,
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
