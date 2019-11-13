import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';

import { ArtemisSharedModule } from 'app/shared';
import { LectureAttachmentsComponent, LectureComponent, LectureDetailComponent, lectureRoute, LectureUpdateComponent } from './';
import { FormDateTimePickerModule } from 'app/shared/date-time-picker/date-time-picker.module';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { ArtemisMarkdownEditorModule } from 'app/markdown-editor';

const ENTITY_STATES = [...lectureRoute];

@NgModule({
    imports: [ArtemisSharedModule, RouterModule.forChild(ENTITY_STATES), FormDateTimePickerModule, ArtemisSharedComponentModule, ArtemisMarkdownEditorModule],
    declarations: [LectureComponent, LectureDetailComponent, LectureUpdateComponent, LectureAttachmentsComponent],
    entryComponents: [LectureComponent, LectureUpdateComponent, LectureAttachmentsComponent],
})
export class ArtemisLectureModule {}
