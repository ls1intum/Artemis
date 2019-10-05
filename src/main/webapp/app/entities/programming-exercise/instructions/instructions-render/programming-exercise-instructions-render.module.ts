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
import { ResultDetailComponent } from 'app/entities/result';

@NgModule({
    imports: [ArtemisSharedModule, ArtemisMarkdownEditorModule],
    declarations: [ProgrammingExerciseInstructionComponent, ProgrammingExerciseInstructionStepWizardComponent, ProgrammingExerciseInstructionTaskStatusComponent],
    entryComponents: [ProgrammingExerciseInstructionComponent, ResultDetailComponent, ProgrammingExerciseInstructionTaskStatusComponent],
    exports: [ProgrammingExerciseInstructionComponent],
    providers: [ProgrammingExerciseTaskExtensionWrapper, ProgrammingExercisePlantUmlExtensionWrapper, ProgrammingExerciseInstructionService, ProgrammingExercisePlantUmlService],
})
export class ArtemisProgrammingExerciseInstructionsRenderModule {}
