import { CUSTOM_ELEMENTS_SCHEMA, NgModule } from '@angular/core';
import { JhiLanguageService } from 'ng-jhipster';
import { JhiLanguageHelper } from 'app/core/language/language.helper';
import { ProgrammingExerciseInstructorExerciseStatusComponent, ProgrammingExerciseInstructorStatusComponent } from './';
import { ArtemisParticipationModule } from 'app/entities/participation/participation.module';
import { ArtemisSharedModule } from 'app/shared';

@NgModule({
    imports: [ArtemisSharedModule, ArtemisParticipationModule],
    declarations: [ProgrammingExerciseInstructorStatusComponent, ProgrammingExerciseInstructorExerciseStatusComponent],
    providers: [{ provide: JhiLanguageService, useClass: JhiLanguageService }],
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
