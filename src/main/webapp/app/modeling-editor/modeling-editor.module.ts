import { ModelingEditorComponent } from './modeling-editor.component';
import { NgModule } from '@angular/core';
import { ArTEMiSSharedModule } from '../shared';
import { ArTEMiSResultModule, ResultComponent } from '../entities/result';
import { JhiLanguageService } from 'ng-jhipster';
import { JhiLanguageHelper } from 'app/core';
import { AceEditorModule } from 'ng2-ace-editor';

@NgModule({
    imports: [ArTEMiSSharedModule, ArTEMiSResultModule, AceEditorModule],
    declarations: [ModelingEditorComponent],
    exports: [ModelingEditorComponent],
    providers: [{ provide: JhiLanguageService, useClass: JhiLanguageService }],
})
export class ArTEMiSModelingEditorModule {
    constructor(private languageService: JhiLanguageService, private languageHelper: JhiLanguageHelper) {
        this.languageHelper.language.subscribe((languageKey: string) => {
            if (languageKey !== undefined) {
                this.languageService.changeLanguage(languageKey);
            }
        });
    }
}
