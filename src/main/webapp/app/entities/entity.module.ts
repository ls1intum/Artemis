import { NgModule } from '@angular/core';

import { ArtemisExerciseModule } from '../exercises/shared/exercise/exercise.module';
import { ArtemisParticipationModule } from '../exercises/shared/participation/participation.module';
import { ArtemisTeamModule } from 'app/exercises/shared/team/team.module';
import { ArtemisProgrammingExerciseModule } from '../exercises/programming/manage/programming-exercise.module';
import { ArtemisModelingExerciseModule } from '../exercises/modeling/manage/modeling-exercise/modeling-exercise.module';
import { ArtemisTextExerciseModule } from '../exercises/text/manage/text-exercise/text-exercise.module';
import { ArtemisFileUploadExerciseModule } from '../exercises/file-upload/manage/file-upload-exercise/file-upload-exercise.module';
import { ArtemisLectureModule } from 'app/lecture/lecture.module';
import { ArtemisExerciseHintModule } from 'app/exercises/shared/exercise-hint/exercise-hint.module';
import { ArtemisNotificationModule } from 'app/overview/notification/notification.module';
import { ArtemisResultModule } from 'app/exercises/shared/result/result.module';
import { ArtemisCourseModule } from 'app/course/manage/course.module';
import { ArtemisQuizExerciseModule } from 'app/exercises/quiz/manage/quiz-exercise.module';

/* jhipster-needle-add-entity-module-import - JHipster will add entity modules imports here */

@NgModule({
    imports: [
        ArtemisCourseModule,
        ArtemisExerciseModule,
        ArtemisQuizExerciseModule,
        ArtemisParticipationModule,
        ArtemisTeamModule,
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
