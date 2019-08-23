import { NgModule } from '@angular/core';
import { JhiLanguageService } from 'ng-jhipster';
import { JhiLanguageHelper } from 'app/core/language/language.helper';

import { ArtemisSharedModule } from 'app/shared';
import { ProgrammingExerciseInstructorTriggerBuildButtonComponent } from 'app/entities/programming-exercise/actions/programming-exercise-instructor-trigger-build-button.component';
import { ProgrammingExerciseStudentTriggerBuildButtonComponent } from 'app/entities/programming-exercise/actions/programming-exercise-student-trigger-build-button.component';

@NgModule({
    imports: [ArtemisSharedModule],
    declarations: [ProgrammingExerciseInstructorTriggerBuildButtonComponent, ProgrammingExerciseStudentTriggerBuildButtonComponent],
    exports: [ProgrammingExerciseInstructorTriggerBuildButtonComponent, ProgrammingExerciseStudentTriggerBuildButtonComponent],
    providers: [],
})
export class ArtemisProgrammingExerciseActionsModule {
    constructor(private languageService: JhiLanguageService, private languageHelper: JhiLanguageHelper) {
        this.languageHelper.language.subscribe((languageKey: string) => {
            if (languageKey) {
                this.languageService.changeLanguage(languageKey);
            }
        });
    }
}
