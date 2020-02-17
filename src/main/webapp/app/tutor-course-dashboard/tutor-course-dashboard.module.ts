import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';
import { AlertService } from 'app/core/alert/alert.service';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { tutorCourseDashboardRoute } from './tutor-course-dashboard.route';
import { TutorCourseDashboardComponent } from './tutor-course-dashboard.component';
import { MomentModule } from 'ngx-moment';
import { ClipboardModule } from 'ngx-clipboard';
import { TutorParticipationGraphComponent } from 'app/tutor-course-dashboard/tutor-participation-graph/tutor-participation-graph.component';
import { SortByModule } from 'app/components/pipes/sort-by.module';
import { ArtemisTutorLeaderboardModule } from 'app/instructor-course-dashboard/tutor-leaderboard/tutor-leaderboard.module';
import { ProgressBarComponent } from 'app/tutor-course-dashboard/tutor-participation-graph/progress-bar/progress-bar.component';
import { JhiMainComponent } from 'app/layouts/main/main.component';
import { HomeComponent } from 'app/home/home.component';
import { ArtemisResultModule } from 'app/entities/result/result.module';
import { ResultComponent } from 'app/entities/result/result.component';
import { CourseComponent } from 'app/entities/course/course.component';

const ENTITY_STATES = [...tutorCourseDashboardRoute];

@NgModule({
    imports: [ArtemisSharedModule, ArtemisResultModule, MomentModule, ClipboardModule, RouterModule.forChild(ENTITY_STATES), SortByModule, ArtemisTutorLeaderboardModule],
    declarations: [TutorCourseDashboardComponent, TutorParticipationGraphComponent, ProgressBarComponent],
    exports: [TutorParticipationGraphComponent, ProgressBarComponent],
    entryComponents: [HomeComponent, CourseComponent, JhiMainComponent, ResultComponent],
    providers: [],
})
export class ArtemisTutorCourseDashboardModule {}
