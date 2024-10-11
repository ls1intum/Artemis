import { Routes } from '@angular/router';
import { SharingComponent } from 'app/sharing/sharing.component';
import { Authority } from 'app/shared/constants/authority.constants';

export const sharingRoutes: Routes = [
    {
        path: '',
        component: SharingComponent,
        data: {
            pageTitle: 'artemisApp.sharing.title',
            authorities: [Authority.EDITOR, Authority.INSTRUCTOR, Authority.ADMIN],
        },
    },
];

export const featureOverviewState: Routes = [
    {
        path: '',
        children: sharingRoutes,
    },
];
