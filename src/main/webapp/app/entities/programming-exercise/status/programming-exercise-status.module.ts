import { NgModule } from '@angular/core';
import { JhiLanguageService } from 'ng-jhipster';
import { JhiLanguageHelper } from 'app/core/language/language.helper';
import { ProgrammingExerciseInstructorExerciseStatusComponent, ProgrammingExerciseInstructorStatusComponent } from './';
import { ArtemisSharedModule } from 'app/shared';
import { ProgrammingExerciseInstructionAnalysisService } from 'app/entities/programming-exercise/instructions/instructions-editor';

@NgModule({
    imports: [ArtemisSharedModule],
    declarations: [ProgrammingExerciseInstructorStatusComponent, ProgrammingExerciseInstructorExerciseStatusComponent],
    providers: [{ provide: JhiLanguageService, useClass: JhiLanguageService }, ProgrammingExerciseInstructionAnalysisService],
    exports: [ProgrammingExerciseInstructorStatusComponent, ProgrammingExerciseInstructorExerciseStatusComponent],
})
export class ArtemisProgrammingExerciseStatusModule {
    constructor(private languageService: JhiLanguageService, private languageHelper: JhiLanguageHelper) {
        this.languageHelper.language.subscribe((languageKey: string) => {
            if (languageKey) {
                this.languageService.changeLanguage(languageKey);
            }
        });
    }
}
