import { Routes } from '@angular/router';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { TutorCourseDashboardComponent } from './tutor-course-dashboard.component';
import { Authority } from 'app/shared/constants/authority.constants';

export const tutorCourseDashboardRoute: Routes = [
    {
        path: ':courseId/tutor-dashboard',
        component: TutorCourseDashboardComponent,
        data: {
            authorities: [Authority.ADMIN, Authority.INSTRUCTOR, Authority.TA],
            pageTitle: 'artemisApp.tutorCourseDashboard.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
];
