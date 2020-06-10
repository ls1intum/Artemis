import { Routes } from '@angular/router';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { ExamParticipationComponent } from 'app/exam/participate/exam-participation.component';
import { ExamParticipationStartComponent } from 'app/exam/participate/start/exam-participation-start.component';
import { ExamParticipationEndComponent } from 'app/exam/participate/end/exam-participation-end.component';
import { ExamParticipationSummaryComponent } from 'app/exam/participate/summary/exam-participation-summary.component';

export const examParticipationRoute: Routes = [
    {
        path: '',
        component: ExamParticipationComponent,
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'artemisApp.exam.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: 'start',
        component: ExamParticipationStartComponent,
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'artemisApp.exam.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: 'end',
        component: ExamParticipationEndComponent,
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'artemisApp.exam.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: 'summary',
        component: ExamParticipationSummaryComponent,
        data: {
            authorities: ['ROLE_USER'],
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
