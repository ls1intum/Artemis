import { NgModule } from '@angular/core';
import { ProgrammingExerciseUpdateComponent } from 'app/exercises/programming/manage/update/programming-exercise-update.component';
import { ProgrammingExerciseLifecycleComponent } from 'app/exercises/programming/manage/update/programming-exercise-lifecycle.component';
import { ProgrammingExerciseTestScheduleDatePickerComponent } from 'app/exercises/programming/manage/update/programming-exercise-test-schedule-date-picker.component';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { OwlDateTimeModule } from 'ng-pick-datetime';
import { ProgrammingExercisePlansAndRepositoriesPreviewComponent } from 'app/exercises/programming/manage/update/programming-exercise-plans-and-repositories-preview.component';
import { ArtemisTeamConfigFormGroupModule } from 'app/exercises/shared/team-config-form-group/team-config-form-group.module';
import { ArtemisDifficultyPickerModule } from 'app/exercises/shared/difficulty-picker/difficulty-picker.module';
import { ArtemisPresentationScoreModule } from 'app/exercises/shared/presentation-score/presentation-score.module';
import { ArtemisProgrammingExerciseInstructionsEditorModule } from 'app/exercises/programming/manage/instructions-editor/programming-exercise-instructions-editor.module';
import { ArtemisMarkdownEditorModule } from 'app/shared/markdown-editor/markdown-editor.module';
import { ArtemisCategorySelectorModule } from 'app/shared/category-selector/category-selector.module';
import { StructuredGradingCriterionModule } from 'app/exercises/shared/structured-grading-criterion/structured-grading-criterion.module';
import { ArtemisIncludedInOverallScorePickerModule } from 'app/exercises/shared/included-in-overall-score-picker/included-in-overall-score-picker.module';

@NgModule({
    imports: [
        ArtemisSharedModule,
        ArtemisSharedComponentModule,
        OwlDateTimeModule,
        ArtemisTeamConfigFormGroupModule,
        ArtemisIncludedInOverallScorePickerModule,
        ArtemisDifficultyPickerModule,
        ArtemisPresentationScoreModule,
        ArtemisProgrammingExerciseInstructionsEditorModule,
        ArtemisMarkdownEditorModule,
        ArtemisCategorySelectorModule,
        StructuredGradingCriterionModule,
    ],
    declarations: [
        ProgrammingExerciseLifecycleComponent,
        ProgrammingExerciseTestScheduleDatePickerComponent,
        ProgrammingExercisePlansAndRepositoriesPreviewComponent,
        ProgrammingExerciseUpdateComponent,
    ],
    exports: [ProgrammingExerciseUpdateComponent, ProgrammingExerciseLifecycleComponent],
})
export class ArtemisProgrammingExerciseUpdateModule {}
