import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';
import { JhiAlertService } from 'ng-jhipster';

import { ArtemisSharedModule } from '../shared';
import { tutorExerciseDashboardRoute } from './tutor-exercise-dashboard.route';
import { CourseComponent } from '../entities/course';
import { TutorExerciseDashboardComponent } from './tutor-exercise-dashboard.component';
import { ArtemisResultModule } from '../entities/result';
import { HomeComponent } from '../home';
import { MomentModule } from 'ngx-moment';
import { JhiMainComponent } from '../layouts';
import { ClipboardModule } from 'ngx-clipboard';
import { ArtemisTutorCourseDashboardModule } from 'app/tutor-course-dashboard';
import { ArtemisModelingEditorModule } from 'app/modeling-editor';
import { AssessmentInstructionsModule } from 'app/assessment-instructions/assessment-instructions.module';
import { ArtemisHeaderExercisePageWithDetailsModule } from 'app/exercise-headers';
import { ArtemisSidePanelModule } from 'app/components/side-panel/side-panel.module';
import { ArtemisTutorLeaderboardModule } from 'app/instructor-course-dashboard/tutor-leaderboard/tutor-leaderboard.module';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { ArtemisProgrammingExerciseInstructionsRenderModule } from 'app/entities/programming-exercise/instructions/instructions-render';
import { ArtemisProgrammingAssessmentModule } from 'app/programming-assessment/programming-assessment.module';

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
        ArtemisProgrammingAssessmentModule,
        ArtemisResultModule,
    ],
    declarations: [TutorExerciseDashboardComponent],
    entryComponents: [HomeComponent, CourseComponent, JhiMainComponent],
    providers: [JhiAlertService],
})
export class ArtemisTutorExerciseDashboardModule {}
