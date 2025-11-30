import { Routes } from '@angular/router';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { IS_AT_LEAST_INSTRUCTOR } from 'app/shared/constants/authority.constants';

export const tutorEffortStatisticsRoute: Routes = [
    {
        path: '',
        loadComponent: () => import('app/text/manage/tutor-effort/tutor-effort-statistics.component').then((m) => m.TutorEffortStatisticsComponent),
        data: {
            authorities: IS_AT_LEAST_INSTRUCTOR,
            pageTitle: 'artemisApp.textExercise.tutorEffortStatistics.title',
        },
        canActivate: [UserRouteAccessService],
    },
];
