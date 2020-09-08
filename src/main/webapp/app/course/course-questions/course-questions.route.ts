import { Routes } from '@angular/router';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { CourseQuestionsComponent } from 'app/course/course-questions/course-questions.component';

export const courseQuestionsRoute: Routes = [
    {
        path: ':courseId/questions',
        component: CourseQuestionsComponent,
        data: {
            authorities: ['ROLE_ADMIN', 'ROLE_INSTRUCTOR', 'ROLE_TA'],
            pageTitle: 'artemisApp.tutorCourseDashboard.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
];
