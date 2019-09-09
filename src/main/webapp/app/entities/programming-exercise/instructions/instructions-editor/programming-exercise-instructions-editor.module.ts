import { NgModule } from '@angular/core';
import { JhiLanguageService } from 'ng-jhipster';
import { JhiLanguageHelper } from 'app/core';
import { ProgrammingExerciseEditableInstructionComponent, ProgrammingExerciseInstructionAnalysisService, ProgrammingExerciseInstructionAnalysisComponent } from './';
import { ArtemisSharedModule } from 'app/shared';
import { ArtemisProgrammingExerciseInstructionsRenderModule } from 'app/entities/programming-exercise/instructions/instructions-render';
import { ArtemisProgrammingExerciseStatusModule } from 'app/entities/programming-exercise/status';
import { ArtemisMarkdownEditorModule } from 'app/markdown-editor';

@NgModule({
    imports: [ArtemisSharedModule, ArtemisProgrammingExerciseInstructionsRenderModule, ArtemisMarkdownEditorModule, ArtemisProgrammingExerciseStatusModule],
    declarations: [ProgrammingExerciseEditableInstructionComponent, ProgrammingExerciseInstructionAnalysisComponent],
    entryComponents: [ProgrammingExerciseEditableInstructionComponent],
    providers: [{ provide: JhiLanguageService, useClass: JhiLanguageService }, ProgrammingExerciseInstructionAnalysisService],
    exports: [ArtemisProgrammingExerciseInstructionsRenderModule, ProgrammingExerciseEditableInstructionComponent],
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
