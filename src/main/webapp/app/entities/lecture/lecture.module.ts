import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';

import { ArtemisSharedModule } from 'app/shared';
import {
    LectureAttachmentsComponent,
    LectureComponent,
    LectureDeleteDialogComponent,
    LectureDeletePopupComponent,
    LectureDetailComponent,
    lecturePopupRoute,
    lectureRoute,
    LectureUpdateComponent,
} from './';
import { FormDateTimePickerModule } from 'app/shared/date-time-picker/date-time-picker.module';
import { ArtemisConfirmButtonModule } from 'app/components/confirm-button/confirm-button.module';

const ENTITY_STATES = [...lectureRoute, ...lecturePopupRoute];

@NgModule({
    imports: [ArtemisSharedModule, RouterModule.forChild(ENTITY_STATES), FormDateTimePickerModule, ArtemisConfirmButtonModule],
    declarations: [LectureComponent, LectureDetailComponent, LectureUpdateComponent, LectureDeleteDialogComponent, LectureAttachmentsComponent, LectureDeletePopupComponent],
    entryComponents: [LectureComponent, LectureUpdateComponent, LectureDeleteDialogComponent, LectureDeletePopupComponent, LectureAttachmentsComponent],
})
export class ArtemisLectureModule {}
