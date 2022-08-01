import { Routes } from '@angular/router';
import { Authority } from 'app/shared/constants/authority.constants';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { ExamLiveStatisticsComponent } from 'app/exam/statistics/exam-live-statistics.component';
import { LiveStatisticsOverviewComponent } from 'app/exam/statistics/subpages/overview/live-statistics-overview.component';
import { LiveStatisticsExercisesComponent } from 'app/exam/statistics/subpages/exercise/live-statistics-exercises.component';
import { LiveStatisticsActivityLogComponent } from 'app/exam/statistics/subpages/activity-log/live-statistics-activity-log.component';

export const examLiveStatisticsRoute: Routes = [
    {
        path: '',
        redirectTo: 'overview',
        pathMatch: 'full',
    },
    {
        path: '',
        component: ExamLiveStatisticsComponent,
        data: {
            authorities: [Authority.INSTRUCTOR, Authority.ADMIN],
            pageTitle: 'artemisApp.examLiveStatistics.title',
        },
        canActivate: [UserRouteAccessService],
        children: [
            {
                path: 'overview',
                component: LiveStatisticsOverviewComponent,
                data: {
                    authorities: [Authority.INSTRUCTOR, Authority.ADMIN],
                    pageTitle: 'artemisApp.examLiveStatistics.title',
                },
                canActivate: [UserRouteAccessService],
            },
            {
                path: 'exercises',
                component: LiveStatisticsExercisesComponent,
                data: {
                    authorities: [Authority.INSTRUCTOR, Authority.ADMIN],
                    pageTitle: 'artemisApp.examLiveStatistics.title',
                },
                canActivate: [UserRouteAccessService],
            },
            {
                path: 'activity-log',
                component: LiveStatisticsActivityLogComponent,
                data: {
                    authorities: [Authority.INSTRUCTOR, Authority.ADMIN],
                    pageTitle: 'artemisApp.examLiveStatistics.title',
                },
                canActivate: [UserRouteAccessService],
            },
        ],
    },
];

const EXAM_LIVE_STATISTICS_ROUTES = [...examLiveStatisticsRoute];

export const examLiveStatisticsState: Routes = [
    {
        path: '',
        children: EXAM_LIVE_STATISTICS_ROUTES,
    },
];
