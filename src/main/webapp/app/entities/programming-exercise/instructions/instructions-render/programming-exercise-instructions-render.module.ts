import { NgModule } from '@angular/core';
import {
    ProgrammingExerciseInstructionComponent,
    ProgrammingExerciseInstructionService,
    ProgrammingExerciseInstructionStepWizardComponent,
    ProgrammingExerciseInstructionTaskStatusComponent,
    ProgrammingExercisePlantUmlExtensionWrapper,
    ProgrammingExercisePlantUmlService,
    ProgrammingExerciseTaskExtensionWrapper,
} from './';
import { ArtemisSharedModule } from 'app/shared';
import { ArtemisMarkdownEditorModule } from 'app/markdown-editor';
import { ArtemisResultModule, ResultDetailComponent } from 'app/entities/result';

@NgModule({
    imports: [ArtemisSharedModule, ArtemisMarkdownEditorModule, ArtemisResultModule],
    declarations: [ProgrammingExerciseInstructionComponent, ProgrammingExerciseInstructionStepWizardComponent, ProgrammingExerciseInstructionTaskStatusComponent],
    entryComponents: [ProgrammingExerciseInstructionComponent, ProgrammingExerciseInstructionTaskStatusComponent],
    exports: [ProgrammingExerciseInstructionComponent],
    providers: [ProgrammingExerciseTaskExtensionWrapper, ProgrammingExercisePlantUmlExtensionWrapper, ProgrammingExerciseInstructionService, ProgrammingExercisePlantUmlService],
})
export class ArtemisProgrammingExerciseInstructionsRenderModule {}
