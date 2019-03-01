import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';
import { JhiLanguageService } from 'ng-jhipster';
import { JhiLanguageHelper } from 'app/core';

import { ArTEMiSSharedModule } from 'app/shared';
import { ArTEMiSResultModule } from 'app/entities/result';
import { textEditorRoute } from './text-editor.route';
import { TextEditorComponent } from './text-editor.component';
import { TextEditorScoreCardComponent } from './text-editor-score-card/text-editor-score-card.component';
import { TextSharedModule } from 'app/text-shared/text-shared.module';

const ENTITY_STATES = [...textEditorRoute];

@NgModule({
    declarations: [TextEditorComponent, TextEditorScoreCardComponent],
    entryComponents: [TextEditorComponent],
    imports: [ArTEMiSSharedModule, RouterModule.forChild(ENTITY_STATES), ArTEMiSResultModule, TextSharedModule],
    providers: [{ provide: JhiLanguageService, useClass: JhiLanguageService }]
})
export class ArTEMiSTextModule {
    constructor(private languageService: JhiLanguageService, private languageHelper: JhiLanguageHelper) {
        this.languageHelper.language.subscribe((languageKey: string) => {
            if (languageKey !== undefined) {
                this.languageService.changeLanguage(languageKey);
            }
        });
    }
}
