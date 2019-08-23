import { ModelingSubmissionComponent } from './modeling-submission.component';
import { RouterModule } from '@angular/router';
import { NgModule } from '@angular/core';
import { ArtemisSharedModule } from '../shared';
import { modelingSubmissionRoute } from './modeling-submission.route';
import { ArtemisResultModule } from '../entities/result';
import { JhiLanguageService } from 'ng-jhipster';
import { JhiLanguageHelper } from 'app/core';
import { AceEditorModule } from 'ng2-ace-editor';
import { ArtemisModelingEditorModule } from 'app/modeling-editor';
import { ModelingAssessmentModule } from 'app/modeling-assessment';
import { ArtemisComplaintsModule } from 'app/complaints';

const ENTITY_STATES = [...modelingSubmissionRoute];

// TODO CZ: do we need all these imports?
@NgModule({
    imports: [
        ArtemisSharedModule,
        RouterModule.forChild(ENTITY_STATES),
        ArtemisResultModule,
        AceEditorModule,
        ArtemisModelingEditorModule,
        ModelingAssessmentModule,
        ArtemisComplaintsModule,
    ],
    declarations: [ModelingSubmissionComponent],
    providers: [{ provide: JhiLanguageService, useClass: JhiLanguageService }],
})
export class ArtemisModelingSubmissionModule {
    constructor(private languageService: JhiLanguageService, private languageHelper: JhiLanguageHelper) {
        this.languageHelper.language.subscribe((languageKey: string) => {
            if (languageKey) {
                this.languageService.changeLanguage(languageKey);
            }
        });
    }
}
