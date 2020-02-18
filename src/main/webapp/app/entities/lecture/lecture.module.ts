import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';

import { ArtemisSharedModule } from 'app/shared/shared.module';
import { FormDateTimePickerModule } from 'app/shared/date-time-picker/date-time-picker.module';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { ArtemisMarkdownEditorModule } from 'app/markdown-editor/markdown-editor.module';
import { LectureUpdateComponent } from 'app/entities/lecture/lecture-update.component';
import { LectureComponent } from 'app/entities/lecture/lecture.component';
import { LectureDetailComponent } from 'app/entities/lecture/lecture-detail.component';
import { LectureAttachmentsComponent } from 'app/entities/lecture/lecture-attachments.component';
import { lectureRoute } from 'app/entities/lecture/lecture.route';

const ENTITY_STATES = [...lectureRoute];

@NgModule({
    imports: [ArtemisSharedModule, RouterModule.forChild(ENTITY_STATES), FormDateTimePickerModule, ArtemisSharedComponentModule, ArtemisMarkdownEditorModule],
    declarations: [LectureComponent, LectureDetailComponent, LectureUpdateComponent, LectureAttachmentsComponent],
    entryComponents: [LectureComponent, LectureUpdateComponent, LectureAttachmentsComponent],
})
export class ArtemisLectureModule {}
