import { Routes } from '@angular/router';

import { IS_AT_LEAST_ADMIN } from 'app/shared/constants/authority.constants';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';

export const ltiConfigurationRoute: Routes = [
    {
        path: 'lti-configuration',
        loadComponent: () => import('app/core/admin/lti-configuration/lti-configuration.component').then((m) => m.LtiConfigurationComponent),
        data: {
            pageTitle: 'global.menu.admin.lti',
            defaultSort: 'id,desc',
            usesModuleBackground: false,
        },
    },
    {
        path: 'lti-configuration',
        data: {
            pageTitle: 'global.menu.admin.lti',
            usesModuleBackground: false,
        },
        children: [
            {
                path: 'new',
                loadComponent: () => import('app/core/admin/lti-configuration/edit/edit-lti-configuration.component').then((m) => m.EditLtiConfigurationComponent),
                data: {
                    authorities: IS_AT_LEAST_ADMIN,
                    pageTitle: 'artemisApp.lti.addLtiPlatform',
                },
                canActivate: [UserRouteAccessService],
            },
            {
                path: ':platformId',
                data: {
                    breadcrumbLabelVariable: 'platform.id',
                },
                children: [
                    {
                        path: 'edit',
                        loadComponent: () => import('app/core/admin/lti-configuration/edit/edit-lti-configuration.component').then((m) => m.EditLtiConfigurationComponent),
                        data: {
                            authorities: IS_AT_LEAST_ADMIN,
                            pageTitle: 'artemisApp.lti.editLtiPlatform',
                        },
                        canActivate: [UserRouteAccessService],
                    },
                ],
            },
        ],
    },
];
