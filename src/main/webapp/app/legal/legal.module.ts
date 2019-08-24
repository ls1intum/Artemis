import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';

import { JhiLanguageService } from 'ng-jhipster';
import { JhiLanguageHelper } from 'app/core';
import { ArtemisSharedModule } from 'app/shared';

import { LegalRoutingModule } from 'app/legal/legal-routing.module';
import { PrivacyComponent } from 'app/legal/privacy/privacy.component';

@NgModule({
    declarations: [PrivacyComponent],
    imports: [CommonModule, ArtemisSharedModule, LegalRoutingModule],
})
export class ArtemisLegalModule {
    constructor(private languageService: JhiLanguageService, private languageHelper: JhiLanguageHelper) {
        this.languageHelper.language.subscribe((languageKey: string) => {
            if (languageKey) {
                this.languageService.changeLanguage(languageKey);
            }
        });
    }
}
