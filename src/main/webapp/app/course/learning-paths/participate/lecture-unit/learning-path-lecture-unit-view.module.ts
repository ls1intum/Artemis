import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { LearningPathLectureUnitViewComponent } from 'app/course/learning-paths/participate/lecture-unit/learning-path-lecture-unit-view.component';
import { ArtemisLectureUnitsModule } from 'app/overview/course-lectures/lecture-units.module';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { Authority } from 'app/shared/constants/authority.constants';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';

const routes: Routes = [
    {
        path: '',
        component: LearningPathLectureUnitViewComponent,
        data: {
            authorities: [Authority.USER],
            pageTitle: 'overview.learningPath',
        },
        canActivate: [UserRouteAccessService],
        children: [
            {
                path: '',
                pathMatch: 'full',
                loadChildren: () => import('app/overview/discussion-section/discussion-section.module').then((m) => m.DiscussionSectionModule),
            },
        ],
    },
];

@NgModule({
    imports: [ArtemisSharedModule, RouterModule.forChild(routes), ArtemisLectureUnitsModule],
    declarations: [LearningPathLectureUnitViewComponent],
    exports: [LearningPathLectureUnitViewComponent],
})
export class ArtemisLearningPathLectureUnitViewModule {}
