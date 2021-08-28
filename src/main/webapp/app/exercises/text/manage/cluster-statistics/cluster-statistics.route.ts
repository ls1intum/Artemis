import { Routes } from '@angular/router';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { ClusterStatisticsComponent } from './cluster-statistics.component';
import { Authority } from 'app/shared/constants/authority.constants';

export const clusterStatisticsRoute: Routes = [
    {
        path: '',
        component: ClusterStatisticsComponent,
        data: {
            authorities: [Authority.ADMIN, Authority.INSTRUCTOR],
            pageTitle: 'artemisApp.textExercise.clusterStatistics',
        },
        canActivate: [UserRouteAccessService],
    },
];
