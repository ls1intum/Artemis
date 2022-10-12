import { NgModule } from '@angular/core';
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

@NgModule({
    imports: [ArtemisSharedModule, ArtemisCoursesRoutingModule, FeatureToggleModule, ClipboardModule],
    declarations: [
        ButtonComponent,
        HelpIconComponent,
        HelpIconComponentWithoutTranslationComponent,
        ConfirmAutofocusButtonComponent,
        ConfirmAutofocusModalComponent,
        CloneRepoButtonComponent,
        OpenCodeEditorButtonComponent,
        ExerciseActionButtonComponent,
        CourseExamArchiveButtonComponent,
        NotReleasedTagComponent,
    ],
    exports: [
        ButtonComponent,
        HelpIconComponent,
        HelpIconComponentWithoutTranslationComponent,
        ConfirmAutofocusButtonComponent,
        CloneRepoButtonComponent,
        OpenCodeEditorButtonComponent,
        ExerciseActionButtonComponent,
        CourseExamArchiveButtonComponent,
        NotReleasedTagComponent,
    ],
})
export class ArtemisSharedComponentModule {}
