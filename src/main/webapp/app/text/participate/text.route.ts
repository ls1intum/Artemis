import { Routes } from '@angular/router';

import { UserRouteAccessService } from 'app/core';
import { TextComponent } from './text.component';

export const textRoute: Routes = [
    {
        path: 'text/:id',
        component: TextComponent,
        data: {
            authorities: [],
            pageTitle: 'arTeMiSApp.textExercise.home.title',
            mode: 'default'
        },
        canActivate: [UserRouteAccessService]
    }
];
