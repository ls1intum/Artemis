import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';

import { ChartsModule } from 'ng2-charts';
import { ClipboardModule } from 'ngx-clipboard';
import { MomentModule } from 'ngx-moment';
import { ArtemisSharedModule } from 'app/shared';
import { ArtemisProgrammingExerciseModule } from 'app/entities/programming-exercise/programming-exercise.module';
import { ArtemisStudentQuestionsModule } from 'app/student-questions/';

import {
    CourseExerciseDetailsComponent,
    CourseExerciseRowComponent,
    CourseExercisesComponent,
    CourseLectureDetailsComponent,
    CourseLecturesComponent,
    CourseOverviewComponent,
    CourseStatisticsComponent,
    ExerciseActionButtonComponent,
    ExerciseDetailsStudentActionsComponent,
    OVERVIEW_ROUTES,
    OverviewComponent,
    OverviewCourseCardComponent,
    ProgrammingExerciseStudentIdeActionsComponent,
} from './';
import { ArtemisResultModule } from 'app/entities/result';
import { ArtemisSidePanelModule } from 'app/components/side-panel/side-panel.module';
import { ArtemisHeaderExercisePageWithDetailsModule } from 'app/exercise-headers';
import { CourseLectureRowComponent } from 'app/overview/course-lectures/course-lecture-row.component';
import { ArtemisCourseRegistrationSelector } from 'app/components/course-registration-selector/course-registration-selector.module';
import { IntellijModule } from 'app/intellij/intellij.module';
import { ArtemisComplaintsModule } from 'app/complaints';
import { FeatureToggleModule } from 'app/feature-toggle/feature-toggle.module';

const ENTITY_STATES = [...OVERVIEW_ROUTES];

@NgModule({
    imports: [
        ArtemisSharedModule,
        ChartsModule,
        ClipboardModule,
        MomentModule,
        ArtemisResultModule,
        ArtemisProgrammingExerciseModule,
        ArtemisStudentQuestionsModule,
        ArtemisSidePanelModule,
        RouterModule.forChild(ENTITY_STATES),
        ArtemisHeaderExercisePageWithDetailsModule,
        ArtemisCourseRegistrationSelector,
        IntellijModule,
        ArtemisComplaintsModule,
        FeatureToggleModule,
    ],
    declarations: [
        OverviewComponent,
        CourseOverviewComponent,
        OverviewCourseCardComponent,
        CourseStatisticsComponent,
        CourseExerciseRowComponent,
        CourseExercisesComponent,
        CourseExerciseDetailsComponent,
        CourseLecturesComponent,
        CourseLectureRowComponent,
        CourseLectureDetailsComponent,
        ExerciseActionButtonComponent,
        ExerciseDetailsStudentActionsComponent,
        ProgrammingExerciseStudentIdeActionsComponent,
    ],
    entryComponents: [],
    exports: [ExerciseActionButtonComponent],
})
export class ArtemisOverviewModule {}
