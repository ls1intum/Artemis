import { Routes } from '@angular/router';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';

import { IS_AT_LEAST_INSTRUCTOR, IS_AT_LEAST_TUTOR } from 'app/shared/constants/authority.constants';

export const routes: Routes = [
    {
        path: 'assessment',
        loadComponent: () => import('app/modeling/manage/assess/modeling-assessment-editor/modeling-assessment-editor.component').then((m) => m.ModelingAssessmentEditorComponent),
        data: {
            authorities: IS_AT_LEAST_TUTOR,
            pageTitle: 'artemisApp.modelingExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: 'assessments/:resultId',
        loadComponent: () => import('app/modeling/manage/assess/modeling-assessment-editor/modeling-assessment-editor.component').then((m) => m.ModelingAssessmentEditorComponent),
        data: {
            authorities: IS_AT_LEAST_INSTRUCTOR,
            usePathForBreadcrumbs: true,
            pageTitle: 'artemisApp.modelingExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
];
