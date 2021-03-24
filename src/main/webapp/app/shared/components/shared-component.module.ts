import { NgModule } from '@angular/core';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { FeatureToggleModule } from 'app/shared/feature-toggle/feature-toggle.module';
import { ConfirmAutofocusButtonComponent, ConfirmAutofocusModalComponent } from 'app/shared/components/confirm-autofocus-button.component';
import { HelpIconComponent } from 'app/shared/components/help-icon.component';
import { ButtonComponent } from 'app/shared/components/button.component';
import { CloneRepoButtonComponent } from 'app/shared/components/clone-repo-button/clone-repo-button.component';
import { ExerciseActionButtonComponent } from 'app/shared/components/exercise-action-button.component';
import { ClipboardModule } from 'ngx-clipboard';
import { CourseExamArchiveButtonComponent } from 'app/shared/components/course-exam-archive-button/course-exam-archive-button.component';

@NgModule({
    imports: [ArtemisSharedModule, FeatureToggleModule, ClipboardModule],
    entryComponents: [ConfirmAutofocusModalComponent],
    declarations: [
        ButtonComponent,
        HelpIconComponent,
        ConfirmAutofocusButtonComponent,
        ConfirmAutofocusModalComponent,
        CloneRepoButtonComponent,
        ExerciseActionButtonComponent,
        CourseExamArchiveButtonComponent,
    ],
    exports: [ButtonComponent, HelpIconComponent, ConfirmAutofocusButtonComponent, CloneRepoButtonComponent, ExerciseActionButtonComponent, CourseExamArchiveButtonComponent],
})
export class ArtemisSharedComponentModule {}
