import { Routes } from '@angular/router';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { ExamParticipationComponent } from 'app/exam/participate/exam-participation.component';
import { PendingChangesGuard } from 'app/shared/guard/pending-changes.guard';
import { Authority } from 'app/shared/constants/authority.constants';
import { GradingKeyOverviewComponent } from 'app/grading-system/grading-key-overview/grading-key-overview.component';
import { ExampleSolutionComponent } from 'app/exercises/shared/example-solution/example-solution.component';
import { RepositoryViewComponent } from 'app/localvc/repository-view/repository-view.component';
import { LocalVCGuard } from 'app/localvc/localvc-guard.service';
import { CommitHistoryComponent } from 'app/localvc/commit-history/commit-history.component';
import { CommitDetailsViewComponent } from 'app/localvc/commit-details-view/commit-details-view.component';

export const examParticipationRoute: Routes = [
    {
        path: '',
        component: ExamParticipationComponent,
        data: {
            authorities: [Authority.USER],
            pageTitle: 'artemisApp.exam.title',
        },
        canActivate: [UserRouteAccessService],
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
    {
        path: 'exercises/:exerciseId/example-solution',
        component: ExampleSolutionComponent,
        data: {
            authorities: [Authority.USER],
            pageTitle: 'artemisApp.exam.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: 'exercises/:exerciseId/example-solution',
        component: ExampleSolutionComponent,
        data: {
            authorities: [Authority.USER],
            pageTitle: 'artemisApp.exam.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: 'exercises/:exerciseId/repository/:participationId',
        component: RepositoryViewComponent,
        data: {
            authorities: [Authority.USER],
            pageTitle: 'artemisApp.repository.title',
            flushRepositoryCacheAfter: 900000, // 15 min
            participationCache: {},
            repositoryCache: {},
        },
        canActivate: [UserRouteAccessService, LocalVCGuard],
    },
    {
        path: 'exercises/:exerciseId/repository/:participationId/commit-history',
        component: CommitHistoryComponent,
        data: {
            authorities: [Authority.USER],
            pageTitle: 'artemisApp.repository.commitHistory.title',
            flushRepositoryCacheAfter: 900000, // 15 min
            participationCache: {},
            repositoryCache: {},
        },
        canActivate: [UserRouteAccessService, LocalVCGuard],
    },
    {
        path: 'exercises/:exerciseId/repository/:participationId/commit-history/:commitHash',
        component: CommitDetailsViewComponent,
        data: {
            authorities: [Authority.USER],
            pageTitle: 'artemisApp.repository.commitHistory.commitDetails.title',
            flushRepositoryCacheAfter: 900000, // 15 min
            participationCache: {},
            repositoryCache: {},
        },
        canActivate: [UserRouteAccessService, LocalVCGuard],
    },
];

const EXAM_PARTICIPATION_ROUTES = [...examParticipationRoute];

export const examParticipationState: Routes = [
    {
        path: '',
        children: EXAM_PARTICIPATION_ROUTES,
    },
];
