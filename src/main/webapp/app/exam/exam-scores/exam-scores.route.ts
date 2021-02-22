import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { ExamScoresComponent } from 'app/exam/exam-scores/exam-scores.component';
import { Authority } from 'app/shared/constants/authority.constants';

const routes: Routes = [
    {
        path: ':examId/scores',
        component: ExamScoresComponent,
        data: {
            authorities: [Authority.ADMIN, Authority.INSTRUCTOR],
            usePathForBreadcrumbs: true,
            pageTitle: 'artemisApp.examScores.title',
        },
        canActivate: [UserRouteAccessService],
    },
];

@NgModule({
    imports: [RouterModule.forChild(routes)],
    exports: [RouterModule],
})
export class ArtemisExamScoresRoutingModule {}
