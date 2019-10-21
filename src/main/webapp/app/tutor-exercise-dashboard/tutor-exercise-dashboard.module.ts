import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';
import { JhiAlertService } from 'ng-jhipster';

import { ArtemisSharedModule } from '../shared';
import { tutorExerciseDashboardRoute } from './tutor-exercise-dashboard.route';
import { CourseComponent, CourseExerciseService, CourseService } from '../entities/course';
import { TutorExerciseDashboardComponent } from './tutor-exercise-dashboard.component';
import { RepositoryService } from 'app/entities/repository';
import { ArtemisResultModule, ResultComponent, ResultService } from '../entities/result';
import { HomeComponent } from '../home';
import { ParticipationService } from '../entities/participation';
import { MomentModule } from 'ngx-moment';
import { JhiMainComponent } from '../layouts';
import { ClipboardModule } from 'ngx-clipboard';
import { ArtemisTutorCourseDashboardModule } from 'app/tutor-course-dashboard';
import { ArtemisModelingEditorModule } from 'app/modeling-editor';
import { AssessmentInstructionsModule } from 'app/assessment-instructions/assessment-instructions.module';
import { ArtemisHeaderExercisePageWithDetailsModule } from 'app/exercise-headers';
import { ArtemisSidePanelModule } from 'app/components/side-panel/side-panel.module';
import { ArtemisTutorLeaderboardModule } from 'app/instructor-course-dashboard/tutor-leaderboard/tutor-leaderboard.module';
import { CourseScoreCalculationService } from 'app/overview';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { ArtemisProgrammingExerciseInstructionsRenderModule } from 'app/entities/programming-exercise/instructions/instructions-render';

const ENTITY_STATES = [...tutorExerciseDashboardRoute];

@NgModule({
    imports: [
        ArtemisSharedModule,
        ArtemisResultModule,
        MomentModule,
        ClipboardModule,
        RouterModule.forChild(ENTITY_STATES),
        ArtemisTutorCourseDashboardModule,
        ArtemisModelingEditorModule,
        AssessmentInstructionsModule,
        ArtemisHeaderExercisePageWithDetailsModule,
        ArtemisSidePanelModule,
        ArtemisTutorLeaderboardModule,
        ArtemisSharedComponentModule,
        ArtemisProgrammingExerciseInstructionsRenderModule,
    ],
    declarations: [TutorExerciseDashboardComponent],
    exports: [ResultComponent],
    entryComponents: [HomeComponent, CourseComponent, JhiMainComponent, ResultComponent],
    providers: [CourseService, JhiAlertService, RepositoryService, ResultService, CourseExerciseService, ParticipationService, CourseScoreCalculationService],
})
export class ArtemisTutorExerciseDashboardModule {}
