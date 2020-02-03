import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';

import { ArtemisSharedModule } from '../shared';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { HomeComponent } from '../home';
import { MomentModule } from 'ngx-moment';
import { JhiMainComponent } from '../layouts';
import { CourseScoresComponent } from './course-scores.component';
import { SortByModule } from 'app/components/pipes';

const ENTITY_STATES = [
    {
        path: 'course/:courseId/dashboard',
        component: CourseScoresComponent,
        data: {
            authorities: ['ROLE_ADMIN', 'ROLE_INSTRUCTOR', 'ROLE_TA'],
            pageTitle: 'instructorDashboard.title',
        },
        canActivate: [UserRouteAccessService],
    },
];

@NgModule({
    imports: [ArtemisSharedModule, MomentModule, RouterModule.forChild(ENTITY_STATES), SortByModule],
    declarations: [CourseScoresComponent],
    entryComponents: [HomeComponent, CourseScoresComponent, JhiMainComponent],
})
export class ArtemisCourseScoresModule {}
