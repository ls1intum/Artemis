import { Routes } from '@angular/router';
import { Authority } from 'app/shared/constants/authority.constants';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { CourseExamsComponent } from 'app/overview/course-exams/course-exams.component';

export const routes: Routes = [
    {
        path: '',
        component: CourseExamsComponent,
        data: {
            authorities: [Authority.USER],
            pageTitle: 'overview.exams',
        },
        canActivate: [UserRouteAccessService],
    },
];
