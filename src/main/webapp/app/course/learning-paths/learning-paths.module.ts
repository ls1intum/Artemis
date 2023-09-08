import { NgModule } from '@angular/core';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { LearningPathManagementComponent } from 'app/course/learning-paths/learning-path-management/learning-path-management.component';
import { NgxGraphModule } from '@swimlane/ngx-graph';
import { ArtemisLectureUnitsModule } from 'app/overview/course-lectures/lecture-units.module';
import { ArtemisCompetenciesModule } from 'app/course/competencies/competency.module';
import { LearningPathProgressModalComponent } from 'app/course/learning-paths/progress-modal/learning-path-progress-modal.component';
import { LearningPathGraphComponent } from 'app/course/learning-paths/learning-path-graph/learning-path-graph.component';
import { LearningPathProgressNavComponent } from 'app/course/learning-paths/progress-modal/learning-path-progress-nav.component';
import { LearningPathGraphNodeComponent } from 'app/course/learning-paths/learning-path-graph/learning-path-graph-node.component';
import { CompetencyNodeDetailsComponent } from 'app/course/learning-paths/learning-path-graph/node-details/competency-node-details.component';
import { LectureUnitNodeDetailsComponent } from 'app/course/learning-paths/learning-path-graph/node-details/lecture-unit-node-details.component';
import { ExerciseNodeDetailsComponent } from 'app/course/learning-paths/learning-path-graph/node-details/exercise-node-details.component';
import { LearningPathHealthStatusWarningComponent } from 'app/course/learning-paths/learning-path-management/learning-path-health-status-warning.component';
import { LearningPathContainerComponent } from 'app/course/learning-paths/participate/learning-path-container.component';
import { Authority } from 'app/shared/constants/authority.constants';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { RouterModule, Routes } from '@angular/router';
import { LearningPathGraphSidebarComponent } from 'app/course/learning-paths/participate/learning-path-graph-sidebar.component';

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
        ArtemisCompetenciesModule,
        RouterModule.forChild(routes),
    ],
    declarations: [
        LearningPathManagementComponent,
        LearningPathHealthStatusWarningComponent,
        LearningPathProgressModalComponent,
        LearningPathProgressNavComponent,
        LearningPathGraphComponent,
        LearningPathGraphNodeComponent,
        CompetencyNodeDetailsComponent,
        LectureUnitNodeDetailsComponent,
        ExerciseNodeDetailsComponent,
        LearningPathContainerComponent,
        LearningPathGraphSidebarComponent,
    ],
    exports: [LearningPathContainerComponent],
})
export class ArtemisLearningPathsModule {}
