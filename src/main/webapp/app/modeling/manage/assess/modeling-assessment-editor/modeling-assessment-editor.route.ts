import { Routes } from '@angular/router';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';

import { Authority } from 'app/shared/constants/authority.constants';

export const routes: Routes = [
    {
        path: 'assessment',
        loadComponent: () => import('app/modeling/manage/assess/modeling-assessment-editor/modeling-assessment-editor.component').then((m) => m.ModelingAssessmentEditorComponent),
        data: {
            authorities: [Authority.ADMIN, Authority.INSTRUCTOR, Authority.EDITOR, Authority.TA],
            pageTitle: 'artemisApp.modelingExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: 'assessments/:resultId',
        loadComponent: () => import('app/modeling/manage/assess/modeling-assessment-editor/modeling-assessment-editor.component').then((m) => m.ModelingAssessmentEditorComponent),
        data: {
            authorities: [Authority.ADMIN, Authority.INSTRUCTOR],
            usePathForBreadcrumbs: true,
            pageTitle: 'artemisApp.modelingExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
];
