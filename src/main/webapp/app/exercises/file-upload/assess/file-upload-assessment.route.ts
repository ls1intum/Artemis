import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { FileUploadAssessmentComponent } from 'app/exercises/file-upload/assess/file-upload-assessment.component';
import { FileUploadAssessmentDashboardComponent } from 'app/exercises/file-upload/assess/file-upload-assessment-dashboard.component';
import { Authority } from 'app/shared/constants/authority.constants';

export const routes: Routes = [
    {
        path: ':courseId/file-upload-exercises/:exerciseId/submissions/:submissionId/assessment',
        component: FileUploadAssessmentComponent,
        data: {
            authorities: [Authority.ADMIN, Authority.INSTRUCTOR, Authority.TA],
            pageTitle: 'artemisApp.fileUploadExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':courseId/file-upload-exercises/:exerciseId/assessment',
        component: FileUploadAssessmentDashboardComponent,
        data: {
            authorities: [Authority.ADMIN, Authority.INSTRUCTOR, Authority.TA],
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
