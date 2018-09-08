import { CUSTOM_ELEMENTS_SCHEMA, NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';

import { ArTEMiSSharedModule } from '../../shared';
import {
    CourseComponent,
    CourseDeleteDialogComponent,
    CourseDeletePopupComponent,
    CourseDetailComponent,
    CourseDialogComponent,
    CourseExerciseService,
    CoursePopupComponent,
    coursePopupRoute,
    CoursePopupService,
    courseRoute,
    CourseScoreCalculationComponent,
    CourseService
} from './';
import { CourseScoreCalculationService } from './courseScoreCalculation.service';
import { FormDateTimePickerModule } from '../../shared/dateTimePicker/date-time-picker.module';

const ENTITY_STATES = [
    ...courseRoute,
    ...coursePopupRoute,
];

@NgModule({
    imports: [
        ArTEMiSSharedModule,
        RouterModule.forChild(ENTITY_STATES),
        FormDateTimePickerModule,
    ],
    declarations: [
        CourseComponent,
        CourseDetailComponent,
        CourseDialogComponent,
        CourseDeleteDialogComponent,
        CoursePopupComponent,
        CourseDeletePopupComponent,
        CourseScoreCalculationComponent
    ],
    entryComponents: [
        CourseComponent,
        CourseDialogComponent,
        CoursePopupComponent,
        CourseDeleteDialogComponent,
        CourseDeletePopupComponent,
        CourseScoreCalculationComponent
    ],
    providers: [
        CourseService,
        CourseExerciseService,
        CoursePopupService,
        CourseScoreCalculationService
    ],
    schemas: [CUSTOM_ELEMENTS_SCHEMA]
})
export class ArTEMiSCourseModule {}
