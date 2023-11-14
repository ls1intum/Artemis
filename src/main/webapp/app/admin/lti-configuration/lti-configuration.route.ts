import { Route } from '@angular/router';
import { LtiConfigurationComponent } from 'app/admin/lti-configuration/lti-configuration.component';

export const ltiConfigurationRoute: Route = {
    path: 'lti-configuration',
    component: LtiConfigurationComponent,
    data: {
        pageTitle: 'global.menu.admin.lti',
    },
};
