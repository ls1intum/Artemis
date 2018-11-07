import { Routes } from '@angular/router';

import { UserRouteAccessService } from 'app/core';
import { TextComponent } from './text.component';
import { PendingChangesGuard } from 'app/shared';

export const textRoute: Routes = [
    {
        path: 'text/:participationId',
        component: TextComponent,
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.textExercise.home.title'
        },
        canActivate: [UserRouteAccessService],
        canDeactivate: [PendingChangesGuard]
    }
];
