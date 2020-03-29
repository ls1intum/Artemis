import { NgModule } from '@angular/core';
import { ArtemisQuizParticipationModule } from 'app/exercises/quiz/participate/quiz-participation.module';
import { ArtemisTextExerciseParticipationModule } from 'app/exercises/text/participate/text-exercise-participation.module';
import { ArtemisFileUploadParticipationModule } from 'app/exercises/file-upload/participate/file-upload-participation.module';

@NgModule({
    imports: [ArtemisQuizParticipationModule, ArtemisTextExerciseParticipationModule, ArtemisFileUploadParticipationModule],
})
export class ArtemisExerciseParticipationModule {}
