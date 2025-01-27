import { Routes } from '@angular/router';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { Authority } from 'app/shared/constants/authority.constants';

export const tutorEffortStatisticsRoute: Routes = [
    {
        path: '',
        loadComponent: () => import('app/exercises/text/manage/tutor-effort/tutor-effort-statistics.component').then((m) => m.TutorEffortStatisticsComponent),
        data: {
            authorities: [Authority.ADMIN, Authority.INSTRUCTOR],
            pageTitle: 'artemisApp.textExercise.tutorEffortStatistics.title',
        },
        canActivate: [UserRouteAccessService],
    },
];
