import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';

import { JhiLanguageService } from 'ng-jhipster';
import { JhiLanguageHelper } from 'app/core';
import { ArTEMiSSharedModule } from 'app/shared';

import { LegalRoutingModule } from 'app/legal/legal-routing.module';
import { PrivacyComponent } from 'app/legal/privacy/privacy.component';

@NgModule({
    declarations: [
        PrivacyComponent
    ],
    imports: [
        CommonModule,
        ArTEMiSSharedModule,
        LegalRoutingModule
    ]
})
export class ArTEMiSLegalModule {
    constructor(private languageService: JhiLanguageService, private languageHelper: JhiLanguageHelper) {
        this.languageHelper.language.subscribe((languageKey: string) => {
            if (languageKey !== undefined) {
                this.languageService.changeLanguage(languageKey);
            }
        });
    }
}
