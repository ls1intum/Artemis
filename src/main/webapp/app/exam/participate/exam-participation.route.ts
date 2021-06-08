import { Routes } from '@angular/router';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { ExamParticipationComponent } from 'app/exam/participate/exam-participation.component';
import { PendingChangesGuard } from 'app/shared/guard/pending-changes.guard';
import { Authority } from 'app/shared/constants/authority.constants';
import { GradeKeyOverviewComponent } from 'app/exam/participate/summary/points-summary/grade-key-overview/grade-key-overview.component';

export const examParticipationRoute: Routes = [
    {
        path: '',
        component: ExamParticipationComponent,
        data: {
            authorities: [Authority.USER],
            pageTitle: 'artemisApp.exam.title',
        },
        canActivate: [UserRouteAccessService],
        canDeactivate: [PendingChangesGuard],
    },
    {
        path: 'grade-key/overview',
        component: GradeKeyOverviewComponent,
        data: {
            authorities: [Authority.USER],
            pageTitle: 'artemisApp.exam.title',
        },
        canActivate: [UserRouteAccessService],
    },
];

const EXAM_PARTICIPATION_ROUTES = [...examParticipationRoute];

export const examParticipationState: Routes = [
    {
        path: '',
        children: EXAM_PARTICIPATION_ROUTES,
    },
];
