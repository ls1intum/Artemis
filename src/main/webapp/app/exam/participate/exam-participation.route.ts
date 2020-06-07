import { Routes } from '@angular/router';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { ExamParticipationComponent } from 'app/exam/participate/exam-participation.component';

export const examParticipationRoute: Routes = [
    {
        path: '',
        component: ExamParticipationComponent,
        data: {
            authorities: ['ROLE_USER'],
            pageTitle: 'Super cool exam mode',
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
