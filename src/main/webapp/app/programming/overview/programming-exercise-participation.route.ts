import { Routes } from '@angular/router';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { IS_AT_LEAST_STUDENT } from 'app/shared/constants/authority.constants';

export const programmingExerciseParticipationRoute: Routes = [
    {
        path: 'code-editor/:participationId',
        loadComponent: () => import('./code-editor-student-container/code-editor-student-container.component').then((m) => m.CodeEditorStudentContainerComponent),
        data: {
            authorities: IS_AT_LEAST_STUDENT,
            pageTitle: 'artemisApp.programmingExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
];
