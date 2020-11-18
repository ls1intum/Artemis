import { Route } from '@angular/router';
import { JhiStatisticsComponent } from 'app/admin/statistics/statistics.component';

export const statisticsRoute: Route = {
    path: 'jhi-statistics',
    component: JhiStatisticsComponent,
    data: {
        pageTitle: 'statistics.title',
    },
};
