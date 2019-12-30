import { Routes } from '@angular/router';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { FileUploadAssessmentComponent } from 'app/file-upload-assessment/file-upload-assessment.component';
import { FileUploadAssessmentDashboardComponent } from 'app/file-upload-assessment/file-upload-assessment-dashboard/file-upload-assessment-dashboard.component';

export const fileUploadAssessmentRoutes: Routes = [
    {
        path: 'file-upload-exercise/:exerciseId/submission/:submissionId/assessment',
        component: FileUploadAssessmentComponent,
        data: {
            authorities: ['ROLE_ADMIN', 'ROLE_INSTRUCTOR', 'ROLE_TA'],
            pageTitle: 'artemisApp.fileUploadExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: 'file-upload-exercise/:exerciseId/assessment',
        component: FileUploadAssessmentDashboardComponent,
        data: {
            authorities: ['ROLE_ADMIN', 'ROLE_INSTRUCTOR', 'ROLE_TA'],
            pageTitle: 'assessmentDashboard.title',
        },
        canActivate: [UserRouteAccessService],
    },
];
