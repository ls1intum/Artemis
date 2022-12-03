import { Routes } from '@angular/router';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { ExamParticipationComponent } from 'app/exam/participate/exam-participation.component';
import { PendingChangesGuard } from 'app/shared/guard/pending-changes.guard';
import { Authority } from 'app/shared/constants/authority.constants';
import { GradingKeyOverviewComponent } from 'app/grading-system/grading-key-overview/grading-key-overview.component';

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
        path: 'overview/grading-key',
        component: GradingKeyOverviewComponent,
        data: {
            authorities: [Authority.USER],
            pageTitle: 'artemisApp.exam.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: 'overview/bonus-grading-key',
        component: GradingKeyOverviewComponent,
        data: {
            authorities: [Authority.USER],
            pageTitle: 'artemisApp.exam.title',
            forBonus: true,
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: 'test-exam/:studentExamId',
        component: ExamParticipationComponent,
        data: {
            authorities: [Authority.USER],
            pageTitle: 'artemisApp.exam.title',
        },
        canActivate: [UserRouteAccessService],
        canDeactivate: [PendingChangesGuard],
    },
];

const EXAM_PARTICIPATION_ROUTES = [...examParticipationRoute];

export const examParticipationState: Routes = [
    {
        path: '',
        children: EXAM_PARTICIPATION_ROUTES,
    },
];
