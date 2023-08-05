import { NgModule } from '@angular/core';
import { ChecklistCheckComponent } from 'app/shared/components/checklist-check.component';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { FeatureToggleModule } from 'app/shared/feature-toggle/feature-toggle.module';
import { ConfirmAutofocusButtonComponent, ConfirmAutofocusModalComponent } from 'app/shared/components/confirm-autofocus-button.component';
import { HelpIconComponent } from 'app/shared/components/help-icon.component';
import { ButtonComponent } from 'app/shared/components/button.component';
import { CloneRepoButtonComponent } from 'app/shared/components/clone-repo-button/clone-repo-button.component';
import { ExerciseActionButtonComponent } from 'app/shared/components/exercise-action-button.component';
import { ClipboardModule } from '@angular/cdk/clipboard';
import { CourseExamArchiveButtonComponent } from 'app/shared/components/course-exam-archive-button/course-exam-archive-button.component';
import { NotReleasedTagComponent } from 'app/shared/components/not-released-tag.component';
import { HelpIconComponentWithoutTranslationComponent } from 'app/shared/components/help-icon-without-translation.component';
import { OpenCodeEditorButtonComponent } from 'app/shared/components/open-code-editor-button/open-code-editor-button.component';
import { ArtemisCoursesRoutingModule } from 'app/overview/courses-routing.module';
import { CopyIconButtonComponent } from 'app/shared/components/copy-icon-button/copy-icon-button.component';
import { StartPracticeModeButtonComponent } from 'app/shared/components/start-practice-mode-button/start-practice-mode-button.component';
import { DocumentationButtonComponent } from 'app/shared/components/documentation-button/documentation-button.component';
import { ResetRepoButtonComponent } from 'app/shared/components/reset-repo-button/reset-repo-button.component';
import { ExerciseImportComponent } from 'app/exercises/shared/import/exercise-import.component';
import { DifficultyBadgeComponent } from 'app/exercises/shared/exercise-headers/difficulty-badge.component';
import { IncludedInScoreBadgeComponent } from 'app/exercises/shared/exercise-headers/included-in-score-badge.component';
import { ExerciseImportTabsComponent } from 'app/exercises/shared/import/exercise-import-tabs.component';
import { ExerciseImportFromFileComponent } from 'app/exercises/shared/import/from-file/exercise-import-from-file.component';
import { ExerciseImportWrapperComponent } from 'app/exercises/shared/import/exercise-import-wrapper/exercise-import-wrapper.component';

@NgModule({
    imports: [ArtemisSharedModule, ArtemisCoursesRoutingModule, FeatureToggleModule, ClipboardModule],
    declarations: [
        ButtonComponent,
        HelpIconComponent,
        HelpIconComponentWithoutTranslationComponent,
        ConfirmAutofocusButtonComponent,
        ConfirmAutofocusModalComponent,
        ChecklistCheckComponent,
        CloneRepoButtonComponent,
        ResetRepoButtonComponent,
        CopyIconButtonComponent,
        StartPracticeModeButtonComponent,
        OpenCodeEditorButtonComponent,
        ExerciseActionButtonComponent,
        CourseExamArchiveButtonComponent,
        NotReleasedTagComponent,
        DifficultyBadgeComponent,
        IncludedInScoreBadgeComponent,
        DocumentationButtonComponent,
        ExerciseImportComponent,
        ExerciseImportTabsComponent,
        ExerciseImportFromFileComponent,
        ExerciseImportWrapperComponent,
    ],
    exports: [
        ButtonComponent,
        HelpIconComponent,
        HelpIconComponentWithoutTranslationComponent,
        ConfirmAutofocusButtonComponent,
        CloneRepoButtonComponent,
        ChecklistCheckComponent,
        ResetRepoButtonComponent,
        CopyIconButtonComponent,
        StartPracticeModeButtonComponent,
        OpenCodeEditorButtonComponent,
        ExerciseActionButtonComponent,
        CourseExamArchiveButtonComponent,
        NotReleasedTagComponent,
        DifficultyBadgeComponent,
        IncludedInScoreBadgeComponent,
        DocumentationButtonComponent,
        ExerciseImportComponent,
        ExerciseImportTabsComponent,
        ExerciseImportFromFileComponent,
        ExerciseImportWrapperComponent,
    ],
})
export class ArtemisSharedComponentModule {}
