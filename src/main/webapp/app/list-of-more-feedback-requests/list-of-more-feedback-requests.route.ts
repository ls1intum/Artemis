import { Routes } from '@angular/router';

import { UserRouteAccessService } from '../core';
import { ListOfMoreFeedbackRequestsComponent } from 'app/list-of-more-feedback-requests/list-of-more-feedback-requests.component';

export const listOfMoreFeedbackRequestsRoute: Routes = [
    {
        path: 'moreFeedbackRequests',
        component: ListOfMoreFeedbackRequestsComponent,
        data: {
            authorities: ['ROLE_ADMIN', 'ROLE_INSTRUCTOR', 'ROLE_TA'],
            pageTitle: 'artemisApp.moreFeedback.list.title',
        },
        canActivate: [UserRouteAccessService],
    },
];
