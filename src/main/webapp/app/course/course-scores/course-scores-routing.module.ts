import { RouterModule, Routes } from '@angular/router';

import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { NgModule } from '@angular/core';
import { Authority } from 'app/shared/constants/authority.constants';
import { CourseManagementResolve } from 'app/course/manage/course-management-resolve.service';

const routes: Routes = [
    {
        path: ':courseId/scores',
        loadComponent: () => import('app/course/manage/course-management-tab-bar/course-management-tab-bar.component').then((m) => m.CourseManagementTabBarComponent),
        children: [
            {
                path: '',
                loadComponent: () => import('app/course/course-scores/course-scores.component').then((m) => m.CourseScoresComponent),
                resolve: {
                    course: CourseManagementResolve,
                },
                data: {
                    authorities: [Authority.ADMIN, Authority.INSTRUCTOR, Authority.EDITOR, Authority.TA],
                    pageTitle: 'artemisApp.instructorDashboard.title',
                },
                canActivate: [UserRouteAccessService],
            },
        ],
    },
];

@NgModule({
    imports: [RouterModule.forChild(routes)],
    exports: [RouterModule],
})
export class ArtemisCourseScoresRoutingModule {}
