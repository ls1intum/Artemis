import { Routes } from '@angular/router';
import { LtiConfigurationComponent } from 'app/admin/lti-configuration/lti-configuration.component';
import { EditLtiConfigurationComponent } from 'app/admin/lti-configuration/edit-lti-configuration.component';
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
        path: 'lti-configuration/:platformId/edit',
        component: EditLtiConfigurationComponent,
        data: {
            authorities: [Authority.ADMIN],
            pageTitle: 'artemisApp.pages.checklist.title',
        },
        canActivate: [UserRouteAccessService],
    },
];
