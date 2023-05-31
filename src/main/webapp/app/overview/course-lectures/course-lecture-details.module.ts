import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { ArtemisSharedPipesModule } from 'app/shared/pipes/shared-pipes.module';
import { ArtemisLectureUnitsModule } from 'app/overview/course-lectures/lecture-units.module';
import { CourseLectureDetailsComponent } from 'app/overview/course-lectures/course-lecture-details.component';
import { NgModule } from '@angular/core';
import { ArtemisCompetenciesModule } from 'app/course/competencies/competency.module';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { Authority } from 'app/shared/constants/authority.constants';
import { RouterModule, Routes } from '@angular/router';
import { ArtemisMarkdownModule } from 'app/shared/markdown.module';

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
                loadChildren: () => import('../discussion-section/discussion-section.module').then((m) => m.DiscussionSectionModule),
            },
        ],
    },
];
@NgModule({
    imports: [
        RouterModule.forChild(routes),
        ArtemisSharedModule,
        ArtemisSharedComponentModule,
        ArtemisSharedPipesModule,
        ArtemisLectureUnitsModule,
        ArtemisCompetenciesModule,
        ArtemisMarkdownModule,
    ],
    declarations: [CourseLectureDetailsComponent],
    exports: [CourseLectureDetailsComponent],
})
export class ArtemisCourseLectureDetailsModule {}
