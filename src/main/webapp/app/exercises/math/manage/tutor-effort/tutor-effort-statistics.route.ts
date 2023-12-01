import { Routes } from '@angular/router';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { Authority } from 'app/shared/constants/authority.constants';
import { TutorEffortStatisticsComponent } from 'app/exercises/text/manage/tutor-effort/tutor-effort-statistics.component';

export const tutorEffortStatisticsRoute: Routes = [
    {
        path: '',
        component: TutorEffortStatisticsComponent,
        data: {
            authorities: [Authority.ADMIN, Authority.INSTRUCTOR],
            pageTitle: 'artemisApp.textExercise.tutorEffortStatistics.title',
        },
        canActivate: [UserRouteAccessService],
    },
];
