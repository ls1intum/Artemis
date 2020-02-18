import { NgModule } from '@angular/core';

import { ArtemisCourseModule } from './course/course.module';
import { ArtemisExerciseModule } from './exercise/exercise.module';
import { ArtemisQuizExerciseModule } from './quiz-exercise/quiz-exercise.module';
import { ArtemisParticipationModule } from './participation/participation.module';
import { ArtemisProgrammingExerciseModule } from './programming-exercise/programming-exercise.module';
import { ArtemisModelingExerciseModule } from './modeling-exercise/modeling-exercise.module';
import { ArtemisTextExerciseModule } from './text-exercise/text-exercise.module';
import { ArtemisFileUploadExerciseModule } from './file-upload-exercise/file-upload-exercise.module';
import { ArtemisLectureModule } from 'app/entities/lecture/lecture.module';
import { ArtemisExerciseHintModule } from 'app/entities/exercise-hint/exercise-hint.module';
import { ArtemisNotificationModule } from 'app/entities/notification/notification.module';
import { ArtemisResultModule } from 'app/entities/result/result.module';

/* jhipster-needle-add-entity-module-import - JHipster will add entity modules imports here */

@NgModule({
    imports: [
        ArtemisCourseModule,
        ArtemisExerciseModule,
        ArtemisQuizExerciseModule,
        ArtemisParticipationModule,
        ArtemisProgrammingExerciseModule,
        ArtemisExerciseHintModule,
        ArtemisModelingExerciseModule,
        ArtemisNotificationModule,
        ArtemisResultModule,
        ArtemisTextExerciseModule,
        ArtemisFileUploadExerciseModule,
        ArtemisLectureModule,
        /* jhipster-needle-add-entity-module - JHipster will add entity modules here */
    ],
    declarations: [],
    entryComponents: [],
    providers: [],
})
export class ArtemisEntityModule {}
