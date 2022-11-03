import { NgModule } from '@angular/core';
import { ProgrammingExerciseUpdateComponent } from 'app/exercises/programming/manage/update/programming-exercise-update.component';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { ProgrammingExercisePlansAndRepositoriesPreviewComponent } from 'app/exercises/programming/manage/update/programming-exercise-plans-and-repositories-preview.component';
import { ArtemisTeamConfigFormGroupModule } from 'app/exercises/shared/team-config-form-group/team-config-form-group.module';
import { ArtemisDifficultyPickerModule } from 'app/exercises/shared/difficulty-picker/difficulty-picker.module';
import { ArtemisPresentationScoreModule } from 'app/exercises/shared/presentation-score/presentation-score.module';
import { ArtemisProgrammingExerciseInstructionsEditorModule } from 'app/exercises/programming/manage/instructions-editor/programming-exercise-instructions-editor.module';
import { ArtemisMarkdownEditorModule } from 'app/shared/markdown-editor/markdown-editor.module';
import { ArtemisCategorySelectorModule } from 'app/shared/category-selector/category-selector.module';
import { StructuredGradingCriterionModule } from 'app/exercises/shared/structured-grading-criterion/structured-grading-criterion.module';
import { ArtemisIncludedInOverallScorePickerModule } from 'app/exercises/shared/included-in-overall-score-picker/included-in-overall-score-picker.module';
import { ArtemisProgrammingExerciseLifecycleModule } from 'app/exercises/programming/shared/lifecycle/programming-exercise-lifecycle.module';
import { AddAuxiliaryRepositoryButtonComponent } from 'app/exercises/programming/manage/update/add-auxiliary-repository-button.component';
import { NgxDatatableModule } from '@flaviosantoro92/ngx-datatable';
import { ArtemisTableModule } from 'app/shared/table/table.module';
import { RemoveAuxiliaryRepositoryButtonComponent } from 'app/exercises/programming/manage/update/remove-auxiliary-repository-button.component';
import { SubmissionPolicyUpdateModule } from 'app/exercises/shared/submission-policy/submission-policy-update.module';
import { ArtemisModePickerModule } from 'app/exercises/shared/mode-picker/mode-picker.module';
import { ProgrammingExerciseUpdateWizardComponent } from 'app/exercises/programming/manage/update/wizard-mode/programming-exercise-update-wizard.component';
import { ProgrammingExerciseUpdateWizardStepComponent } from 'app/exercises/programming/manage/update/wizard-mode/programming-exercise-update-wizard-step.component';
import { ProgrammingExerciseUpdateWizardInformationComponent } from 'app/exercises/programming/manage/update/wizard-mode/programming-exercise-update-wizard-information.component';

@NgModule({
    imports: [
        ArtemisSharedModule,
        ArtemisSharedComponentModule,
        ArtemisTeamConfigFormGroupModule,
        ArtemisIncludedInOverallScorePickerModule,
        ArtemisDifficultyPickerModule,
        ArtemisPresentationScoreModule,
        ArtemisProgrammingExerciseInstructionsEditorModule,
        ArtemisMarkdownEditorModule,
        ArtemisCategorySelectorModule,
        StructuredGradingCriterionModule,
        ArtemisProgrammingExerciseLifecycleModule,
        NgxDatatableModule,
        ArtemisTableModule,
        SubmissionPolicyUpdateModule,
        ArtemisModePickerModule,
    ],
    declarations: [
        ProgrammingExerciseUpdateComponent,
        ProgrammingExerciseUpdateWizardComponent,
        ProgrammingExerciseUpdateWizardStepComponent,
        ProgrammingExerciseUpdateWizardInformationComponent,
        ProgrammingExercisePlansAndRepositoriesPreviewComponent,
        AddAuxiliaryRepositoryButtonComponent,
        RemoveAuxiliaryRepositoryButtonComponent,
    ],
    exports: [ProgrammingExerciseUpdateComponent, ProgrammingExerciseUpdateWizardComponent, ProgrammingExercisePlansAndRepositoriesPreviewComponent],
})
export class ArtemisProgrammingExerciseUpdateModule {}
