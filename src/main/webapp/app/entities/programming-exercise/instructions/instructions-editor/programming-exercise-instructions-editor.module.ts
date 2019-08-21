import { CUSTOM_ELEMENTS_SCHEMA, NgModule } from '@angular/core';
import { JhiLanguageService } from 'ng-jhipster';
import { JhiLanguageHelper } from 'app/core/language/language.helper';
import { ProgrammingExerciseEditableInstructionComponent, ProgrammingExerciseInstructionTestcaseStatusComponent } from './';
import { ArtemisSharedModule } from 'app/shared';
import { ArtemisProgrammingExerciseInstructionsRenderModule } from 'app/entities/programming-exercise/instructions/instructions-render';
import { ArtemisProgrammingExerciseStatusModule } from 'app/entities/programming-exercise/status';
import { ArtemisMarkdownEditorModule } from 'app/markdown-editor';

@NgModule({
    imports: [ArtemisSharedModule, ArtemisProgrammingExerciseInstructionsRenderModule, ArtemisMarkdownEditorModule, ArtemisProgrammingExerciseStatusModule],
    declarations: [ProgrammingExerciseEditableInstructionComponent, ProgrammingExerciseInstructionTestcaseStatusComponent],
    entryComponents: [ProgrammingExerciseEditableInstructionComponent],
    providers: [{ provide: JhiLanguageService, useClass: JhiLanguageService }],
    exports: [ArtemisProgrammingExerciseInstructionsRenderModule, ProgrammingExerciseEditableInstructionComponent, ProgrammingExerciseInstructionTestcaseStatusComponent],
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
