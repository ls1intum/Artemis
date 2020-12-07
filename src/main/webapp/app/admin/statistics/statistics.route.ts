import { Route } from '@angular/router';
import { StatisticsComponent } from 'app/admin/statistics/statistics.component';

export const statisticsRoute: Route = {
    path: 'user-statistics',
    component: StatisticsComponent,
    data: {
        pageTitle: 'statistics.title',
    },
};
