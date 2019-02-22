import { CUSTOM_ELEMENTS_SCHEMA, NgModule } from '@angular/core';
import { ReactiveFormsModule } from '@angular/forms';
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
    CourseService,
    CourseExercisesOverviewComponent,
    CourseUpdateComponent
} from './';

import { CourseExerciseCardComponent } from 'app/entities/course/course-exercise-card.component';
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
        FormDateTimePickerModule,
        ReactiveFormsModule
    ],
    declarations: [
        CourseComponent,
        CourseDetailComponent,
        CourseDeleteDialogComponent,
        CourseUpdateComponent,
        CourseDeletePopupComponent,
        CourseExerciseCardComponent,
        CourseExercisesOverviewComponent
    ],
    entryComponents: [
        CourseComponent,
        CourseUpdateComponent,
        CourseDeleteDialogComponent,
        CourseDeletePopupComponent,
        CourseExerciseCardComponent,
        CourseDeletePopupComponent
    ],
    providers: [CourseService, CourseExerciseService],
    schemas: [CUSTOM_ELEMENTS_SCHEMA]
})
export class ArTEMiSCourseModule {}
