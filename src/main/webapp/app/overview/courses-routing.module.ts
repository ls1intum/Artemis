import { RouterModule, Routes } from '@angular/router';
import { CoursesComponent } from 'app/overview/courses.component';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { CourseOverviewComponent } from 'app/overview/course-overview.component';
import { CourseLecturesComponent } from 'app/overview/course-lectures/course-lectures.component';
import { CourseExamsComponent } from 'app/overview/course-exams/course-exams.component';
import { NgModule } from '@angular/core';
import { Authority } from 'app/shared/constants/authority.constants';
import { GradingKeyOverviewComponent } from 'app/grading-system/grading-key-overview/grading-key-overview.component';
import { CourseExercisesComponent } from 'app/overview/course-exercises/course-exercises.component';
import { PlagiarismCasesStudentViewComponent } from 'app/course/plagiarism-cases/student-view/plagiarism-cases-student-view.component';
import { PlagiarismCaseStudentDetailViewComponent } from 'app/course/plagiarism-cases/student-view/detail-view/plagiarism-case-student-detail-view.component';

const routes: Routes = [
    {
        path: 'courses',
        component: CoursesComponent,
        data: {
            authorities: [Authority.USER],
            pageTitle: 'overview.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: 'courses/register',
        loadChildren: () => import('./course-registration/course-registration.module').then((m) => m.CourseRegistrationModule),
    },
    {
        path: 'courses/:courseId',
        component: CourseOverviewComponent,
        data: {
            authorities: [Authority.USER],
            pageTitle: 'overview.course',
        },
        canActivate: [UserRouteAccessService],
        children: [
            {
                path: 'exercises',
                component: CourseExercisesComponent,
                data: {
                    authorities: [Authority.USER],
                    pageTitle: 'overview.course',
                },
                canActivate: [UserRouteAccessService],
            },
            {
                path: 'lectures',
                component: CourseLecturesComponent,
                data: {
                    authorities: [Authority.USER],
                    pageTitle: 'overview.lectures',
                },
                canActivate: [UserRouteAccessService],
            },
            {
                path: 'statistics',
                loadChildren: () => import('./course-statistics/course-statistics.module').then((m) => m.CourseStatisticsModule),
            },
            {
                path: 'discussion',
                loadChildren: () => import('../overview/course-discussion/course-discussion.module').then((m) => m.CourseDiscussionModule),
            },
            {
                path: 'exams',
                component: CourseExamsComponent,
                data: {
                    authorities: [Authority.USER],
                    pageTitle: 'overview.exams',
                },
                canActivate: [UserRouteAccessService],
            },
            {
                path: 'plagiarism-cases',
                component: PlagiarismCasesStudentViewComponent,
                data: {
                    authorities: [Authority.USER],
                    pageTitle: 'overview.plagiarism-cases',
                },
                canActivate: [UserRouteAccessService],
            },
            {
                path: 'plagiarism-cases/:plagiarismCaseId',
                component: PlagiarismCaseStudentDetailViewComponent,
                data: {
                    authorities: [Authority.USER],
                    pageTitle: 'overview.plagiarism-cases',
                },
                canActivate: [UserRouteAccessService],
            },
            {
                path: '',
                redirectTo: 'exercises',
                pathMatch: 'full',
            },
        ],
    },
    {
        path: 'courses/:courseId/statistics/grading-key',
        component: GradingKeyOverviewComponent,
        data: {
            authorities: [Authority.USER],
            pageTitle: 'artemisApp.gradingSystem.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: 'courses/:courseId/plagiarism',
        loadChildren: () => import('app/course/plagiarism-cases/plagiarism-cases.module').then((m) => m.ArtemisPlagiarismCasesModule),
    },
];

@NgModule({
    imports: [RouterModule.forChild(routes)],
    exports: [RouterModule],
})
export class ArtemisCoursesRoutingModule {}
