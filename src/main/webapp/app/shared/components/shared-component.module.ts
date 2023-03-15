import { ClipboardModule } from '@angular/cdk/clipboard';
import { NgModule } from '@angular/core';

import { ExerciseImportComponent } from 'app/exercises/shared/import/exercise-import.component';
import { ArtemisCoursesRoutingModule } from 'app/overview/courses-routing.module';
import { ButtonComponent } from 'app/shared/components/button.component';
import { CloneRepoButtonComponent } from 'app/shared/components/clone-repo-button/clone-repo-button.component';
import { ConfirmAutofocusButtonComponent, ConfirmAutofocusModalComponent } from 'app/shared/components/confirm-autofocus-button.component';
import { CopyIconButtonComponent } from 'app/shared/components/copy-icon-button/copy-icon-button.component';
import { CourseExamArchiveButtonComponent } from 'app/shared/components/course-exam-archive-button/course-exam-archive-button.component';
import { DocumentationButtonComponent } from 'app/shared/components/documentation-button/documentation-button.component';
import { ExerciseActionButtonComponent } from 'app/shared/components/exercise-action-button.component';
import { HelpIconComponentWithoutTranslationComponent } from 'app/shared/components/help-icon-without-translation.component';
import { HelpIconComponent } from 'app/shared/components/help-icon.component';
import { NotReleasedTagComponent } from 'app/shared/components/not-released-tag.component';
import { OpenCodeEditorButtonComponent } from 'app/shared/components/open-code-editor-button/open-code-editor-button.component';
import { ResetRepoButtonComponent } from 'app/shared/components/reset-repo-button/reset-repo-button.component';
import { StartPracticeModeButtonComponent } from 'app/shared/components/start-practice-mode-button/start-practice-mode-button.component';
import { FeatureToggleModule } from 'app/shared/feature-toggle/feature-toggle.module';
import { ArtemisSharedModule } from 'app/shared/shared.module';

@NgModule({
    imports: [ArtemisSharedModule, ArtemisCoursesRoutingModule, FeatureToggleModule, ClipboardModule],
    declarations: [
        ButtonComponent,
        HelpIconComponent,
        HelpIconComponentWithoutTranslationComponent,
        ConfirmAutofocusButtonComponent,
        ConfirmAutofocusModalComponent,
        CloneRepoButtonComponent,
        ResetRepoButtonComponent,
        CopyIconButtonComponent,
        StartPracticeModeButtonComponent,
        OpenCodeEditorButtonComponent,
        ExerciseActionButtonComponent,
        CourseExamArchiveButtonComponent,
        NotReleasedTagComponent,
        DocumentationButtonComponent,
        ExerciseImportComponent,
    ],
    exports: [
        ButtonComponent,
        HelpIconComponent,
        HelpIconComponentWithoutTranslationComponent,
        ConfirmAutofocusButtonComponent,
        CloneRepoButtonComponent,
        ResetRepoButtonComponent,
        CopyIconButtonComponent,
        StartPracticeModeButtonComponent,
        OpenCodeEditorButtonComponent,
        ExerciseActionButtonComponent,
        CourseExamArchiveButtonComponent,
        NotReleasedTagComponent,
        DocumentationButtonComponent,
        ExerciseImportComponent,
    ],
})
export class ArtemisSharedComponentModule {}
