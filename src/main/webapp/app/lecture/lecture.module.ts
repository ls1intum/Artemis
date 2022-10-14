import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';

import { ArtemisSharedModule } from 'app/shared/shared.module';
import { FormDateTimePickerModule } from 'app/shared/date-time-picker/date-time-picker.module';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { ArtemisMarkdownEditorModule } from 'app/shared/markdown-editor/markdown-editor.module';
import { LectureUpdateComponent } from 'app/lecture/lecture-update.component';
import { LectureUpdateWizardComponent } from 'app/lecture/lecture-update-wizard.component';
import { LectureComponent } from 'app/lecture/lecture.component';
import { LectureDetailComponent } from 'app/lecture/lecture-detail.component';
import { LectureAttachmentsComponent } from 'app/lecture/lecture-attachments.component';
import { lectureRoute } from 'app/lecture/lecture.route';
import { ArtemisLectureUnitManagementModule } from 'app/lecture/lecture-unit/lecture-unit-management/lecture-unit-management.module';
import { ArtemisMarkdownModule } from 'app/shared/markdown.module';
import { LectureImportComponent } from 'app/lecture/lecture-import.component';
import { ArtemisLearningGoalsModule } from 'app/course/learning-goals/learning-goal.module';

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
    declarations: [LectureComponent, LectureDetailComponent, LectureImportComponent, LectureUpdateComponent, LectureUpdateWizardComponent, LectureAttachmentsComponent],
})
export class ArtemisLectureModule {}
