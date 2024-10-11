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

const SHARING_ROUTES = [...sharingRoutes];

export const featureOverviewState: Routes = [
    {
        path: '',
        children: SHARING_ROUTES,
    },
];
