import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { ExampleModelingSubmissionComponent } from './example-modeling-submission.component';

const routes: Routes = [
    {
        path: 'course-management/:courseId/modeling-exercises/:exerciseId/example-submissions/:exampleSubmissionId',
        component: ExampleModelingSubmissionComponent,
        data: {
            authorities: ['ROLE_ADMIN', 'ROLE_INSTRUCTOR', 'ROLE_TA'],
            pageTitle: 'artemisApp.exampleSubmission.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
];

@NgModule({
    imports: [RouterModule.forChild(routes)],
    exports: [RouterModule],
})
export class ArtemisExampleModelingSubmissionRoutingModule {}
