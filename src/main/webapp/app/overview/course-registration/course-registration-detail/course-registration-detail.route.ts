import { Routes } from '@angular/router';
import { Authority } from 'app/shared/constants/authority.constants';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';

const routes: Routes = [
    {
        path: '',
        loadComponent: () =>
            import('app/overview/course-registration/course-registration-detail/course-registration-detail.component').then((m) => m.CourseRegistrationDetailComponent),
        data: {
            authorities: [Authority.USER],
            pageTitle: 'artemisApp.studentDashboard.enroll.title',
        },
        canActivate: [UserRouteAccessService],
    },
];

export { routes };
