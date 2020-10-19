import { Routes } from '@angular/router';
import { Authority } from 'app/shared/constants/authority.constants';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { LectureUnitManagementComponent } from 'app/lecture/lecture-unit/lecture-module-management/lecture-unit-management.component';

export const lectureUnitRoute: Routes = [
    {
        path: ':courseId/lectures/:lectureId/unit-management',
        component: LectureUnitManagementComponent,
        data: {
            authorities: [Authority.INSTRUCTOR, Authority.ADMIN],
            pageTitle: 'artemisApp.lectureUnit.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
];
