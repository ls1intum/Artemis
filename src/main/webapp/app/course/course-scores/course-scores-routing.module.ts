import { RouterModule, Routes } from '@angular/router';
import { CourseScoresComponent } from 'app/course/course-scores/course-scores.component';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { NgModule } from '@angular/core';

const routes: Routes = [
    {
        path: 'course-management/:courseId/scores',
        component: CourseScoresComponent,
        data: {
            authorities: ['ROLE_ADMIN', 'ROLE_INSTRUCTOR', 'ROLE_TA'],
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
