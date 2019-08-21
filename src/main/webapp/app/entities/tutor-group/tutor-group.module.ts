import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';
import { JhiLanguageService } from 'ng-jhipster';
import { JhiLanguageHelper } from 'app/core';

import { ArtemisSharedModule } from 'app/shared';
import {
    TutorGroupComponent,
    TutorGroupDetailComponent,
    TutorGroupUpdateComponent,
    TutorGroupDeletePopupComponent,
    TutorGroupDeleteDialogComponent,
    tutorGroupRoute,
    tutorGroupPopupRoute,
} from './';

const ENTITY_STATES = [...tutorGroupRoute, ...tutorGroupPopupRoute];

@NgModule({
    imports: [ArtemisSharedModule, RouterModule.forChild(ENTITY_STATES)],
    declarations: [TutorGroupComponent, TutorGroupDetailComponent, TutorGroupUpdateComponent, TutorGroupDeleteDialogComponent, TutorGroupDeletePopupComponent],
    entryComponents: [TutorGroupComponent, TutorGroupUpdateComponent, TutorGroupDeleteDialogComponent, TutorGroupDeletePopupComponent],
    providers: [{ provide: JhiLanguageService, useClass: JhiLanguageService }],
})
export class ArtemisTutorGroupModule {
    constructor(private languageService: JhiLanguageService, private languageHelper: JhiLanguageHelper) {
        this.languageHelper.language.subscribe((languageKey: string) => {
            if (languageKey) {
                this.languageService.changeLanguage(languageKey);
            }
        });
    }
}
