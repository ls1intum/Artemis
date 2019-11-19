import { Routes } from '@angular/router';

import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { ListOfComplaintsComponent } from 'app/list-of-complaints/list-of-complaints.component';
import { ComplaintType } from 'app/entities/complaint';

export const listOfComplaintsRoute: Routes = [
    {
        path: 'complaints',
        component: ListOfComplaintsComponent,
        data: {
            authorities: ['ROLE_ADMIN', 'ROLE_INSTRUCTOR', 'ROLE_TA'],
            pageTitle: 'artemisApp.complaint.listOfComplaints.title',
            complaintType: ComplaintType.COMPLAINT,
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: 'more-feedback-requests',
        component: ListOfComplaintsComponent,
        data: {
            authorities: ['ROLE_ADMIN', 'ROLE_INSTRUCTOR', 'ROLE_TA'],
            pageTitle: 'artemisApp.moreFeedback.list.title',
            complaintType: ComplaintType.MORE_FEEDBACK,
        },
        canActivate: [UserRouteAccessService],
    },
];
