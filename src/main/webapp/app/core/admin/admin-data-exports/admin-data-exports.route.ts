import { Route } from '@angular/router';

export const adminDataExportsRoute: Route[] = [
    {
        path: 'data-exports',
        loadComponent: () => import('app/core/admin/admin-data-exports/admin-data-exports.component').then((m) => m.AdminDataExportsComponent),
        data: {
            pageTitle: 'artemisApp.dataExport.admin.title',
        },
    },
];
