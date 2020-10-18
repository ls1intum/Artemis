import { Routes } from '@angular/router';
import { Authority } from 'app/shared/constants/authority.constants';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { LectureModuleManagementComponent } from 'app/lecture/lecture-module/lecture-module-management/lecture-module-management.component';

export const lectureModuleRoute: Routes = [
    {
        path: ':courseId/lectures/:lectureId/module-management',
        component: LectureModuleManagementComponent,
        data: {
            authorities: [Authority.INSTRUCTOR, Authority.ADMIN],
            pageTitle: 'artemisApp.lectureModule.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
];
