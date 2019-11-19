import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';
import { JhiAlertService } from 'ng-jhipster';

import { ArtemisSharedModule } from '../shared';
import { tutorCourseDashboardRoute } from './tutor-course-dashboard.route';
import { CourseComponent, CourseExerciseService, CourseService } from '../entities/course';
import { TutorCourseDashboardComponent } from './tutor-course-dashboard.component';
import { RepositoryService } from 'app/entities/repository/repository.service';
import { ArtemisResultModule, ResultComponent, ResultService } from '../entities/result';
import { HomeComponent } from '../home';
import { ParticipationService } from '../entities/participation';
import { MomentModule } from 'ngx-moment';
import { JhiMainComponent } from '../layouts';
import { ClipboardModule } from 'ngx-clipboard';
import { TutorParticipationGraphComponent } from 'app/tutor-course-dashboard/tutor-participation-graph/tutor-participation-graph.component';
import { SortByModule } from 'app/components/pipes';
import { ArtemisTutorLeaderboardModule } from 'app/instructor-course-dashboard/tutor-leaderboard/tutor-leaderboard.module';
import { CourseScoreCalculationService } from 'app/overview';

const ENTITY_STATES = [...tutorCourseDashboardRoute];

@NgModule({
    imports: [ArtemisSharedModule, ArtemisResultModule, MomentModule, ClipboardModule, RouterModule.forChild(ENTITY_STATES), SortByModule, ArtemisTutorLeaderboardModule],
    declarations: [TutorCourseDashboardComponent, TutorParticipationGraphComponent],
    exports: [TutorParticipationGraphComponent],
    entryComponents: [HomeComponent, CourseComponent, JhiMainComponent, ResultComponent],
    providers: [CourseService, JhiAlertService, RepositoryService, ResultService, CourseExerciseService, ParticipationService, CourseScoreCalculationService],
})
export class ArtemisTutorCourseDashboardModule {}
