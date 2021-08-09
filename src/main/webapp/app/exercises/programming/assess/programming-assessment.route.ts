import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { CodeEditorTutorAssessmentContainerComponent } from 'app/exercises/programming/assess/code-editor-tutor-assessment-container.component';
import { Authority } from 'app/shared/constants/authority.constants';
import { ProgrammingAssessmentDashboardComponent } from 'app/exercises/programming/assess/programming-assessment-dashboard/programming-assessment-dashboard.component';
import { isOrion } from 'app/shared/orion/orion';
import { OrionTutorAssessmentComponent } from 'app/orion/assessment/orion-tutor-assessment.component';

export const routes: Routes = [
    {
        path: ':courseId/programming-exercises/:exerciseId/submissions',
        component: ProgrammingAssessmentDashboardComponent,
        data: {
            authorities: [Authority.ADMIN, Authority.INSTRUCTOR, Authority.EDITOR, Authority.TA],
            pageTitle: 'artemisApp.assessmentDashboard.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':courseId/programming-exercises/:exerciseId/submissions/:submissionId/assessment',
        component: !isOrion ? CodeEditorTutorAssessmentContainerComponent : OrionTutorAssessmentComponent,
        data: {
            authorities: [Authority.ADMIN, Authority.INSTRUCTOR, Authority.EDITOR, Authority.TA],
            pageTitle: 'artemisApp.programmingExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: ':courseId/programming-exercises/:exerciseId/submissions/:submissionId/assessment/correction-round/:correctionRound',
        redirectTo: ':courseId/programming-exercises/:exerciseId/submissions/:submissionId/assessment?correction-round=:correctionRound',
    },
];

@NgModule({
    imports: [RouterModule.forChild(routes)],
    exports: [RouterModule],
})
export class ArtemisProgrammingAssessmentRoutingModule {}
