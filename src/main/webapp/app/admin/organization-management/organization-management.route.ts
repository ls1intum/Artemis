import { Injectable } from '@angular/core';
import { ActivatedRouteSnapshot, Resolve, Route } from '@angular/router';
import { JhiResolvePagingParams } from 'ng-jhipster';
import { OrganizationManagementComponent } from 'app/admin/organization-management/organization-management.component';
import { OrganizationManagementUpdateComponent } from 'app/admin/organization-management/organization-management-update.component';
import { OrganizationManagementDetailComponent } from 'app/admin/organization-management/organization-management-detail.component';
import { OrganizationManagementService } from 'app/admin/organization-management/organization-management.service';
import { Organization } from 'app/entities/organization.model';
import { UserMgmtResolve } from 'app/admin/user-management/user-management.route';

@Injectable({ providedIn: 'root' })
export class OrganizationMgmtResolve implements Resolve<any> {
    constructor(private organizationManagementService: OrganizationManagementService) {}

    resolve(route: ActivatedRouteSnapshot) {
        if (route.params['id']) {
            return this.organizationManagementService.getOrganizationById(route.params['id']);
        }
        return new Organization();
    }
}

export const organizationMgmtRoute: Route[] = [
    {
        path: 'organization-management',
        component: OrganizationManagementComponent,
        resolve: {
            pagingParams: JhiResolvePagingParams,
        },
        data: {
            pageTitle: 'organizationManagement.title',
        },
    },
    {
        path: 'organization-management',
        data: {
            pageTitle: 'organizationManagement.title',
        },
        children: [
            {
                path: 'new',
                component: OrganizationManagementUpdateComponent,
                resolve: {
                    organization: OrganizationMgmtResolve,
                },
                data: {
                    pageTitle: 'organizationManagement.addLabel',
                },
            },
            {
                path: ':id',
                component: OrganizationManagementDetailComponent,
                resolve: {
                    organization: OrganizationMgmtResolve,
                },
                data: {
                    pageTitle: 'organizationManagement.title',
                    breadcrumbLabelVariable: '',
                },
            },
            {
                path: ':id',
                resolve: {
                    user: UserMgmtResolve,
                },
                data: {
                    breadcrumbLabelVariable: '',
                },
                children: [
                    {
                        path: 'edit',
                        component: OrganizationManagementUpdateComponent,
                        data: {
                            pageTitle: 'organizationManagement.editLabel',
                            breadcrumbLabelVariable: '',
                        },
                    },
                ],
            },
        ],
    },
];
