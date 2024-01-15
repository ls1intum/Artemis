import { Routes } from '@angular/router';
import { LtiConfigurationComponent } from 'app/admin/lti-configuration/lti-configuration.component';
import { LtiConfigurationUpdateComponent } from 'app/admin/lti-configuration/lti-configuration-update.component';
import { Authority } from 'app/shared/constants/authority.constants';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';

export const ltiConfigurationRoute: Routes = [
    {
        path: 'lti-configuration',
        component: LtiConfigurationComponent,
        data: {
            pageTitle: 'global.menu.admin.lti',
        },
    },
    {
        path: 'lti-configuration',
        data: {
            pageTitle: 'global.menu.admin.lti',
        },
        children: [
            {
                path: 'new',
                component: LtiConfigurationUpdateComponent,
                data: {
                    authorities: [Authority.ADMIN],
                    pageTitle: 'artemisApp.lti.addOrEditLtiPlatform',
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
                        component: LtiConfigurationUpdateComponent,
                        data: {
                            authorities: [Authority.ADMIN],
                            pageTitle: 'artemisApp.lti.addOrEditLtiPlatform',
                        },
                        canActivate: [UserRouteAccessService],
                    },
                ],
            },
        ],
    },
];
