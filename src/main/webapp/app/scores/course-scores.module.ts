import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { MomentModule } from 'ngx-moment';
import { CourseScoresComponent } from './course-scores.component';
import { SortByModule } from 'app/components/pipes/sort-by.module';
import { JhiMainComponent } from 'app/layouts/main/main.component';
import { HomeComponent } from 'app/home/home.component';

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
