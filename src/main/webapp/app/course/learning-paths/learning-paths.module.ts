import { NgModule } from '@angular/core';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { NgxGraphModule } from '@swimlane/ngx-graph';
import { ArtemisLectureUnitsModule } from 'app/overview/course-lectures/lecture-units.module';
import { LearningPathContainerComponent } from 'app/course/learning-paths/participate/learning-path-container.component';
import { Authority } from 'app/shared/constants/authority.constants';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { RouterModule, Routes } from '@angular/router';
import { ArtemisLearningPathProgressModule } from 'app/course/learning-paths/progress-modal/learning-path-progress.module';
import { ArtemisLearningPathGraphModule } from 'app/course/learning-paths/learning-path-graph/learning-path-graph.module';

const routes: Routes = [
    {
        path: '',
        component: LearningPathContainerComponent,
        data: {
            authorities: [Authority.USER],
            pageTitle: 'overview.learningPath',
        },
        canActivate: [UserRouteAccessService],
        children: [
            {
                path: 'lecture-unit',
                pathMatch: 'full',
                children: [
                    {
                        path: '',
                        pathMatch: 'full',
                        loadChildren: () =>
                            import('app/course/learning-paths/participate/lecture-unit/learning-path-lecture-unit-view.module').then(
                                (m) => m.ArtemisLearningPathLectureUnitViewModule,
                            ),
                    },
                ],
            },
            {
                path: 'exercise',
                pathMatch: 'full',
                children: [
                    {
                        path: '',
                        pathMatch: 'full',
                        loadChildren: () => import('app/overview/exercise-details/course-exercise-details.module').then((m) => m.CourseExerciseDetailsModule),
                    },
                ],
            },
        ],
    },
];

@NgModule({
    imports: [
        ArtemisSharedModule,
        FormsModule,
        ReactiveFormsModule,
        ArtemisSharedComponentModule,
        NgxGraphModule,
        ArtemisLectureUnitsModule,
        RouterModule.forChild(routes),
        ArtemisLearningPathGraphModule,
        ArtemisLearningPathProgressModule,
    ],
    declarations: [LearningPathContainerComponent],
    exports: [LearningPathContainerComponent],
})
export class ArtemisLearningPathsModule {}
