import { Routes } from '@angular/router';

import { Authority } from 'app/shared/constants/authority.constants';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';

const routes: Routes = [
    {
        path: '',
        pathMatch: 'full',
        data: {
            authorities: [Authority.USER],
            pageTitle: 'overview.competencies',
        },
        loadComponent: () => import('app/overview/course-competencies/course-competencies.component').then((m) => m.CourseCompetenciesComponent),
        canActivate: [UserRouteAccessService],
    },
];

export { routes };
