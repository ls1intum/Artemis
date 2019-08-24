import { NgModule } from '@angular/core';
import { JhiLanguageService } from 'ng-jhipster';
import { JhiLanguageHelper } from 'app/core';

import { HeaderExercisePageWithDetailsComponent } from './header-exercise-page-with-details.component';
import { ArtemisSharedCommonModule, ArtemisSharedModule } from 'app/shared';
import { DifficultyBadgeComponent } from './difficulty-badge.component';
import { MomentModule } from 'ngx-moment';

@NgModule({
    imports: [ArtemisSharedCommonModule, MomentModule, ArtemisSharedModule],
    declarations: [HeaderExercisePageWithDetailsComponent, DifficultyBadgeComponent],
    entryComponents: [],
    providers: [{ provide: JhiLanguageService, useClass: JhiLanguageService }],
    exports: [HeaderExercisePageWithDetailsComponent, DifficultyBadgeComponent],
})
export class ArtemisHeaderExercisePageWithDetailsModule {
    constructor(private languageService: JhiLanguageService, private languageHelper: JhiLanguageHelper) {
        this.languageHelper.language.subscribe((languageKey: string) => {
            if (languageKey) {
                this.languageService.changeLanguage(languageKey);
            }
        });
    }
}
