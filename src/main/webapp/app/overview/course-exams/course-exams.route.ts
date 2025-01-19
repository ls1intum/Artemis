import { Routes } from '@angular/router';
import { Authority } from 'app/shared/constants/authority.constants';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';

export const routes: Routes = [
    {
        path: '',
        loadComponent: () => import('app/overview/course-exams/course-exams.component').then((m) => m.CourseExamsComponent),
        data: {
            authorities: [Authority.USER],
            pageTitle: 'overview.exams',
        },
        canActivate: [UserRouteAccessService],
    },
];
