import { Route } from '@angular/router';

import { LogsComponent } from 'app/admin';

export const logsRoute: Route = {
    path: 'logs',
    component: LogsComponent,
    data: {
        pageTitle: 'logs.title',
    },
};
