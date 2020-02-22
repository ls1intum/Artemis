import { NgModule } from '@angular/core';
import { ReactiveFormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { ArtemisTextExerciseModule } from 'app/exercises/text/manage/text-exercise/text-exercise.module';
import { ArtemisModelingExerciseModule } from 'app/exercises/modeling/manage/modeling-exercise.module';
import { ArtemisFileUploadExerciseManagementModule } from 'app/exercises/file-upload/manage/file-upload-exercise-management.module';

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
import { ArtemisProgrammingExerciseManagementModule } from 'app/exercises/programming/manage/programming-exercise-management.module';
import { ArtemisQuizManagementModule } from 'app/exercises/quiz/manage/quiz-management.module';
import { ArtemisProgrammingExerciseModule } from 'app/exercises/programming/shared/programming-exercise.module';

const ENTITY_STATES = [...courseRoute];

@NgModule({
    imports: [
        ArtemisSharedModule,
        ArtemisFileUploadExerciseManagementModule,
        ArtemisQuizManagementModule,
        ArtemisTextExerciseModule,
        ArtemisModelingExerciseModule,
        RouterModule.forChild(ENTITY_STATES),
        SortByModule,
        FormDateTimePickerModule,
        ReactiveFormsModule,
        ArtemisColorSelectorModule,
        ImageCropperModule,
        MomentModule,
        ArtemisProgrammingExerciseManagementModule,
        OrionModule,
        ArtemisProgrammingExerciseModule,
        ArtemisFileUploadExerciseManagementModule,
    ],
    declarations: [CourseComponent, CourseDetailComponent, CourseUpdateComponent, CourseExerciseCardComponent, CourseExercisesOverviewComponent],
})
export class ArtemisCourseModule {}
