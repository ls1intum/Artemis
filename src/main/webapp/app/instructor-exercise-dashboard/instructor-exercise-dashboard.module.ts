import { CUSTOM_ELEMENTS_SCHEMA, NgModule } from '@angular/core';
import { BrowserModule } from '@angular/platform-browser';

import { RouterModule } from '@angular/router';

import { ArTEMiSSharedModule } from '../shared';
import { instructorExerciseDashboardRoute } from './instructor-exercise-dashboard.route';
import { InstructorExerciseDashboardComponent } from './instructor-exercise-dashboard.component';
import { MomentModule } from 'ngx-moment';
import { ClipboardModule } from 'ngx-clipboard';
import { ChartsModule } from 'ng2-charts';
import { ArTEMiSInstructorCourseStatsDashboardModule } from 'app/instructor-course-dashboard';
import { ArTEMiSHeaderExercisePageWithDetailsModule } from 'app/exercise-headers';
import { ArTEMiSSidePanelModule } from 'app/components/side-panel/side-panel.module';

const ENTITY_STATES = instructorExerciseDashboardRoute;

@NgModule({
    imports: [
        BrowserModule,
        ArTEMiSSharedModule,
        MomentModule,
        ClipboardModule,
        RouterModule.forChild(ENTITY_STATES),
        ChartsModule,
        ArTEMiSInstructorCourseStatsDashboardModule,
        ArTEMiSHeaderExercisePageWithDetailsModule,
        ArTEMiSSidePanelModule,
    ],
    declarations: [InstructorExerciseDashboardComponent],
    entryComponents: [],
    providers: [],
    schemas: [CUSTOM_ELEMENTS_SCHEMA],
})
export class ArTEMiSInstructorExerciseStatsDashboardModule {}
