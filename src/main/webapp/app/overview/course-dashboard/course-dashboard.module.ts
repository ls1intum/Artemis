import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { CourseDashboardComponent } from 'app/overview/course-dashboard/course-dashboard.component';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { RouterModule, Routes } from '@angular/router';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { Authority } from 'app/shared/constants/authority.constants';
import { IrisModule } from 'app/iris/iris.module';

const routes: Routes = [
    {
        path: '',
        pathMatch: 'full',
        component: CourseDashboardComponent,
        data: {
            authorities: [Authority.USER],
            pageTitle: 'overview.dashboard',
        },
        canActivate: [UserRouteAccessService],
    },
];

@NgModule({
    declarations: [CourseDashboardComponent],
    exports: [CourseDashboardComponent],
    imports: [CommonModule, RouterModule.forChild(routes), ArtemisSharedModule, FontAwesomeModule, IrisModule],
})
export class CourseDashboardModule {}
