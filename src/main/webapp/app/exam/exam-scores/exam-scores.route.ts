import { Route, Routes } from '@angular/router';
import { ExamScoresComponent } from 'app/exam/exam-scores/exam-scores.component';
import { Authority } from 'app/shared/constants/authority.constants';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';

export const examScoresRoute: Route[] = [
    {
        path: ':examId/scores',
        component: ExamScoresComponent,
    },
];

const EXAM_SCORES_ROUTES = [...examScoresRoute];

export const examScoresState: Routes = [
    {
        path: '',
        children: EXAM_SCORES_ROUTES,
        data: {
            authorities: [Authority.ADMIN, Authority.INSTRUCTOR],
            pageTitle: 'artemisApp.examScores.title',
        },
        canActivate: [UserRouteAccessService],
    },
];
