import { NgModule } from '@angular/core';
import { BrowserModule } from '@angular/platform-browser';

import { RouterModule } from '@angular/router';

import { ArtemisSharedModule } from '../shared';
import { instructorExerciseDashboardRoute } from './instructor-exercise-dashboard.route';
import { InstructorExerciseDashboardComponent } from './instructor-exercise-dashboard.component';
import { MomentModule } from 'ngx-moment';
import { ClipboardModule } from 'ngx-clipboard';
import { ChartsModule } from 'ng2-charts';
import { ArtemisInstructorCourseStatsDashboardModule } from 'app/instructor-course-dashboard';
import { ArtemisHeaderExercisePageWithDetailsModule } from 'app/exercise-headers';
import { ArtemisSidePanelModule } from 'app/components/side-panel/side-panel.module';
import { ArtemisTutorLeaderboardModule } from 'app/instructor-course-dashboard/tutor-leaderboard/tutor-leaderboard.module';

const ENTITY_STATES = instructorExerciseDashboardRoute;

@NgModule({
    imports: [
        BrowserModule,
        ArtemisSharedModule,
        MomentModule,
        ClipboardModule,
        RouterModule.forChild(ENTITY_STATES),
        ChartsModule,
        ArtemisInstructorCourseStatsDashboardModule,
        ArtemisHeaderExercisePageWithDetailsModule,
        ArtemisSidePanelModule,
        ArtemisTutorLeaderboardModule,
    ],
    declarations: [InstructorExerciseDashboardComponent],
    entryComponents: [],
    providers: [],
})
export class ArtemisInstructorExerciseStatsDashboardModule {}
