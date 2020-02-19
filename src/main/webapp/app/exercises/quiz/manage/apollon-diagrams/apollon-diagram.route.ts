import { Routes } from '@angular/router';
import { ApollonDiagramDetailComponent } from './apollon-diagram-detail.component';
import { ApollonDiagramListComponent } from './apollon-diagram-list.component';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';

export const apollonDiagramsRoutes: Routes = [
    {
        path: 'apollon-diagrams',
        component: ApollonDiagramListComponent,
        data: {
            authorities: ['ROLE_ADMIN', 'ROLE_INSTRUCTOR', 'ROLE_TA'],
            pageTitle: 'artemisApp.apollonDiagram.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: 'apollon-diagrams/:id',
        component: ApollonDiagramDetailComponent,
        data: {
            authorities: ['ROLE_ADMIN', 'ROLE_INSTRUCTOR', 'ROLE_TA'],
            pageTitle: 'artemisApp.apollonDiagram.detail.title',
        },
        canActivate: [UserRouteAccessService],
    },
];
