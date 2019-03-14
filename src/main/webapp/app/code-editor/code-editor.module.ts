import { CUSTOM_ELEMENTS_SCHEMA, NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';
import { JhiLanguageService } from 'ng-jhipster';
import { JhiLanguageHelper } from 'app/core';

import { ArTEMiSSharedModule } from '../shared';
import { codeEditorRoute } from './code-editor.route';
import { JhiAlertService } from 'ng-jhipster';
import { CodeEditorComponent } from './code-editor.component';
import { CodeEditorService } from './code-editor.service';
import { RepositoryService } from '../entities/repository';
import { ArTEMiSResultModule, ResultComponent, ResultService } from '../entities/result';
import { HomeComponent } from '../home';
import { ParticipationService } from '../entities/participation';
import { MomentModule } from 'angular2-moment';
import { JhiMainComponent } from '../layouts';
import { CodeEditorAceComponent } from './ace/code-editor-ace.component';
import { AceEditorModule } from 'ng2-ace-editor';
import { TreeviewModule } from 'ngx-treeview';
import { CodeEditorFileBrowserComponent } from './file-browser/code-editor-file-browser.component';
import { CodeEditorBuildOutputComponent } from './build-output/code-editor-build-output.component';
import { CodeEditorFileBrowserCreateComponent } from './file-browser/code-editor-file-browser-create';
import { CodeEditorFileBrowserDeleteComponent } from './file-browser/code-editor-file-browser-delete';
import { CodeEditorInstructionsComponent } from './instructions/code-editor-instructions.component';
import { EditorInstructionsResultDetailComponent } from './instructions/code-editor-instructions-result-detail';

const ENTITY_STATES = [
    ...codeEditorRoute
];

@NgModule({
    imports: [
        ArTEMiSSharedModule,
        AceEditorModule,
        ArTEMiSResultModule,
        MomentModule,
        TreeviewModule.forRoot(),
        RouterModule.forChild(ENTITY_STATES)
    ],
    declarations: [
        CodeEditorComponent,
        CodeEditorAceComponent,
        CodeEditorFileBrowserComponent,
        CodeEditorFileBrowserCreateComponent,
        CodeEditorFileBrowserDeleteComponent,
        CodeEditorBuildOutputComponent,
        CodeEditorInstructionsComponent,
        EditorInstructionsResultDetailComponent
    ],
    exports: [
        CodeEditorComponent
    ],
    entryComponents: [
        HomeComponent,
        CodeEditorComponent,
        JhiMainComponent,
        CodeEditorFileBrowserCreateComponent,
        CodeEditorFileBrowserDeleteComponent,
        EditorInstructionsResultDetailComponent,
        ResultComponent
    ],
    providers: [
        JhiAlertService,
        RepositoryService,
        ResultService,
        ParticipationService,
        CodeEditorService,
        { provide: JhiLanguageService, useClass: JhiLanguageService }
    ],
    schemas: [CUSTOM_ELEMENTS_SCHEMA]
})
export class ArTEMiSCodeEditorModule {
    constructor(private languageService: JhiLanguageService, private languageHelper: JhiLanguageHelper) {
        this.languageHelper.language.subscribe((languageKey: string) => {
            if (languageKey !== undefined) {
                this.languageService.changeLanguage(languageKey);
            }
        });
    }
}
