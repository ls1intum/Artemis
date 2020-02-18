import { NgModule } from '@angular/core';
import { ReactiveFormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { ArtemisQuizExerciseModule } from '../quiz-exercise/quiz-exercise.module';
import { ArtemisTextExerciseModule } from '../text-exercise/text-exercise.module';
import { ArtemisModelingExerciseModule } from '../modeling-exercise/modeling-exercise.module';
import { ArtemisFileUploadExerciseModule } from '../file-upload-exercise/file-upload-exercise.module';
import { ArtemisProgrammingExerciseModule } from '../programming-exercise/programming-exercise.module';

import { CourseExerciseCardComponent } from 'app/entities/course/course-exercise-card.component';
import { FormDateTimePickerModule } from 'app/shared/date-time-picker/date-time-picker.module';
import { ArtemisColorSelectorModule } from 'app/components/color-selector/color-selector.module';
import { ImageCropperModule } from 'ngx-image-cropper';
import { MomentModule } from 'ngx-moment';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { SortByModule } from 'app/components/pipes/sort-by.module';
import { CourseExercisesOverviewComponent } from 'app/entities/course/course-exercises-overview.component';
import { CourseDetailComponent } from 'app/entities/course/course-detail.component';
import { CourseUpdateComponent } from 'app/entities/course/course-update.component';
import { courseRoute } from 'app/entities/course/course.route';
import { CourseComponent } from 'app/entities/course/course.component';
import { OrionModule } from 'app/orion/orion.module';

const ENTITY_STATES = [...courseRoute];

@NgModule({
    imports: [
        ArtemisSharedModule,
        ArtemisProgrammingExerciseModule,
        ArtemisFileUploadExerciseModule,
        ArtemisQuizExerciseModule,
        ArtemisTextExerciseModule,
        ArtemisModelingExerciseModule,
        RouterModule.forChild(ENTITY_STATES),
        SortByModule,
        FormDateTimePickerModule,
        ReactiveFormsModule,
        ArtemisColorSelectorModule,
        ImageCropperModule,
        MomentModule,
        OrionModule,
    ],
    declarations: [CourseComponent, CourseDetailComponent, CourseUpdateComponent, CourseExerciseCardComponent, CourseExercisesOverviewComponent],
    entryComponents: [CourseComponent, CourseUpdateComponent, CourseExerciseCardComponent],
})
export class ArtemisCourseModule {}
