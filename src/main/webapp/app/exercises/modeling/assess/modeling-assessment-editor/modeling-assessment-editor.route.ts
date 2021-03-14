import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { ModelingAssessmentEditorComponent } from 'app/exercises/modeling/assess/modeling-assessment-editor/modeling-assessment-editor.component';
import { ModelingAssessmentDashboardComponent } from 'app/exercises/modeling/assess/modeling-assessment-editor/modeling-assessment-dashboard.component';
import { Authority } from 'app/shared/constants/authority.constants';

const routes: Routes = [
    {
        path: ':courseId/modeling-exercises/:exerciseId/submissions/:submissionId/assessment',
        component: ModelingAssessmentEditorComponent,
        data: {
            authorities: [Authority.ADMIN, Authority.INSTRUCTOR, Authority.TA],
            usePathForBreadcrumbs: true,
            pageTitle: 'artemisApp.apollonDiagram.detail.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':courseId/modeling-exercises/:exerciseId/submissions/:submissionId/assessments/:resultId',
        component: ModelingAssessmentEditorComponent,
        data: {
            authorities: [Authority.ADMIN, Authority.INSTRUCTOR],
            usePathForBreadcrumbs: true,
            pageTitle: 'artemisApp.apollonDiagram.detail.title',
        },
        canActivate: [UserRouteAccessService],
    },

    {
        path: ':courseId/modeling-exercises/:exerciseId/assessment',
        component: ModelingAssessmentDashboardComponent,
        data: {
            authorities: [Authority.ADMIN, Authority.INSTRUCTOR, Authority.TA],
            usePathForBreadcrumbs: true,
            pageTitle: 'assessmentDashboard.title',
        },
        canActivate: [UserRouteAccessService],
    },
];

@NgModule({
    imports: [RouterModule.forChild(routes)],
    exports: [RouterModule],
})
export class ArtemisModelingAssessmentEditorRoutingModule {}
