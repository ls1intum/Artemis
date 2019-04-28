import { NgModule } from '@angular/core';
import { JhiLanguageService } from 'ng-jhipster';
import { JhiLanguageHelper } from 'app/core';

import { HeaderExercisePageWithDetailsComponent } from './header-exercise-page-with-details.component';
import { ArTEMiSSharedCommonModule, ArTEMiSSharedModule } from 'app/shared';
import { DifficultyBadgeComponent } from './difficulty-badge.component';
import { MomentModule } from 'ngx-moment';

@NgModule({
    imports: [ArTEMiSSharedCommonModule, MomentModule, ArTEMiSSharedModule],
    declarations: [HeaderExercisePageWithDetailsComponent, DifficultyBadgeComponent],
    entryComponents: [],
    providers: [{ provide: JhiLanguageService, useClass: JhiLanguageService }],
    exports: [HeaderExercisePageWithDetailsComponent, DifficultyBadgeComponent],
})
export class ArTEMiSHeaderExercisePageWithDetailsModule {
    constructor(private languageService: JhiLanguageService, private languageHelper: JhiLanguageHelper) {
        this.languageHelper.language.subscribe((languageKey: string) => {
            if (languageKey !== undefined) {
                this.languageService.changeLanguage(languageKey);
            }
        });
    }
}
