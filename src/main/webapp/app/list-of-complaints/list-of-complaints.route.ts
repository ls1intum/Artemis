import { Routes } from '@angular/router';

import { UserRouteAccessService } from '../core';
import { ListOfComplaintsComponent } from 'app/list-of-complaints/list-of-complaints.component';

export const listOfComplaintsRoute: Routes = [
    {
        path: 'complaints',
        component: ListOfComplaintsComponent,
        data: {
            authorities: ['ROLE_ADMIN', 'ROLE_INSTRUCTOR', 'ROLE_TA'],
            pageTitle: 'artemisApp.complaint.listOfComplaints.title',
        },
        canActivate: [UserRouteAccessService],
    },
];
