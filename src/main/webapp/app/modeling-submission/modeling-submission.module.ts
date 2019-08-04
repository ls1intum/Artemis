import { ModelingSubmissionComponent } from './modeling-submission.component';
import { RouterModule } from '@angular/router';
import { NgModule } from '@angular/core';
import { ArTEMiSSharedModule } from '../shared';
import { modelingSubmissionRoute } from './modeling-submission.route';
import { ArTEMiSResultModule } from '../entities/result';
import { JhiLanguageService } from 'ng-jhipster';
import { JhiLanguageHelper } from 'app/core';
import { AceEditorModule } from 'ng2-ace-editor';
import { ArTEMiSModelingEditorModule } from 'app/modeling-editor';
import { ModelingAssessmentModule } from 'app/modeling-assessment';
import { ArTEMiSComplaintsModule } from 'app/complaints';

const ENTITY_STATES = [...modelingSubmissionRoute];

// TODO CZ: do we need all these imports?
@NgModule({
    imports: [
        ArTEMiSSharedModule,
        RouterModule.forChild(ENTITY_STATES),
        ArTEMiSResultModule,
        AceEditorModule,
        ArTEMiSModelingEditorModule,
        ModelingAssessmentModule,
        ArTEMiSComplaintsModule,
    ],
    declarations: [ModelingSubmissionComponent],
    providers: [{ provide: JhiLanguageService, useClass: JhiLanguageService }],
})
export class ArTEMiSModelingSubmissionModule {
    constructor(private languageService: JhiLanguageService, private languageHelper: JhiLanguageHelper) {
        this.languageHelper.language.subscribe((languageKey: string) => {
            if (languageKey) {
                this.languageService.changeLanguage(languageKey);
            }
        });
    }
}
