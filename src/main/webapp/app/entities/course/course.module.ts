import { CUSTOM_ELEMENTS_SCHEMA, NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';

import { ArTEMiSSharedModule } from '../../shared';
import {
    CourseComponent,
    CourseDeleteDialogComponent,
    CourseDeletePopupComponent,
    CourseDetailComponent,
    CourseExerciseService,
    CourseScoreCalculationComponent,
    CourseService,
    CourseUpdateComponent,
    coursePopupRoute,
    courseRoute
} from './';
import { CourseScoreCalculationService } from './course-score-calculation.service';
import { FormDateTimePickerModule } from '../../shared/dateTimePicker/date-time-picker.module';

const ENTITY_STATES = [...courseRoute, ...coursePopupRoute];

@NgModule({
    imports: [ArTEMiSSharedModule, RouterModule.forChild(ENTITY_STATES), FormDateTimePickerModule],
    declarations: [
        CourseComponent,
        CourseDetailComponent,
        CourseDeleteDialogComponent,
        CourseUpdateComponent,
        CourseDeletePopupComponent,
        CourseScoreCalculationComponent
    ],
    entryComponents: [
        CourseComponent,
        CourseUpdateComponent,
        CourseDeleteDialogComponent,
        CourseDeletePopupComponent,
        CourseScoreCalculationComponent
    ],
    providers: [CourseService, CourseExerciseService, CourseScoreCalculationService],
    schemas: [CUSTOM_ELEMENTS_SCHEMA]
})
export class ArTEMiSCourseModule {}
