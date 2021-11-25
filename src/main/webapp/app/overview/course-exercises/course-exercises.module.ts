import { NgModule } from '@angular/core';
import { CourseExercisesComponent } from 'app/overview/course-exercises/course-exercises.component';
import { Authority } from 'app/shared/constants/authority.constants';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { RouterModule, Routes } from '@angular/router';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ArtemisCourseExerciseRowModule } from 'app/overview/course-exercises/course-exercise-row.module';
import { ArtemisSidePanelModule } from 'app/shared/side-panel/side-panel.module';

const routes: Routes = [
    {
        path: '',
        component: CourseExercisesComponent,
        data: {
            authorities: [Authority.USER],
            pageTitle: 'overview.course',
        },
        canActivate: [UserRouteAccessService],
    },
];

@NgModule({ imports: [ArtemisSharedModule, ArtemisCourseExerciseRowModule, ArtemisSidePanelModule, RouterModule.forChild(routes)], declarations: [CourseExercisesComponent] })
export class CourseExercisesModule {}
