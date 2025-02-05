import { NgModule } from '@angular/core';
import { ProgrammingExerciseUpdateComponent } from 'app/exercises/programming/manage/update/programming-exercise-update.component';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { ArtemisTeamConfigFormGroupModule } from 'app/exercises/shared/team-config-form-group/team-config-form-group.module';
import { ArtemisDifficultyPickerModule } from 'app/exercises/shared/difficulty-picker/difficulty-picker.module';
import { ArtemisPresentationScoreModule } from 'app/exercises/shared/presentation-score/presentation-score.module';
import { ArtemisProgrammingExerciseInstructionsEditorModule } from 'app/exercises/programming/manage/instructions-editor/programming-exercise-instructions-editor.module';
import { ArtemisMarkdownEditorModule } from 'app/shared/markdown-editor/markdown-editor.module';
import { StructuredGradingCriterionModule } from 'app/exercises/shared/structured-grading-criterion/structured-grading-criterion.module';
import { ArtemisIncludedInOverallScorePickerModule } from 'app/exercises/shared/included-in-overall-score-picker/included-in-overall-score-picker.module';
import { ArtemisProgrammingExerciseLifecycleModule } from 'app/exercises/programming/shared/lifecycle/programming-exercise-lifecycle.module';
import { AddAuxiliaryRepositoryButtonComponent } from 'app/exercises/programming/manage/update/add-auxiliary-repository-button.component';
import { NgxDatatableModule } from '@siemens/ngx-datatable';
import { RemoveAuxiliaryRepositoryButtonComponent } from 'app/exercises/programming/manage/update/remove-auxiliary-repository-button.component';
import { SubmissionPolicyUpdateModule } from 'app/exercises/shared/submission-policy/submission-policy-update.module';
import { ProgrammingExerciseInformationComponent } from 'app/exercises/programming/manage/update/update-components/information/programming-exercise-information.component';
import { ProgrammingExerciseModeComponent } from 'app/exercises/programming/manage/update/update-components/mode/programming-exercise-mode.component';
import { ProgrammingExerciseLanguageComponent } from 'app/exercises/programming/manage/update/update-components/language/programming-exercise-language.component';
import { ProgrammingExerciseGradingComponent } from 'app/exercises/programming/manage/update/update-components/grading/programming-exercise-grading.component';
import { ProgrammingExerciseProblemComponent } from 'app/exercises/programming/manage/update/update-components/problem/programming-exercise-problem.component';
import { ExerciseTitleChannelNameModule } from 'app/exercises/shared/exercise-title-channel-name/exercise-title-channel-name.module';
import { ExerciseUpdateNotificationModule } from 'app/exercises/shared/exercise-update-notification/exercise-update-notification.module';
import { ExerciseUpdatePlagiarismModule } from 'app/exercises/shared/plagiarism/exercise-update-plagiarism/exercise-update-plagiarism.module';
import { ProgrammingExerciseCustomAeolusBuildPlanComponent } from 'app/exercises/programming/manage/update/update-components/custom-build-plans/programming-exercise-custom-aeolus-build-plan.component';
import { ProgrammingExerciseCustomBuildPlanComponent } from 'app/exercises/programming/manage/update/update-components/custom-build-plans/programming-exercise-custom-build-plan.component';
import { ProgrammingExerciseBuildConfigurationComponent } from 'app/exercises/programming/manage/update/update-components/custom-build-plans/programming-exercise-build-configuration/programming-exercise-build-configuration.component';
import { ArtemisFormsModule } from 'app/forms/artemis-forms.module';
import { ProgrammingExerciseBuildPlanCheckoutDirectoriesComponent } from 'app/exercises/programming/shared/build-details/programming-exercise-build-plan-checkout-directories.component';
import { ProgrammingExerciseRepositoryAndBuildPlanDetailsComponent } from 'app/exercises/programming/shared/build-details/programming-exercise-repository-and-build-plan-details.component';
import { ProgrammingExerciseTheiaComponent } from 'app/exercises/programming/manage/update/update-components/theia/programming-exercise-theia.component';
import { MonacoEditorComponent } from 'app/shared/monaco-editor/monaco-editor.component';
import { ProgrammingExerciseEditCheckoutDirectoriesComponent } from 'app/exercises/programming/shared/build-details/programming-exercise-edit-checkout-directories/programming-exercise-edit-checkout-directories.component';
import { ProgrammingExerciseDifficultyComponent } from 'app/exercises/programming/manage/update/update-components/difficulty/programming-exercise-difficulty.component';
import { SwitchEditModeButtonComponent } from 'app/exercises/programming/manage/update/switch-edit-mode-button/switch-edit-mode-button.component';
import { CustomNotIncludedInValidatorDirective } from 'app/shared/validators/custom-not-included-in-validator.directive';

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
        StructuredGradingCriterionModule,
        ArtemisProgrammingExerciseLifecycleModule,
        NgxDatatableModule,
        SubmissionPolicyUpdateModule,
        ExerciseTitleChannelNameModule,
        ExerciseUpdateNotificationModule,
        ExerciseUpdatePlagiarismModule,
        ArtemisFormsModule,
        ProgrammingExerciseBuildPlanCheckoutDirectoriesComponent,
        ProgrammingExerciseRepositoryAndBuildPlanDetailsComponent,
        MonacoEditorComponent,
        ProgrammingExerciseTheiaComponent,
        ProgrammingExerciseEditCheckoutDirectoriesComponent,
        ProgrammingExerciseDifficultyComponent,
        SwitchEditModeButtonComponent,
        CustomNotIncludedInValidatorDirective,
        ProgrammingExerciseUpdateComponent,
        ProgrammingExerciseInformationComponent,
        ProgrammingExerciseModeComponent,
        ProgrammingExerciseCustomAeolusBuildPlanComponent,
        ProgrammingExerciseCustomBuildPlanComponent,
        ProgrammingExerciseBuildConfigurationComponent,
        ProgrammingExerciseLanguageComponent,
        ProgrammingExerciseGradingComponent,
        ProgrammingExerciseProblemComponent,
        AddAuxiliaryRepositoryButtonComponent,
        RemoveAuxiliaryRepositoryButtonComponent,
    ],
    exports: [ProgrammingExerciseUpdateComponent],
})
export class ArtemisProgrammingExerciseUpdateModule {}
