import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';

import { ArtemisSharedModule } from 'app/shared/shared.module';
import { FormDateTimePickerModule } from 'app/shared/date-time-picker/date-time-picker.module';
import { ArtemisCategorySelectorModule } from 'app/shared/category-selector/category-selector.module';
import { ArtemisDifficultyPickerModule } from 'app/exercises/shared/difficulty-picker/difficulty-picker.module';
import { ArtemisPresentationScoreModule } from 'app/exercises/shared/presentation-score/presentation-score.module';
import { ArtemisAssessmentSharedModule } from 'app/assessment/assessment-shared.module';
import { SortByModule } from 'app/shared/pipes/sort-by.module';
import { ArtemisMarkdownEditorModule } from 'app/shared/markdown-editor/markdown-editor.module';
import { FileUploadExerciseService } from 'app/exercises/file-upload/manage/file-upload-exercise/file-upload-exercise.service';
import { fileUploadExerciseRoute } from 'app/exercises/file-upload/manage/file-upload-exercise/file-upload-exercise.route';
import { FileUploadExerciseComponent } from 'app/exercises/file-upload/manage/file-upload-exercise/file-upload-exercise.component';
import { FileUploadExerciseUpdateComponent } from 'app/exercises/file-upload/manage/file-upload-exercise/file-upload-exercise-update.component';
import { FileUploadExerciseDetailComponent } from 'app/exercises/file-upload/manage/file-upload-exercise/file-upload-exercise-detail.component';
import { ArtemisTeamConfigFormGroupModule } from 'app/exercises/shared/team-config-form-group/team-config-form-group.module';
import { StructuredGradingCriterionModule } from 'app/exercises/shared/structured-grading-criterion/structured-grading-criterion.module';
import { ArtemisSlideToggleModule } from 'app/exercises/shared/slide-toggle/slide-toggle.module';

const ENTITY_STATES = [...fileUploadExerciseRoute];

@NgModule({
    imports: [
        ArtemisSharedModule,
        RouterModule.forChild(ENTITY_STATES),
        SortByModule,
        FormDateTimePickerModule,
        ArtemisCategorySelectorModule,
        ArtemisDifficultyPickerModule,
        ArtemisMarkdownEditorModule,
        ArtemisPresentationScoreModule,
        ArtemisAssessmentSharedModule,
        ArtemisTeamConfigFormGroupModule,
        StructuredGradingCriterionModule,
        ArtemisSlideToggleModule,
    ],
    declarations: [FileUploadExerciseComponent, FileUploadExerciseDetailComponent, FileUploadExerciseUpdateComponent],
    exports: [FileUploadExerciseComponent],
    providers: [FileUploadExerciseService],
})
export class ArtemisFileUploadExerciseModule {}
