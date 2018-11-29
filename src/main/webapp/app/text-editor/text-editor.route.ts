import { Routes } from '@angular/router';

import { UserRouteAccessService } from 'app/core';
import { TextEditorComponent } from './text-editor.component';

export const textEditorRoute: Routes = [
    {
        path: 'text/:participationId',
        component: TextEditorComponent,
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.textExercise.home.title'
        },
        canActivate: [UserRouteAccessService]
    }
];
