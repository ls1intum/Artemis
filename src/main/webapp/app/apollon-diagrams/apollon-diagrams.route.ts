import { Routes } from '@angular/router';
import { ApollonDiagramDetailComponent } from './apollon-diagram-detail.component';
import { ApollonDiagramListComponent } from './apollon-diagram-list.component';
import { UserRouteAccessService } from '../shared';
import { ApollonDiagramStudentComponent } from './apollon-diagram-student.component';
import { ApollonDiagramTutorComponent } from './apollon-diagram-tutor.component';

export const apollonDiagramsRoutes: Routes = [
    {
        path: 'apollon-diagrams',
        component: ApollonDiagramListComponent,
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.apollonDiagram.home.title'
        },
        canActivate: [UserRouteAccessService]
    },
    {
        path: 'apollon-diagrams/:id',
        component: ApollonDiagramDetailComponent,
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.apollonDiagram.detail.title'
        },
        canActivate: [UserRouteAccessService]
    },
    {
        path: 'apollon-diagrams/:id/student',
        component: ApollonDiagramStudentComponent,
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'arTeMiSApp.apollonDiagram.detail.title'
        },
        canActivate: [UserRouteAccessService]
    },
    {
        path: 'apollon-diagrams/exercise/:exerciseId/:submissionId/tutor',
        component: ApollonDiagramTutorComponent,
        data: {
            authorities: ['ROLE_ADMIN', 'ROLE_INSTRUCTOR', 'ROLE_TA'],
            pageTitle: 'arTeMiSApp.apollonDiagram.detail.title'
        },
        canActivate: [UserRouteAccessService]
    }
];
