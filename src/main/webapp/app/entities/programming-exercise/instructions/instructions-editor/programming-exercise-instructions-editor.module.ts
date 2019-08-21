import { CUSTOM_ELEMENTS_SCHEMA, NgModule } from '@angular/core';
import { JhiLanguageService } from 'ng-jhipster';
import { JhiLanguageHelper } from 'app/core';
import { ProgrammingExerciseEditableInstructionComponent, ProgrammingExerciseInstructionTestcaseStatusComponent } from './';
import { ArtemisSharedModule } from 'app/shared';
import { ArtemisMarkdownEditorModule } from 'app/markdown-editor';
import { ArtemisProgrammingExerciseInstructionsRenderModule } from 'app/entities/programming-exercise/instructions/instructions-render';

@NgModule({
    imports: [ArtemisSharedModule, ArtemisMarkdownEditorModule, ArtemisProgrammingExerciseInstructionsRenderModule],
    declarations: [ProgrammingExerciseEditableInstructionComponent, ProgrammingExerciseInstructionTestcaseStatusComponent],
    entryComponents: [ProgrammingExerciseEditableInstructionComponent],
    exports: [ProgrammingExerciseEditableInstructionComponent, ProgrammingExerciseInstructionTestcaseStatusComponent],
    schemas: [CUSTOM_ELEMENTS_SCHEMA],
})
export class ArtemisProgrammingExerciseInstructionsEditorModule {
    constructor(private languageService: JhiLanguageService, private languageHelper: JhiLanguageHelper) {
        this.languageHelper.language.subscribe((languageKey: string) => {
            if (languageKey) {
                this.languageService.changeLanguage(languageKey);
            }
        });
    }
}
