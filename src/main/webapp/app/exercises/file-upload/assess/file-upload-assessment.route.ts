import { Routes } from '@angular/router';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';

import { Authority } from 'app/shared/constants/authority.constants';

export const routes: Routes = [
    {
        path: 'assessment',
        loadComponent: () => import('app/exercises/file-upload/assess/file-upload-assessment.component').then((m) => m.FileUploadAssessmentComponent),
        data: {
            authorities: [Authority.ADMIN, Authority.INSTRUCTOR, Authority.EDITOR, Authority.TA],
            pageTitle: 'artemisApp.fileUploadExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: 'assessments/:resultId',
        loadComponent: () => import('app/exercises/file-upload/assess/file-upload-assessment.component').then((m) => m.FileUploadAssessmentComponent),
        data: {
            authorities: [Authority.ADMIN, Authority.INSTRUCTOR, Authority.EDITOR, Authority.TA],
            pageTitle: 'artemisApp.fileUploadExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
];
