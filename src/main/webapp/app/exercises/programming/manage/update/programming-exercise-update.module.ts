import { NgModule } from '@angular/core';
import { ProgrammingExerciseUpdateComponent } from 'app/exercises/programming/manage/update/programming-exercise-update.component';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
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
import { ProgrammingExerciseInformationComponent } from 'app/exercises/programming/manage/update/update-components/programming-exercise-information.component';
import { ProgrammingExerciseDifficultyComponent } from 'app/exercises/programming/manage/update/update-components/programming-exercise-difficulty.component';
import { ProgrammingExerciseLanguageComponent } from 'app/exercises/programming/manage/update/update-components/programming-exercise-language.component';
import { ProgrammingExerciseGradingComponent } from 'app/exercises/programming/manage/update/update-components/programming-exercise-grading.component';
import { ProgrammingExerciseProblemComponent } from 'app/exercises/programming/manage/update/update-components/programming-exercise-problem.component';
import { ExerciseTitleChannelNameModule } from 'app/exercises/shared/exercise-title-channel-name/exercise-title-channel-name.module';
import { ExerciseUpdateNotificationModule } from 'app/exercises/shared/exercise-update-notification/exercise-update-notification.module';
import { ExerciseUpdatePlagiarismModule } from 'app/exercises/shared/plagiarism/exercise-update-plagiarism/exercise-update-plagiarism.module';
import { ProgrammingExerciseCustomAeolusBuildPlanComponent } from 'app/exercises/programming/manage/update/update-components/custom-build-plans/programming-exercise-custom-aeolus-build-plan.component';
import { ProgrammingExerciseCustomBuildPlanComponent } from 'app/exercises/programming/manage/update/update-components/custom-build-plans/programming-exercise-custom-build-plan.component';
import { ProgrammingExerciseDockerImageComponent } from 'app/exercises/programming/manage/update/update-components/custom-build-plans/programming-exercise-docker-image/programming-exercise-docker-image.component';
import { FormsModule } from 'app/forms/forms.module';
import { ProgrammingExerciseBuildPlanCheckoutDirectoriesComponent } from 'app/exercises/programming/shared/build-details/programming-exercise-build-plan-checkout-directories.component';
import { ProgrammingExerciseRepositoryAndBuildPlanDetailsComponent } from 'app/exercises/programming/shared/build-details/programming-exercise-repository-and-build-plan-details.component';
import { ProgrammingExerciseTheiaComponent } from 'app/exercises/programming/manage/update/update-components/theia/programming-exercise-theia.component';
import { MonacoEditorModule } from 'app/shared/monaco-editor/monaco-editor.module';
import {
    ProgrammingExerciseDockerFlagsComponent
} from "app/exercises/programming/manage/update/update-components/custom-build-plans/programming-exercise-docker-flags/programming-exercise-docker-flags.component";

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
        ExerciseTitleChannelNameModule,
        ExerciseUpdateNotificationModule,
        ExerciseUpdatePlagiarismModule,
        FormsModule,
        ProgrammingExerciseBuildPlanCheckoutDirectoriesComponent,
        ProgrammingExerciseRepositoryAndBuildPlanDetailsComponent,
        MonacoEditorModule,
        ProgrammingExerciseTheiaComponent,
        ProgrammingExerciseDockerFlagsComponent,
    ],
    declarations: [
        ProgrammingExerciseUpdateComponent,
        ProgrammingExerciseInformationComponent,
        ProgrammingExerciseDifficultyComponent,
        ProgrammingExerciseCustomAeolusBuildPlanComponent,
        ProgrammingExerciseCustomBuildPlanComponent,
        ProgrammingExerciseDockerImageComponent,
        ProgrammingExerciseLanguageComponent,
        ProgrammingExerciseGradingComponent,
        ProgrammingExerciseProblemComponent,
        AddAuxiliaryRepositoryButtonComponent,
        RemoveAuxiliaryRepositoryButtonComponent,
    ],
    exports: [ProgrammingExerciseUpdateComponent],
})
export class ArtemisProgrammingExerciseUpdateModule {}
