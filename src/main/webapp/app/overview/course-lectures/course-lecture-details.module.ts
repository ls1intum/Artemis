import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { ArtemisSharedPipesModule } from 'app/shared/pipes/shared-pipes.module';
import { MomentModule } from 'ngx-moment';
import { ArtemisLectureUnitsModule } from 'app/overview/course-lectures/lecture-units.module';
import { CourseLectureDetailsComponent } from 'app/overview/course-lectures/course-lecture-details.component';
import { NgModule } from '@angular/core';
import { ArtemisLearningGoalsModule } from 'app/course/learning-goals/learning-goal.module';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { Authority } from 'app/shared/constants/authority.constants';
import { RouterModule, Routes } from '@angular/router';

const routes: Routes = [
    {
        path: '',
        component: CourseLectureDetailsComponent,
        data: {
            authorities: [Authority.USER],
            pageTitle: 'overview.lectures',
        },
        canActivate: [UserRouteAccessService],
        children: [
            {
                path: '',
                pathMatch: 'full',
                loadChildren: () => import('../../overview/student-questions/student-questions.module').then((m) => m.ArtemisStudentQuestionsModule),
            },
        ],
    },
];
@NgModule({
    imports: [
        RouterModule.forChild(routes),
        ArtemisSharedModule,
        ArtemisSharedComponentModule,
        MomentModule,
        ArtemisSharedPipesModule,
        ArtemisLectureUnitsModule,
        ArtemisLearningGoalsModule,
    ],
    declarations: [CourseLectureDetailsComponent],
    exports: [CourseLectureDetailsComponent],
})
export class ArtemisCourseLectureDetailsModule {}
