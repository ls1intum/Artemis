import { Route } from '@angular/router';
import { JhiConfigurationComponent } from 'app/admin/configuration/configuration.component';

export const configurationRoute: Route = {
    path: 'jhi-configuration',
    component: JhiConfigurationComponent,
    data: {
        pageTitle: 'configuration.title',
    },
};
