import { Routes } from '@angular/router';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { Authority } from 'app/shared/constants/authority.constants';
import { DetailedGradingSystemComponent } from 'app/grading-system/detailed-grading-system/detailed-grading-system.component';
import { IntervalGradingSystemComponent } from 'app/grading-system/interval-grading-system/interval-grading-system.component';

export const gradingSystemState: Routes = [
    {
        path: '',
        redirectTo: 'interval',
        pathMatch: 'full',
    },
    {
        path: 'interval',
        component: IntervalGradingSystemComponent,
        data: {
            authorities: [Authority.INSTRUCTOR, Authority.ADMIN],
            pageTitle: 'artemisApp.gradingSystem.intervalTab.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: 'detailed',
        component: DetailedGradingSystemComponent,
        data: {
            authorities: [Authority.INSTRUCTOR, Authority.ADMIN],
            pageTitle: 'artemisApp.gradingSystem.detailedTab.title',
        },
        canActivate: [UserRouteAccessService],
    },
    // {
    //     path: 'bonus',
    //     component: BonusComponent,
    //     data: {
    //         authorities: [Authority.INSTRUCTOR, Authority.ADMIN],
    //         pageTitle: 'artemisApp.gradingSystem.TODO: Ata',
    //     },
    //     canActivate: [UserRouteAccessService],
    // },
];
