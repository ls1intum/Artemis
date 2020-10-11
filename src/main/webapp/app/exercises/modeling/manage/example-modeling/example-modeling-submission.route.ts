import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { ExampleModelingSubmissionComponent } from './example-modeling-submission.component';
import { Authority } from 'app/shared/constants/authority.constants';

const routes: Routes = [
    {
        path: ':courseId/modeling-exercises/:exerciseId/example-submissions/:exampleSubmissionId',
        component: ExampleModelingSubmissionComponent,
        data: {
            authorities: [Authority.ADMIN, Authority.INSTRUCTOR, Authority.TA],
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
