import { NgModule } from '@angular/core';
import { ReactiveFormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { ArtemisTextExerciseModule } from '../../exercises/text/manage/text-exercise/text-exercise.module';
import { ArtemisModelingExerciseModule } from '../../exercises/modeling/manage/modeling-exercise/modeling-exercise.module';
import { ArtemisFileUploadExerciseModule } from '../../exercises/file-upload/manage/file-upload-exercise/file-upload-exercise.module';
import { ArtemisProgrammingExerciseModule } from '../../exercises/programming/manage/programming-exercise.module';

import { CourseExerciseCardComponent } from 'app/course/manage/course-exercise-card.component';
import { FormDateTimePickerModule } from 'app/shared/date-time-picker/date-time-picker.module';
import { ArtemisColorSelectorModule } from 'app/shared/color-selector/color-selector.module';
import { ImageCropperModule } from 'ngx-image-cropper';
import { MomentModule } from 'ngx-moment';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { SortByModule } from 'app/shared/pipes/sort-by.module';
import { CourseExercisesOverviewComponent } from 'app/course/manage/course-exercises-overview.component';
import { CourseDetailComponent } from 'app/course/manage/course-detail.component';
import { CourseUpdateComponent } from 'app/course/manage/course-update.component';
import { courseRoute } from 'app/course/manage/course.route';
import { CourseComponent } from 'app/course/manage/course.component';
import { OrionModule } from 'app/shared/orion/orion.module';
import { ArtemisQuizExerciseModule } from 'app/exercises/quiz/manage/quiz-exercise.module';

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
