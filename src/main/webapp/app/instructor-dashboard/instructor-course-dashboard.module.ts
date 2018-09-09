import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';

import { ArTEMiSSharedModule } from '../shared';
import { UserRouteAccessService } from '../core';
import { HomeComponent } from '../home';
import { MomentModule } from 'angular2-moment';
import { JhiMainComponent } from '../layouts';
import { InstructorCourseDashboardComponent } from './instructor-course-dashboard.component';
import { SortByModule } from '../components/pipes/sort-by.module';

const ENTITY_STATES = [
    {
        path: 'course/:courseId/dashboard',
        component: InstructorCourseDashboardComponent,
        data: {
            authorities: ['ROLE_ADMIN', 'ROLE_INSTRUCTOR', 'ROLE_TA'],
            pageTitle: 'instructorDashboard.title'
        },
        canActivate: [UserRouteAccessService]
    }
];

@NgModule({
    imports: [ArTEMiSSharedModule, MomentModule, RouterModule.forChild(ENTITY_STATES), SortByModule],
    declarations: [InstructorCourseDashboardComponent],
    entryComponents: [HomeComponent, InstructorCourseDashboardComponent, JhiMainComponent]
})
export class ArTEMiSInstructorCourseDashboardModule {}
