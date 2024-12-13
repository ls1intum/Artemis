import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { FormDateTimePickerModule } from 'app/shared/date-time-picker/date-time-picker.module';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { ArtemisMarkdownEditorModule } from 'app/shared/markdown-editor/markdown-editor.module';
import { LectureUpdateComponent } from 'app/lecture/lecture-update.component';
import { LectureComponent } from 'app/lecture/lecture.component';
import { LectureDetailComponent } from 'app/lecture/lecture-detail.component';
import { LectureAttachmentsComponent } from 'app/lecture/lecture-attachments.component';
import { lectureRoute } from 'app/lecture/lecture.route';
import { ArtemisLectureUnitManagementModule } from 'app/lecture/lecture-unit/lecture-unit-management/lecture-unit-management.module';
import { ArtemisMarkdownModule } from 'app/shared/markdown.module';
import { LectureImportComponent } from 'app/lecture/lecture-import.component';
import { ArtemisCompetenciesModule } from 'app/course/competencies/competency.module';
import { LectureUpdateWizardPeriodComponent } from 'app/lecture/wizard-mode/lecture-wizard-period.component';
import { LectureUpdateWizardUnitsComponent } from 'app/lecture/wizard-mode/lecture-wizard-units.component';
import { TitleChannelNameModule } from 'app/shared/form/title-channel-name/title-channel-name.module';
import { LectureTitleChannelNameComponent } from 'app/lecture/lecture-title-channel-name.component';
import { DetailModule } from 'app/detail-overview-list/detail.module';
import { CompetencyFormComponent } from 'app/course/competencies/forms/competency/competency-form.component';
import { FormsModule } from 'app/forms/forms.module';

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
        DetailModule,
        CompetencyFormComponent,
        FormsModule,
    ],
    declarations: [
        LectureComponent,
        LectureDetailComponent,
        LectureImportComponent,
        LectureUpdateComponent,
        LectureAttachmentsComponent,
        LectureUpdateWizardPeriodComponent,
        LectureUpdateWizardUnitsComponent,
        LectureTitleChannelNameComponent,
    ],
})
export class ArtemisLectureModule {}
