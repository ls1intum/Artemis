import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { LearningPathLectureUnitViewComponent } from 'app/course/learning-paths/participate/lecture-unit/learning-path-lecture-unit-view.component';
import { ArtemisLectureUnitsModule } from 'app/overview/course-lectures/lecture-units.module';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { Authority } from 'app/shared/constants/authority.constants';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { AttachmentUnitComponent } from 'app/overview/course-lectures/attachment-unit/attachment-unit.component';
import { VideoUnitComponent } from 'app/overview/course-lectures/video-unit/video-unit.component';
import { TextUnitComponent } from 'app/overview/course-lectures/text-unit/text-unit.component';
import { OnlineUnitComponent } from 'app/overview/course-lectures/online-unit/online-unit.component';

const routes: Routes = [
    {
        path: '',
        component: LearningPathLectureUnitViewComponent,
        data: {
            authorities: [Authority.USER],
            pageTitle: 'overview.learningPath',
        },
        canActivate: [UserRouteAccessService],
    },
];

@NgModule({
    imports: [ArtemisSharedModule, RouterModule.forChild(routes), ArtemisLectureUnitsModule, AttachmentUnitComponent, VideoUnitComponent, TextUnitComponent, OnlineUnitComponent],
    declarations: [LearningPathLectureUnitViewComponent],
    exports: [LearningPathLectureUnitViewComponent],
})
export class ArtemisLearningPathLectureUnitViewModule {}
