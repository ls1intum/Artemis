import { CUSTOM_ELEMENTS_SCHEMA, NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';
import { JhiLanguageService } from 'ng-jhipster';
import { JhiLanguageHelper } from 'app/core';
import { NgxDatatableModule } from '@swimlane/ngx-datatable';

import { ArtemisSharedModule } from 'app/shared';
import {
    ProgrammingExerciseArchiveDialogComponent,
    ProgrammingExerciseArchivePopupComponent,
    ProgrammingExerciseCleanupDialogComponent,
    ProgrammingExerciseCleanupPopupComponent,
    ProgrammingExerciseComponent,
    ProgrammingExerciseDeleteDialogComponent,
    ProgrammingExerciseDeletePopupComponent,
    ProgrammingExerciseDetailComponent,
    ProgrammingExerciseDialogComponent,
    ProgrammingExerciseEditableInstructionComponent,
    ProgrammingExerciseInstructionComponent,
    ProgrammingExerciseInstructionTaskStatusComponent,
    ProgrammingExerciseInstructionTestcaseStatusComponent,
    ProgrammingExerciseInstructorStatusComponent,
    ProgrammingExerciseInstructorTriggerBuildButtonComponent,
    ProgrammingExerciseManageTestCasesComponent,
    ProgrammingExerciseParticipationService,
    ProgrammingExercisePopupComponent,
    programmingExercisePopupRoute,
    ProgrammingExercisePopupService,
    programmingExerciseRoute,
    ProgrammingExerciseService,
    ProgrammingExerciseStudentTriggerBuildButtonComponent,
    ProgrammingExerciseTestCaseService,
    ProgrammingExerciseUpdateComponent,
} from './';
import { ArtemisMarkdownEditorModule } from 'app/markdown-editor';
import { SortByModule } from 'app/components/pipes';
import { FormDateTimePickerModule } from 'app/shared/date-time-picker/date-time-picker.module';
import { ArtemisCategorySelectorModule } from 'app/components/category-selector/category-selector.module';
import { ArtemisDifficultyPickerModule } from 'app/components/exercise/difficulty-picker/difficulty-picker.module';
import { ArtemisResultModule } from 'app/entities/result';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ProgrammingExerciseInstructorExerciseStatusComponent } from 'app/entities/programming-exercise/status/programming-exercise-instructor-exercise-status.component';
import { ArtemisTableModule } from 'app/components/table/table.module';
import { ProgrammingExerciseTaskExtensionWrapper } from 'app/entities/programming-exercise/instructions/extensions/programming-exercise-task.extension';
import { ProgrammingExerciseInstructionService } from 'app/entities/programming-exercise/instructions/programming-exercise-instruction.service';
import { ProgrammingExerciseInstructionStepWizardComponent } from 'app/entities/programming-exercise/instructions/programming-exercise-instruction-step-wizard.component';
import { ProgrammingExercisePlantUmlExtensionWrapper } from 'app/entities/programming-exercise/instructions/extensions/programming-exercise-plant-uml.extension';
import { ProgrammingExercisePlantUmlService } from 'app/entities/programming-exercise/instructions/programming-exercise-plant-uml.service';
import { ProgrammingExerciseInstructionResultDetailComponent } from './instructions/programming-exercise-instructions-result-detail.component';

const ENTITY_STATES = [...programmingExerciseRoute, ...programmingExercisePopupRoute];

@NgModule({
    imports: [
        ArtemisSharedModule,
        RouterModule.forChild(ENTITY_STATES),
        SortByModule,
        FormDateTimePickerModule,
        ArtemisCategorySelectorModule,
        ArtemisDifficultyPickerModule,
        ArtemisResultModule,
        ArtemisMarkdownEditorModule,
        ArtemisTableModule,
        NgxDatatableModule,
    ],
    declarations: [
        ProgrammingExerciseComponent,
        ProgrammingExerciseDetailComponent,
        ProgrammingExerciseUpdateComponent,
        ProgrammingExerciseDialogComponent,
        ProgrammingExerciseDeleteDialogComponent,
        ProgrammingExercisePopupComponent,
        ProgrammingExerciseDeletePopupComponent,
        ProgrammingExerciseArchiveDialogComponent,
        ProgrammingExerciseArchivePopupComponent,
        ProgrammingExerciseCleanupDialogComponent,
        ProgrammingExerciseCleanupPopupComponent,
        ProgrammingExerciseInstructionComponent,
        ProgrammingExerciseEditableInstructionComponent,
        ProgrammingExerciseInstructorStatusComponent,
        ProgrammingExerciseInstructorExerciseStatusComponent,
        ProgrammingExerciseInstructionStepWizardComponent,
        ProgrammingExerciseInstructionResultDetailComponent,
        ProgrammingExerciseInstructionTestcaseStatusComponent,
        ProgrammingExerciseInstructionTaskStatusComponent,
        ProgrammingExerciseManageTestCasesComponent,
        ProgrammingExerciseInstructorTriggerBuildButtonComponent,
        ProgrammingExerciseStudentTriggerBuildButtonComponent,
    ],
    entryComponents: [
        ProgrammingExerciseComponent,
        ProgrammingExerciseDialogComponent,
        ProgrammingExerciseUpdateComponent,
        ProgrammingExercisePopupComponent,
        ProgrammingExerciseDeleteDialogComponent,
        ProgrammingExerciseDeletePopupComponent,
        ProgrammingExerciseArchiveDialogComponent,
        ProgrammingExerciseArchivePopupComponent,
        ProgrammingExerciseCleanupDialogComponent,
        ProgrammingExerciseCleanupPopupComponent,
        ProgrammingExerciseInstructorStatusComponent,
        ProgrammingExerciseInstructionComponent,
        ProgrammingExerciseInstructionTaskStatusComponent,
        ProgrammingExerciseInstructionStepWizardComponent,
        ProgrammingExerciseInstructionResultDetailComponent,
        ProgrammingExerciseInstructorExerciseStatusComponent,
        ProgrammingExerciseEditableInstructionComponent,
        FaIconComponent,
    ],
    exports: [
        ProgrammingExerciseComponent,
        ProgrammingExerciseInstructionComponent,
        ProgrammingExerciseEditableInstructionComponent,
        ProgrammingExerciseInstructorExerciseStatusComponent,
        ProgrammingExerciseInstructorTriggerBuildButtonComponent,
        ProgrammingExerciseStudentTriggerBuildButtonComponent,
    ],
    providers: [
        ProgrammingExerciseService,
        ProgrammingExerciseTestCaseService,
        ProgrammingExercisePopupService,
        ProgrammingExerciseTaskExtensionWrapper,
        ProgrammingExercisePlantUmlExtensionWrapper,
        ProgrammingExerciseInstructionService,
        ProgrammingExercisePlantUmlService,
        ProgrammingExerciseParticipationService,
    ],
})
export class ArtemisProgrammingExerciseModule {
    constructor(private languageService: JhiLanguageService, private languageHelper: JhiLanguageHelper) {
        this.languageHelper.language.subscribe((languageKey: string) => {
            if (languageKey) {
                this.languageService.changeLanguage(languageKey);
            }
        });
    }
}
