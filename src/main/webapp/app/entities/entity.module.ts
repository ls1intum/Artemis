import { CUSTOM_ELEMENTS_SCHEMA, NgModule } from '@angular/core';

import { ArTEMiSCourseModule } from './course/course.module';
import { ArTEMiSExerciseModule } from './exercise/exercise.module';
import { ArTEMiSQuizExerciseModule } from './quiz-exercise/quiz-exercise.module';
import { ArTEMiSParticipationModule } from './participation/participation.module';
import { ArTEMiSProgrammingExerciseModule } from './programming-exercise/programming-exercise.module';
import { ArTEMiSModelingExerciseModule } from './modeling-exercise/modeling-exercise.module';
import { ArTEMiSQuizSubmissionModule } from './quiz-submission/quiz-submission.module';
import { ArTEMiSModelingSubmissionModule } from './modeling-submission/modeling-submission.module';
import { ArTEMiSResultModule } from './result/result.module';
import { ArTEMiSTextExerciseModule } from './text-exercise/text-exercise.module';
import { ArTEMiSFileUploadExerciseModule } from './file-upload-exercise/file-upload-exercise.module';
/* jhipster-needle-add-entity-module-import - JHipster will add entity modules imports here */

@NgModule({
    imports: [
        ArTEMiSCourseModule,
        ArTEMiSExerciseModule,
        ArTEMiSQuizExerciseModule,
        ArTEMiSParticipationModule,
        ArTEMiSProgrammingExerciseModule,
        ArTEMiSModelingExerciseModule,
        ArTEMiSQuizSubmissionModule,
        ArTEMiSModelingSubmissionModule,
        ArTEMiSResultModule,
        ArTEMiSTextExerciseModule,
        ArTEMiSFileUploadExerciseModule,
        /* jhipster-needle-add-entity-module - JHipster will add entity modules here */
    ],
    declarations: [],
    entryComponents: [],
    providers: [],
    schemas: [CUSTOM_ELEMENTS_SCHEMA]
})
export class ArTEMiSEntityModule {}
