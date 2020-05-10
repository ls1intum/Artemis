import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { FileUploadAssessmentComponent } from 'app/exercises/file-upload/assess/file-upload-assessment.component';
import { FileUploadAssessmentDashboardComponent } from 'app/exercises/file-upload/assess/file-upload-assessment-dashboard.component';

export const routes: Routes = [
    {
        path: ':courseId/file-upload-exercises/:exerciseId/submissions/:submissionId/assessment',
        component: FileUploadAssessmentComponent,
        data: {
            authorities: ['ROLE_ADMIN', 'ROLE_INSTRUCTOR', 'ROLE_TA'],
            pageTitle: 'artemisApp.fileUploadExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':courseId/file-upload-exercises/:exerciseId/assessment',
        component: FileUploadAssessmentDashboardComponent,
        data: {
            authorities: ['ROLE_ADMIN', 'ROLE_INSTRUCTOR', 'ROLE_TA'],
            pageTitle: 'assessmentDashboard.title',
        },
        canActivate: [UserRouteAccessService],
    },
];

@NgModule({
    imports: [RouterModule.forChild(routes)],
    exports: [RouterModule],
})
export class ArtemisFileUploadAssessmentRoutingModule {}
