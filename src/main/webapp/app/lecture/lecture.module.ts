import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';

import { ArtemisLearningGoalsModule } from 'app/course/learning-goals/learning-goal.module';
import { LectureAttachmentsComponent } from 'app/lecture/lecture-attachments.component';
import { LectureDetailComponent } from 'app/lecture/lecture-detail.component';
import { LectureImportComponent } from 'app/lecture/lecture-import.component';
import { ArtemisLectureUnitManagementModule } from 'app/lecture/lecture-unit/lecture-unit-management/lecture-unit-management.module';
import { LectureUpdateComponent } from 'app/lecture/lecture-update.component';
import { LectureComponent } from 'app/lecture/lecture.component';
import { lectureRoute } from 'app/lecture/lecture.route';
import { LectureUpdateWizardStepComponent } from 'app/lecture/wizard-mode/lecture-update-wizard-step.component';
import { LectureUpdateWizardComponent } from 'app/lecture/wizard-mode/lecture-update-wizard.component';
import { LectureUpdateWizardAttachmentsComponent } from 'app/lecture/wizard-mode/lecture-wizard-attachments.component';
import { LectureUpdateWizardLearningGoalsComponent } from 'app/lecture/wizard-mode/lecture-wizard-learning-goals.component';
import { LectureUpdateWizardPeriodComponent } from 'app/lecture/wizard-mode/lecture-wizard-period.component';
import { LectureUpdateWizardTitleComponent } from 'app/lecture/wizard-mode/lecture-wizard-title.component';
import { LectureUpdateWizardUnitsComponent } from 'app/lecture/wizard-mode/lecture-wizard-units.component';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { FormDateTimePickerModule } from 'app/shared/date-time-picker/date-time-picker.module';
import { ArtemisMarkdownEditorModule } from 'app/shared/markdown-editor/markdown-editor.module';
import { ArtemisMarkdownModule } from 'app/shared/markdown.module';
import { ArtemisSharedModule } from 'app/shared/shared.module';

const ENTITY_STATES = [...lectureRoute];

@NgModule({
    imports: [
        ArtemisSharedModule,
        RouterModule.forChild(ENTITY_STATES),
        ArtemisLectureUnitManagementModule,
        FormDateTimePickerModule,
        ArtemisSharedComponentModule,
        ArtemisMarkdownModule,
        ArtemisMarkdownEditorModule,
        ArtemisLearningGoalsModule,
    ],
    declarations: [
        LectureComponent,
        LectureDetailComponent,
        LectureImportComponent,
        LectureUpdateComponent,
        LectureUpdateWizardComponent,
        LectureAttachmentsComponent,
        LectureUpdateWizardTitleComponent,
        LectureUpdateWizardPeriodComponent,
        LectureUpdateWizardAttachmentsComponent,
        LectureUpdateWizardUnitsComponent,
        LectureUpdateWizardLearningGoalsComponent,
        LectureUpdateWizardStepComponent,
    ],
})
export class ArtemisLectureModule {}
