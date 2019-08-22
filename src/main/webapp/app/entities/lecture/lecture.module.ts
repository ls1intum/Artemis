import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';
import { JhiLanguageService } from 'ng-jhipster';
import { JhiLanguageHelper } from 'app/core';

import { ArtemisSharedModule } from 'app/shared';
import {
    LectureComponent,
    LectureDetailComponent,
    LectureUpdateComponent,
    LectureDeletePopupComponent,
    LectureDeleteDialogComponent,
    lectureRoute,
    lecturePopupRoute,
    LectureAttachmentsComponent,
} from './';
import { FormDateTimePickerModule } from 'app/shared/date-time-picker/date-time-picker.module';
import { ArtemisConfirmButtonModule } from 'app/components/confirm-button/confirm-button.module';

const ENTITY_STATES = [...lectureRoute, ...lecturePopupRoute];

@NgModule({
    imports: [ArtemisSharedModule, RouterModule.forChild(ENTITY_STATES), FormDateTimePickerModule, ArtemisConfirmButtonModule],
    declarations: [LectureComponent, LectureDetailComponent, LectureUpdateComponent, LectureDeleteDialogComponent, LectureAttachmentsComponent, LectureDeletePopupComponent],
    entryComponents: [LectureComponent, LectureUpdateComponent, LectureDeleteDialogComponent, LectureDeletePopupComponent, LectureAttachmentsComponent],
    providers: [{ provide: JhiLanguageService, useClass: JhiLanguageService }],
})
export class ArtemisLectureModule {
    constructor(private languageService: JhiLanguageService, private languageHelper: JhiLanguageHelper) {
        this.languageHelper.language.subscribe((languageKey: string) => {
            if (languageKey) {
                this.languageService.changeLanguage(languageKey);
            }
        });
    }
}
