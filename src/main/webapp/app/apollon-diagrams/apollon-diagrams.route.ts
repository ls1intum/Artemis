import { Routes } from '@angular/router';
import { ApollonDiagramDetailComponent } from './apollon-diagram-detail.component';
import { ApollonDiagramListComponent } from './apollon-diagram-list.component';
import { UserRouteAccessService } from '../core';
import { ApollonDiagramStudentComponent } from './apollon-diagram-student.component';
import { ModelingAssessmentComponent } from './modeling-assessment/modeling-assessment.component';
import {ModelingAssessmentConflictComponent} from "app/apollon-diagrams/modeling-assessment/modeling-assessment-conflict/modeling-assessment-conflict.component";

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
        component: ModelingAssessmentComponent,
        data: {
            authorities: ['ROLE_ADMIN', 'ROLE_INSTRUCTOR', 'ROLE_TA'],
            pageTitle: 'arTeMiSApp.apollonDiagram.detail.title'
        },
        canActivate: [UserRouteAccessService]
    },
    {
        path: 'apollon-diagrams/exercise/:exerciseId/:submissionId/conflict',
        component: ModelingAssessmentConflictComponent,
        data: {
            authorities: ['ROLE_ADMIN', 'ROLE_INSTRUCTOR', 'ROLE_TA'],
            pageTitle: 'arTeMiSApp.apollonDiagram.detail.title'
        },
        canActivate: [UserRouteAccessService]
    },
    // We have to fake path change to make angular reload the component
    {
        path: 'apollon-diagrams2/exercise/:exerciseId/:submissionId/tutor',
        component: ModelingAssessmentComponent,
        data: {
            authorities: ['ROLE_ADMIN', 'ROLE_INSTRUCTOR', 'ROLE_TA'],
            pageTitle: 'arTeMiSApp.apollonDiagram.detail.title'
        },
        canActivate: [UserRouteAccessService]
    }
];
