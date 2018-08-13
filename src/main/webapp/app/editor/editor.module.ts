import { CUSTOM_ELEMENTS_SCHEMA, NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';
import { ArTEMiSSharedModule } from '../shared';
import { editorRoute } from './editor.route';
import { JhiAlertService } from 'ng-jhipster';
import { EditorComponent } from './editor.component';
import { EditorService } from './editor.service';
import { RepositoryService } from '../entities/repository';
import { ResultService } from '../entities/result';
import { HomeComponent } from '../home';
import { ParticipationService } from '../entities/participation';
import { MomentModule } from 'angular2-moment';
import { JhiMainComponent } from '../layouts';
import { EditorAceComponent } from './ace/editor-ace.component';
import { AceEditorModule } from 'ng2-ace-editor';
import { TreeviewModule } from 'ngx-treeview';
import { EditorFileBrowserComponent } from './file-browser/editor-file-browser.component';
import { EditorBuildOutputComponent } from './build-output/editor-build-output.component';
import { EditorFileBrowserCreateComponent } from './file-browser/editor-file-browser-create';
import { EditorFileBrowserDeleteComponent } from './file-browser/editor-file-browser-delete';
import { EditorInstructionsComponent } from './instructions/editor-instructions.component';
import { EditorInstructionsResultDetailComponent } from './instructions/editor-instructions-result-detail';
import { ArTEMiSCoursesModule, ResultComponent } from '../courses';
const ENTITY_STATES = [
    ...editorRoute
];

@NgModule({
    imports: [
        ArTEMiSSharedModule,
        AceEditorModule,
        ArTEMiSCoursesModule,
        MomentModule,
        TreeviewModule.forRoot(),
        RouterModule.forChild(ENTITY_STATES)
    ],
    declarations: [
        EditorComponent,
        EditorAceComponent,
        EditorFileBrowserComponent,
        EditorFileBrowserCreateComponent,
        EditorFileBrowserDeleteComponent,
        EditorBuildOutputComponent,
        EditorInstructionsComponent,
        EditorInstructionsResultDetailComponent
    ],
    exports: [
        EditorComponent
    ],
    entryComponents: [
        HomeComponent,
        EditorComponent,
        JhiMainComponent,
        EditorFileBrowserCreateComponent,
        EditorFileBrowserDeleteComponent,
        EditorInstructionsResultDetailComponent,
        ResultComponent
    ],
    providers: [
        JhiAlertService,
        RepositoryService,
        ResultService,
        ParticipationService,
        EditorService
    ],
    schemas: [CUSTOM_ELEMENTS_SCHEMA]
})
export class ArTEMiSEditorModule {}
