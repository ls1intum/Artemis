import { Routes } from '@angular/router';

import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { TextEditorComponent } from './text-editor.component';

export const textEditorRoute: Routes = [
    {
        path: 'text/:participationId',
        component: TextEditorComponent,
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'artemisApp.textExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
];
