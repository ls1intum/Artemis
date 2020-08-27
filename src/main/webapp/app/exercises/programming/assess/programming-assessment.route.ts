import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { CodeEditorTutorAssessmentContainerComponent } from 'app/exercises/programming/assess/code-editor-tutor-assessment-container.component';

export const routes: Routes = [
    {
        path: ':courseId/programming-exercises/:exerciseId/code-editor/:participationId/assessment',
        component: CodeEditorTutorAssessmentContainerComponent,
        data: {
            authorities: ['ROLE_ADMIN', 'ROLE_INSTRUCTOR', 'ROLE_TA'],
            pageTitle: 'artemisApp.programmingExercise.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
];

@NgModule({
    imports: [RouterModule.forChild(routes)],
    exports: [RouterModule],
})
export class ArtemisProgrammingAssessmentRoutingModule {}
