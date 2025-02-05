import { CourseLectureDetailsComponent } from 'app/overview/course-lectures/course-lecture-details.component';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { ArtemisLectureUnitsModule } from 'app/overview/course-lectures/lecture-units.module';

import { NgModule } from '@angular/core';
import { ArtemisCompetenciesModule } from 'app/course/competencies/competency.module';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { Authority } from 'app/shared/constants/authority.constants';
import { RouterModule, Routes } from '@angular/router';
import { VideoUnitComponent } from 'app/overview/course-lectures/video-unit/video-unit.component';
import { TextUnitComponent } from 'app/overview/course-lectures/text-unit/text-unit.component';
import { OnlineUnitComponent } from 'app/overview/course-lectures/online-unit/online-unit.component';
import { AttachmentUnitComponent } from 'app/overview/course-lectures/attachment-unit/attachment-unit.component';
import { DiscussionSectionComponent } from 'app/overview/discussion-section/discussion-section.component';
import { IrisModule } from 'app/iris/iris.module';

const routes: Routes = [
    {
        path: '',
        loadComponent: () => import('app/overview/course-lectures/course-lecture-details.component').then((m) => m.CourseLectureDetailsComponent),
        data: {
            authorities: [Authority.USER],
            pageTitle: 'overview.lectures',
        },
        canActivate: [UserRouteAccessService],
    },
];
@NgModule({
    imports: [
        RouterModule.forChild(routes),
        ArtemisSharedModule,
        ArtemisSharedComponentModule,
        ArtemisLectureUnitsModule,
        ArtemisCompetenciesModule,
        VideoUnitComponent,
        TextUnitComponent,
        OnlineUnitComponent,
        AttachmentUnitComponent,
        DiscussionSectionComponent,
        IrisModule,
        CourseLectureDetailsComponent,
    ],
    exports: [CourseLectureDetailsComponent],
})
export class ArtemisCourseLectureDetailsModule {}
