import { Routes } from '@angular/router';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';

import { IS_AT_LEAST_TUTOR } from 'app/shared/constants/authority.constants';

export const routes: Routes = [
    {
        path: 'assessment',
        loadComponent: () => import('app/fileupload/manage/assess/file-upload-assessment.component').then((m) => m.FileUploadAssessmentComponent),
        data: {
            authorities: IS_AT_LEAST_TUTOR,
            pageTitle: 'artemisApp.fileUploadExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: 'assessments/:resultId',
        loadComponent: () => import('app/fileupload/manage/assess/file-upload-assessment.component').then((m) => m.FileUploadAssessmentComponent),
        data: {
            authorities: IS_AT_LEAST_TUTOR,
            pageTitle: 'artemisApp.fileUploadExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
];
