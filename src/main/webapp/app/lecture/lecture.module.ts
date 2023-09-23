import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';

import { ArtemisSharedModule } from 'app/shared/shared.module';
import { FormDateTimePickerModule } from 'app/shared/date-time-picker/date-time-picker.module';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { ArtemisMarkdownEditorModule } from 'app/shared/markdown-editor/markdown-editor.module';
import { LectureUpdateComponent } from 'app/lecture/lecture-update.component';
import { LectureUpdateWizardComponent } from 'app/lecture/wizard-mode/lecture-update-wizard.component';
import { LectureComponent } from 'app/lecture/lecture.component';
import { LectureDetailComponent } from 'app/lecture/lecture-detail.component';
import { LectureAttachmentsComponent } from 'app/lecture/lecture-attachments.component';
import { lectureRoute } from 'app/lecture/lecture.route';
import { ArtemisLectureUnitManagementModule } from 'app/lecture/lecture-unit/lecture-unit-management/lecture-unit-management.module';
import { ArtemisMarkdownModule } from 'app/shared/markdown.module';
import { LectureImportComponent } from 'app/lecture/lecture-import.component';
import { ArtemisCompetenciesModule } from 'app/course/competencies/competency.module';
import { LectureUpdateWizardTitleComponent } from 'app/lecture/wizard-mode/lecture-wizard-title.component';
import { LectureUpdateWizardPeriodComponent } from 'app/lecture/wizard-mode/lecture-wizard-period.component';
import { LectureUpdateWizardAttachmentsComponent } from 'app/lecture/wizard-mode/lecture-wizard-attachments.component';
import { LectureUpdateWizardUnitsComponent } from 'app/lecture/wizard-mode/lecture-wizard-units.component';
import { LectureUpdateWizardCompetenciesComponent } from 'app/lecture/wizard-mode/lecture-wizard-competencies.component';
import { LectureUpdateWizardStepComponent } from 'app/lecture/wizard-mode/lecture-update-wizard-step.component';
import { TitleChannelNameModule } from 'app/shared/form/title-channel-name/title-channel-name.module';
import { LectureTitleChannelNameComponent } from 'app/lecture/lecture-title-channel-name.component';

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
        ArtemisCompetenciesModule,
        TitleChannelNameModule,
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
        LectureUpdateWizardCompetenciesComponent,
        LectureUpdateWizardStepComponent,
        LectureTitleChannelNameComponent,
    ],
})
export class ArtemisLectureModule {}
