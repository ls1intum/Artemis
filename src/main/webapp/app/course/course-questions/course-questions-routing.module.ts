import { RouterModule, Routes } from '@angular/router';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { CourseQuestionsComponent } from 'app/course/course-questions/course-questions.component';
import { NgModule } from '@angular/core';
import { CourseResolve } from 'app/course/manage/course-management.route';

const routes: Routes = [
    {
        path: ':courseId/questions',
        component: CourseQuestionsComponent,
        resolve: {
            course: CourseResolve,
        },
        data: {
            authorities: ['ROLE_ADMIN', 'ROLE_INSTRUCTOR', 'ROLE_TA'],
            // HACK: The path is a composite, so we need to define both parts
            breadcrumbs: [
                { variable: 'course.title', path: 'course.id' },
                { label: 'artemisApp.studentQuestion.overview.title', path: 'questions' },
            ],
            pageTitle: 'artemisApp.studentQuestion.overview.title',
        },
        canActivate: [UserRouteAccessService],
    },
];

@NgModule({
    imports: [RouterModule.forChild(routes)],
    exports: [RouterModule],
})
export class ArtemisCourseQuestionsRoutingModule {}
