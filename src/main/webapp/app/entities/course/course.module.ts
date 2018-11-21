import { CUSTOM_ELEMENTS_SCHEMA, NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';

import { ArTEMiSSharedModule } from '../../shared';
import { ArTEMiSQuizExerciseModule } from '../quiz-exercise/quiz-exercise.module';
import { ArTEMiSTextExerciseModule } from '../text-exercise/text-exercise.module';
import { ArTEMiSModelingExerciseModule } from '../modeling-exercise/modeling-exercise.module';
import { ArTEMiSFileUploadExerciseModule } from '../file-upload-exercise/file-upload-exercise.module';
import { ArTEMiSProgrammingExerciseModule } from '../programming-exercise/programming-exercise.module';

import {
    CourseComponent,
    CourseDeleteDialogComponent,
    CourseDeletePopupComponent,
    CourseDetailComponent,
    CourseExerciseService,
    coursePopupRoute,
    courseRoute,
    CourseScoreCalculationComponent,
    CourseService,
    CourseExercisesOverviewComponent,
    CourseUpdateComponent
} from './';
import { CourseScoreCalculationService } from './course-score-calculation.service';
import { FormDateTimePickerModule } from '../../shared/dateTimePicker/date-time-picker.module';

const ENTITY_STATES = [...courseRoute, ...coursePopupRoute];

@NgModule({
    imports: [
        ArTEMiSSharedModule,
        ArTEMiSProgrammingExerciseModule,
        ArTEMiSFileUploadExerciseModule,
        ArTEMiSQuizExerciseModule,
        ArTEMiSTextExerciseModule,
        ArTEMiSModelingExerciseModule,
        RouterModule.forChild(ENTITY_STATES),
        FormDateTimePickerModule
    ],
    declarations: [
        CourseComponent,
        CourseDetailComponent,
        CourseDeleteDialogComponent,
        CourseUpdateComponent,
        CourseDeletePopupComponent,
        CourseScoreCalculationComponent,
        CourseExercisesOverviewComponent
    ],
    entryComponents: [
        CourseComponent,
        CourseUpdateComponent,
        CourseDeleteDialogComponent,
        CourseDeletePopupComponent,
        CourseScoreCalculationComponent,
        CourseExercisesOverviewComponent
    ],
    providers: [CourseService, CourseExerciseService, CourseScoreCalculationService],
    schemas: [CUSTOM_ELEMENTS_SCHEMA]
})
export class ArTEMiSCourseModule {}
