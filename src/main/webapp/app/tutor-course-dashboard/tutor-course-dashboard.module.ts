import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';
import { JhiAlertService } from 'ng-jhipster';

import { ArtemisSharedModule } from '../shared';
import { tutorCourseDashboardRoute } from './tutor-course-dashboard.route';
import { CourseComponent } from '../entities/course';
import { TutorCourseDashboardComponent } from './tutor-course-dashboard.component';
import { ArtemisResultModule, ResultComponent } from '../entities/result';
import { HomeComponent } from '../home';
import { MomentModule } from 'ngx-moment';
import { JhiMainComponent } from '../layouts';
import { ClipboardModule } from 'ngx-clipboard';
import { TutorParticipationGraphComponent } from 'app/tutor-course-dashboard/tutor-participation-graph/tutor-participation-graph.component';
import { SortByModule } from 'app/components/pipes';
import { ArtemisTutorLeaderboardModule } from 'app/instructor-course-dashboard/tutor-leaderboard/tutor-leaderboard.module';
import { ProgressBarComponent } from 'app/tutor-course-dashboard/tutor-participation-graph/progress-bar/progress-bar.component';

const ENTITY_STATES = [...tutorCourseDashboardRoute];

@NgModule({
    imports: [ArtemisSharedModule, ArtemisResultModule, MomentModule, ClipboardModule, RouterModule.forChild(ENTITY_STATES), SortByModule, ArtemisTutorLeaderboardModule],
    declarations: [TutorCourseDashboardComponent, TutorParticipationGraphComponent, ProgressBarComponent],
    exports: [TutorParticipationGraphComponent, ProgressBarComponent],
    entryComponents: [HomeComponent, CourseComponent, JhiMainComponent, ResultComponent],
    providers: [JhiAlertService],
})
export class ArtemisTutorCourseDashboardModule {}
