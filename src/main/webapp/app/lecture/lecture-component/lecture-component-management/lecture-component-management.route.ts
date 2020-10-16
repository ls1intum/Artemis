import { Routes } from '@angular/router';
import { Authority } from 'app/shared/constants/authority.constants';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { LectureComponentManagementComponent } from 'app/lecture/lecture-component/lecture-component-management/lecture-component-management.component';

export const lectureComponentRoute: Routes = [
    {
        path: ':courseId/lectures/:lectureId/module-management',
        component: LectureComponentManagementComponent,
        data: {
            authorities: [Authority.INSTRUCTOR, Authority.ADMIN],
            pageTitle: 'artemisApp.lectureModule.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
];
