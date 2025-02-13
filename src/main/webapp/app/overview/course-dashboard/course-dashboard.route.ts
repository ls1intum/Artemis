import { Routes } from '@angular/router';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { Authority } from 'app/shared/constants/authority.constants';

const routes: Routes = [
    {
        path: '',
        pathMatch: 'full',
        loadComponent: () => import('app/overview/course-dashboard/course-dashboard.component').then((m) => m.CourseDashboardComponent),
        data: {
            authorities: [Authority.USER],
            pageTitle: 'overview.dashboard',
        },
        canActivate: [UserRouteAccessService],
    },
];

export { routes };
