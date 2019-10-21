import { Route } from '@angular/router';

import { JhiTrackerComponent } from 'app/admin';

export const trackerRoute: Route = {
    path: 'jhi-tracker',
    component: JhiTrackerComponent,
    data: {
        pageTitle: 'tracker.title',
    },
};
