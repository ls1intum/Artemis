import { PendingChangesGuard, UserRouteAccessService } from '../shared';
import { Routes } from '@angular/router';
import { ModelingEditorComponent } from './modeling-editor.component';

export const modelingEditorRoute: Routes = [
    {
        path: 'modeling-editor/:participationId',
        component: ModelingEditorComponent,
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.modelingExercise.home.title'
        },
        canActivate: [UserRouteAccessService],
        canDeactivate: [PendingChangesGuard]
    }
];
