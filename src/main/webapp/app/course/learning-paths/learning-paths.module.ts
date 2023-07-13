import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { LearningPathManagementComponent } from 'app/course/learning-paths/learning-path-management/learning-path-management.component';
import { LearningPathContainerComponent } from 'app/course/learning-paths/participate/learning-path-container.component';
import { Authority } from 'app/shared/constants/authority.constants';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { LearningPathGraphSidebarComponent } from 'app/course/learning-paths/participate/learning-path-graph-sidebar.component';
import { LearningPathGraphComponent } from 'app/course/learning-paths/learning-path-graph/learning-path-graph.component';
import { LearningPathGraphNodeComponent } from 'app/course/learning-paths/learning-path-graph/learning-path-graph-node.component';
import { NgxGraphModule } from '@swimlane/ngx-graph';
import { ArtemisLearningPathLectureUnitViewModule } from 'app/course/learning-paths/participate/lectureunit/learning-path-lecture-unit-view.module';

const routes: Routes = [
    {
        path: '',
        pathMatch: 'prefix',
        data: {
            authorities: [Authority.USER],
            pageTitle: 'overview.learningPath',
        },
        canActivate: [UserRouteAccessService],
        children: [
            {
                path: '',
                pathMatch: 'full',
                component: LearningPathContainerComponent,
                children: [
                    {
                        path: '',
                        pathMatch: 'full',
                        loadChildren: () => import('app/overview/discussion-section/discussion-section.module').then((m) => m.DiscussionSectionModule),
                    },
                ],
            },
        ],
    },
];
@NgModule({
    imports: [ArtemisSharedModule, FormsModule, ReactiveFormsModule, ArtemisSharedComponentModule, RouterModule, NgxGraphModule, ArtemisLearningPathLectureUnitViewModule],
    declarations: [LearningPathContainerComponent, LearningPathManagementComponent, LearningPathGraphSidebarComponent, LearningPathGraphComponent, LearningPathGraphNodeComponent],
    exports: [LearningPathContainerComponent],
})
export class ArtemisLearningPathsModule {}
