import { NgModule } from '@angular/core';
import { Routes, RouterModule } from '@angular/router';
import { UserRouteAccessService } from 'app/core';
import { TextAssessmentComponent } from './text-assessment.component';
import { TextAssessmentDashboardComponent } from './text-assessment-dashboard/text-assessment-dashboard.component';

const routes: Routes = [
    {
        path: 'text/:exerciseId/:submissionId/tutor',
        component: TextAssessmentComponent,
        data: {
            authorities: ['ROLE_ADMIN', 'ROLE_INSTRUCTOR', 'ROLE_TA'],
            pageTitle: 'arTeMiSApp.textExercise.home.title'
        },
        canActivate: [UserRouteAccessService]
    },
    {
        path: 'text/:exerciseId/assessment',
        component: TextAssessmentDashboardComponent,
        data: {
            authorities: ['ROLE_ADMIN', 'ROLE_INSTRUCTOR', 'ROLE_TA'],
            pageTitle: 'assessmentDashboard.title'
        },
        canActivate: [UserRouteAccessService]
    }
];

@NgModule({
    imports: [RouterModule.forChild(routes)],
    exports: [RouterModule]
})
export class ArTEMiSTextAssessmentRoutingModule {}
