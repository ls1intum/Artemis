import { Routes } from '@angular/router';

import { UserRouteAccessService } from '../../shared';
import { ResultComponent } from './result.component';
import { ResultDetailComponent } from './result-detail.component';
import { ResultPopupComponent } from './result-dialog.component';
import { ResultDeletePopupComponent } from './result-delete-dialog.component';

export const resultRoute: Routes = [
    {
        path: 'result',
        component: ResultComponent,
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.result.home.title'
        },
        canActivate: [UserRouteAccessService]
    }, {
        path: 'result/:id',
        component: ResultDetailComponent,
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.result.home.title'
        },
        canActivate: [UserRouteAccessService]
    }
];

export const resultPopupRoute: Routes = [
    {
        path: 'result-new',
        component: ResultPopupComponent,
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.result.home.title'
        },
        canActivate: [UserRouteAccessService],
        outlet: 'popup'
    },
    {
        path: 'result/:id/edit',
        component: ResultPopupComponent,
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.result.home.title'
        },
        canActivate: [UserRouteAccessService],
        outlet: 'popup'
    },
    {
        path: 'result/:id/delete',
        component: ResultDeletePopupComponent,
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.result.home.title'
        },
        canActivate: [UserRouteAccessService],
        outlet: 'popup'
    }
];
