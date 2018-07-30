import { CUSTOM_ELEMENTS_SCHEMA, NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';

import { ArTEMiSSharedModule } from '../shared';
import { editorRoute } from './editor.route';
import { JhiAlertService } from 'ng-jhipster';
import { EditorComponent } from './editor.component';
import { RepositoryService } from '../entities/repository';
import { ResultService } from '../entities/result';
import { HomeComponent } from '../home';
import { ParticipationService } from '../entities/participation';
import { MomentModule } from 'angular2-moment';
import { JhiMainComponent } from '../layouts';
import { EditorAceComponent } from './ace/editor-ace.component';
import { AceEditorModule } from 'ng2-ace-editor';
import { EditorFileBrowserComponent } from './file-browser/editor-file-browser.component';
import { TreeModule } from 'ng2-tree';
import { EditorBuildOutputComponent } from './build-output/editor-build-output.component';
import { EditorFileBrowserCreateComponent } from './file-browser/editor-file-browser-create';
import { EditorFileBrowserDeleteComponent } from './file-browser/editor-file-browser-delete';

const ENTITY_STATES = [
    ...editorRoute
];

@NgModule({
    imports: [
        ArTEMiSSharedModule,
        AceEditorModule,
        MomentModule,
        TreeModule,
        RouterModule.forChild(ENTITY_STATES)
    ],
    declarations: [
        EditorComponent,
        EditorAceComponent,
        EditorFileBrowserComponent,
        EditorFileBrowserCreateComponent,
        EditorFileBrowserDeleteComponent,
        EditorBuildOutputComponent
    ],
    exports: [
        EditorComponent
    ],
    entryComponents: [
        HomeComponent,
        EditorComponent,
        JhiMainComponent,
        EditorFileBrowserCreateComponent,
        EditorFileBrowserDeleteComponent
    ],
    providers: [
        JhiAlertService,
        RepositoryService,
        ResultService,
        ParticipationService
    ],
    schemas: [CUSTOM_ELEMENTS_SCHEMA]
})
export class ArTEMiSEditorModule {}
