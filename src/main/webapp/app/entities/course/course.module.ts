import { CUSTOM_ELEMENTS_SCHEMA, NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';

import { ArTEMiSSharedModule } from '../../shared';
import { ArTEMiSQuizExerciseModule } from '../quiz-exercise/quiz-exercise.module';

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
    CourseOverviewComponent,
    CourseUpdateComponent
} from './';
import { CourseScoreCalculationService } from './course-score-calculation.service';
import { FormDateTimePickerModule } from '../../shared/dateTimePicker/date-time-picker.module';

const ENTITY_STATES = [...courseRoute, ...coursePopupRoute];

@NgModule({
    imports: [ArTEMiSSharedModule, ArTEMiSQuizExerciseModule, RouterModule.forChild(ENTITY_STATES), FormDateTimePickerModule],
    declarations: [
        CourseComponent,
        CourseDetailComponent,
        CourseDeleteDialogComponent,
        CourseUpdateComponent,
        CourseDeletePopupComponent,
        CourseScoreCalculationComponent,
        CourseOverviewComponent
    ],
    entryComponents: [
        CourseComponent,
        CourseUpdateComponent,
        CourseDeleteDialogComponent,
        CourseDeletePopupComponent,
        CourseScoreCalculationComponent,
        CourseOverviewComponent
    ],
    providers: [CourseService, CourseExerciseService, CourseScoreCalculationService],
    schemas: [CUSTOM_ELEMENTS_SCHEMA]
})
export class ArTEMiSCourseModule {}
