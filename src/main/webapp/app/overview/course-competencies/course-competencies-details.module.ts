import { CourseCompetenciesDetailsComponent } from 'app/overview/course-competencies/course-competencies-details.component';

import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { ArtemisLectureUnitsModule } from 'app/overview/course-lectures/lecture-units.module';
import { NgModule } from '@angular/core';
import { ArtemisCompetenciesModule } from 'app/course/competencies/competency.module';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { Authority } from 'app/shared/constants/authority.constants';
import { RouterModule, Routes } from '@angular/router';
import { JudgementOfLearningRatingComponent } from 'app/course/competencies/judgement-of-learning-rating/judgement-of-learning-rating.component';
import { AttachmentUnitComponent } from 'app/overview/course-lectures/attachment-unit/attachment-unit.component';
import { VideoUnitComponent } from 'app/overview/course-lectures/video-unit/video-unit.component';
import { TextUnitComponent } from 'app/overview/course-lectures/text-unit/text-unit.component';
import { OnlineUnitComponent } from 'app/overview/course-lectures/online-unit/online-unit.component';

const routes: Routes = [
    {
        path: '',
        loadComponent: () => import('app/overview/course-competencies/course-competencies-details.component').then((m) => m.CourseCompetenciesDetailsComponent),
        data: {
            authorities: [Authority.USER],
            pageTitle: 'overview.competencies',
        },
        canActivate: [UserRouteAccessService],
    },
];
@NgModule({
    imports: [
        RouterModule.forChild(routes),

        ArtemisSharedComponentModule,
        ArtemisLectureUnitsModule,
        ArtemisCompetenciesModule,
        JudgementOfLearningRatingComponent,
        AttachmentUnitComponent,
        VideoUnitComponent,
        TextUnitComponent,
        OnlineUnitComponent,
        CourseCompetenciesDetailsComponent,
    ],
    exports: [CourseCompetenciesDetailsComponent],
})
export class ArtemisCourseCompetenciesDetailsModule {}
