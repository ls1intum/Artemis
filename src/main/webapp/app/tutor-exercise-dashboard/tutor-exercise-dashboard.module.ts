import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';
import { AlertService } from 'app/core/alert/alert.service';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { tutorExerciseDashboardRoute } from './tutor-exercise-dashboard.route';
import { TutorExerciseDashboardComponent } from './tutor-exercise-dashboard.component';
import { MomentModule } from 'ngx-moment';
import { ClipboardModule } from 'ngx-clipboard';
import { AssessmentInstructionsModule } from 'app/assessment-instructions/assessment-instructions.module';
import { ArtemisSidePanelModule } from 'app/components/side-panel/side-panel.module';
import { ArtemisTutorLeaderboardModule } from 'app/instructor-course-dashboard/tutor-leaderboard/tutor-leaderboard.module';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { ArtemisProgrammingAssessmentModule } from 'app/programming-assessment/programming-assessment.module';
import { ArtemisAssessmentSharedModule } from 'app/assessment-shared/assessment-shared.module';
import { ArtemisTutorCourseDashboardModule } from 'app/tutor-course-dashboard/tutor-course-dashboard.module';
import { JhiMainComponent } from 'app/layouts/main/main.component';
import { ArtemisHeaderExercisePageWithDetailsModule } from 'app/exercise-headers/exercise-headers.module';
import { HomeComponent } from 'app/home/home.component';
import { ArtemisProgrammingExerciseInstructionsRenderModule } from 'app/entities/programming-exercise/instructions/instructions-render/programming-exercise-instructions-render.module';
import { ArtemisResultModule } from 'app/entities/result/result.module';
import { CourseComponent } from 'app/entities/course/course.component';
import { ArtemisModelingEditorModule } from 'app/modeling-editor/modeling-editor.module';

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
        ArtemisAssessmentSharedModule,
    ],
    declarations: [TutorExerciseDashboardComponent],
    entryComponents: [HomeComponent, CourseComponent, JhiMainComponent],
    providers: [],
})
export class ArtemisTutorExerciseDashboardModule {}
