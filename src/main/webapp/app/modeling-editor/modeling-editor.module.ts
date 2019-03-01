import { ModelingEditorComponent } from './modeling-editor.component';
import { RouterModule } from '@angular/router';
import { NgModule } from '@angular/core';
import { ArTEMiSSharedModule } from '../shared';
import { modelingEditorRoute } from './modeling-editor.route';
import { ArTEMiSResultModule, ResultComponent } from '../entities/result';
import { ModelingEditorService } from './modeling-editor.service';
import { JhiLanguageService } from 'ng-jhipster';
import { JhiLanguageHelper } from 'app/core';

const ENTITY_STATES = [
    ...modelingEditorRoute,
];

@NgModule({
    imports: [
        ArTEMiSSharedModule,
        RouterModule.forChild(ENTITY_STATES),
        ArTEMiSResultModule
    ],
    declarations: [
        ModelingEditorComponent
    ],
    entryComponents: [
        ModelingEditorComponent,
        ResultComponent
    ],
    providers: [
        ModelingEditorService,
        { provide: JhiLanguageService, useClass: JhiLanguageService }
    ]
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
