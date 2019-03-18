import { NgModule, CUSTOM_ELEMENTS_SCHEMA } from '@angular/core';
import { RouterModule } from '@angular/router';
import { JhiLanguageService } from 'ng-jhipster';
import { JhiLanguageHelper } from 'app/core';

import { ArTEMiSSharedModule } from 'app/shared';
import {
    LectureComponent,
    LectureDetailComponent,
    LectureUpdateComponent,
    LectureDeletePopupComponent,
    LectureDeleteDialogComponent,
    lectureRoute,
    lecturePopupRoute
} from './';

const ENTITY_STATES = [...lectureRoute, ...lecturePopupRoute];

@NgModule({
    imports: [ArTEMiSSharedModule, RouterModule.forChild(ENTITY_STATES)],
    declarations: [
        LectureComponent,
        LectureDetailComponent,
        LectureUpdateComponent,
        LectureDeleteDialogComponent,
        LectureDeletePopupComponent
    ],
    entryComponents: [LectureComponent, LectureUpdateComponent, LectureDeleteDialogComponent, LectureDeletePopupComponent],
    providers: [{ provide: JhiLanguageService, useClass: JhiLanguageService }],
    schemas: [CUSTOM_ELEMENTS_SCHEMA]
})
export class ArtemisLectureModule {
    constructor(private languageService: JhiLanguageService, private languageHelper: JhiLanguageHelper) {
        this.languageHelper.language.subscribe((languageKey: string) => {
            if (languageKey !== undefined) {
                this.languageService.changeLanguage(languageKey);
            }
        });
    }
}
