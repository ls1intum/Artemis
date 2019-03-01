import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HighlightedTextAreaComponent } from 'app/text-shared/highlighted-text-area/highlighted-text-area.component';
import { JhiLanguageService } from 'ng-jhipster';
import { JhiLanguageHelper } from 'app/core';

@NgModule({
    declarations: [HighlightedTextAreaComponent],
    imports: [CommonModule],
    exports: [HighlightedTextAreaComponent],
    providers: [{ provide: JhiLanguageService, useClass: JhiLanguageService }]
})
export class TextSharedModule {
    constructor(private languageService: JhiLanguageService, private languageHelper: JhiLanguageHelper) {
        this.languageHelper.language.subscribe((languageKey: string) => {
            if (languageKey !== undefined) {
                this.languageService.changeLanguage(languageKey);
            }
        });
    }
}
