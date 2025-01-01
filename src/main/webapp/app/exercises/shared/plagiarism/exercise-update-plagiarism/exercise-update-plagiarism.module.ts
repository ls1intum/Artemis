import { NgModule } from '@angular/core';
import { ArtemisSharedCommonModule } from 'app/shared/shared-common.module';
import { ExerciseUpdatePlagiarismComponent } from 'app/exercises/shared/plagiarism/exercise-update-plagiarism/exercise-update-plagiarism.component';

@NgModule({
    imports: [ArtemisSharedCommonModule, ExerciseUpdatePlagiarismComponent],
    exports: [ExerciseUpdatePlagiarismComponent],
})
export class ExerciseUpdatePlagiarismModule {}
