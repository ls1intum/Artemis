import { Route } from '@angular/router';

import { campusOnlineOrgUnitsResolve } from 'app/core/admin/campus-online-org-units/campus-online-org-units-resolve.service';

export const campusOnlineOrgUnitsRoute: Route[] = [
    {
        path: 'campus-online-org-units',
        loadComponent: () => import('app/core/admin/campus-online-org-units/campus-online-org-units.component').then((m) => m.CampusOnlineOrgUnitsComponent),
        data: {
            pageTitle: 'artemisApp.campusOnlineOrgUnits.title',
        },
    },
    {
        path: 'campus-online-org-units',
        data: {
            pageTitle: 'artemisApp.campusOnlineOrgUnits.title',
        },
        children: [
            {
                path: 'new',
                loadComponent: () => import('app/core/admin/campus-online-org-units/campus-online-org-units-update.component').then((m) => m.CampusOnlineOrgUnitsUpdateComponent),
                resolve: {
                    orgUnit: campusOnlineOrgUnitsResolve,
                },
                data: {
                    pageTitle: 'artemisApp.campusOnlineOrgUnits.addLabel',
                },
            },
            {
                path: ':id',
                resolve: {
                    orgUnit: campusOnlineOrgUnitsResolve,
                },
                data: {
                    breadcrumbLabelVariable: 'orgUnit.id',
                },
                children: [
                    {
                        path: 'edit',
                        loadComponent: () =>
                            import('app/core/admin/campus-online-org-units/campus-online-org-units-update.component').then((m) => m.CampusOnlineOrgUnitsUpdateComponent),
                        data: {
                            pageTitle: 'artemisApp.campusOnlineOrgUnits.addOrEditLabel',
                            breadcrumbLabelVariable: 'orgUnit.id',
                        },
                    },
                ],
            },
        ],
    },
];
