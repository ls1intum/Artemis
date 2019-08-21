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
import { ArtemisProgrammingExerciseInstructionsRenderModule } from 'app/entities/programming-exercise/instructions/instructions-render';
import { ArtemisProgrammingExerciseInstructionsEditorModule } from 'app/entities/programming-exercise/instructions/instructions-editor';

const ENTITY_STATES = [...programmingExerciseRoute, ...programmingExercisePopupRoute];

@NgModule({
    imports: [
        ArtemisSharedModule,
        RouterModule.forChild(ENTITY_STATES),
        SortByModule,
        FormDateTimePickerModule,
        ArtemisCategorySelectorModule,
        ArtemisDifficultyPickerModule,
        ArtemisProgrammingExerciseInstructionsEditorModule,
        ArtemisResultModule,
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
        ProgrammingExerciseInstructorStatusComponent,
        ProgrammingExerciseInstructorExerciseStatusComponent,
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
        ProgrammingExerciseInstructorExerciseStatusComponent,
        FaIconComponent,
    ],
    exports: [
        ProgrammingExerciseComponent,
        ProgrammingExerciseInstructorExerciseStatusComponent,
        ProgrammingExerciseInstructorTriggerBuildButtonComponent,
        ProgrammingExerciseStudentTriggerBuildButtonComponent,
        ArtemisProgrammingExerciseInstructionsEditorModule,
    ],
    providers: [ProgrammingExerciseService, ProgrammingExerciseTestCaseService, ProgrammingExercisePopupService, ProgrammingExerciseParticipationService],
    schemas: [CUSTOM_ELEMENTS_SCHEMA],
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
