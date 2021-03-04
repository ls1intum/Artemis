import { RouterModule, Routes } from '@angular/router';
import { CourseScoresComponent } from 'app/course/course-scores/course-scores.component';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { NgModule } from '@angular/core';
import { Authority } from 'app/shared/constants/authority.constants';
import { CourseResolve } from 'app/course/manage/course-management.route';

const routes: Routes = [
    {
        path: ':courseId/scores',
        component: CourseScoresComponent,
        resolve: {
            course: CourseResolve,
        },
        data: {
            authorities: [Authority.ADMIN, Authority.INSTRUCTOR, Authority.TA],
            pageTitle: 'instructorDashboard.title',
        },
        canActivate: [UserRouteAccessService],
    },
];

@NgModule({
    imports: [RouterModule.forChild(routes)],
    exports: [RouterModule],
})
export class ArtemisCourseScoresRoutingModule {}
