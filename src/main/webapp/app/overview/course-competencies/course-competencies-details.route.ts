import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { Authority } from 'app/shared/constants/authority.constants';
import { Routes } from '@angular/router';

const routes: Routes = [
    {
        path: '',
        loadComponent: () => import('app/overview/course-competencies/course-competencies-details.component').then((m) => m.CourseCompetenciesDetailsComponent),
        data: {
            authorities: [Authority.USER],
            pageTitle: 'overview.competencies',
        },
        canActivate: [UserRouteAccessService],
    },
];
export { routes };
