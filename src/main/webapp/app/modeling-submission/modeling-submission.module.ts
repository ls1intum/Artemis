import { ModelingSubmissionComponent } from './modeling-submission.component';
import { RouterModule } from '@angular/router';
import { NgModule } from '@angular/core';
import { ArTEMiSSharedModule } from '../shared';
import { modelingSubmissionRoute } from './modeling-submission.route';
import { ArTEMiSResultModule, ResultComponent } from '../entities/result';
import { JhiLanguageService } from 'ng-jhipster';
import { JhiLanguageHelper } from 'app/core';
import { AceEditorModule } from 'ng2-ace-editor';

const ENTITY_STATES = [...modelingSubmissionRoute];

@NgModule({
    imports: [ArTEMiSSharedModule, RouterModule.forChild(ENTITY_STATES), ArTEMiSResultModule, AceEditorModule],
    declarations: [ModelingSubmissionComponent],
    entryComponents: [ModelingSubmissionComponent, ResultComponent],
    providers: [{ provide: JhiLanguageService, useClass: JhiLanguageService }],
})
export class ArTEMiSModelingSubmissionModule {
    constructor(private languageService: JhiLanguageService, private languageHelper: JhiLanguageHelper) {
        this.languageHelper.language.subscribe((languageKey: string) => {
            if (languageKey !== undefined) {
                this.languageService.changeLanguage(languageKey);
            }
        });
    }
}
