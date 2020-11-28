import { Route } from '@angular/router';
import { StatisticsComponent } from 'app/admin/statistics/statistics.component';

export const statisticsRoute: Route = {
    path: 'jhi-statistics',
    component: StatisticsComponent,
    data: {
        pageTitle: 'statistics.title',
    },
};
