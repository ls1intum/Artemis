import { CUSTOM_ELEMENTS_SCHEMA, NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';
import { JhiLanguageService } from 'ng-jhipster';
import { JhiLanguageHelper } from 'app/core';

import { ArTEMiSSharedModule } from '../shared';
import { codeEditorRoute } from './code-editor.route';
import { JhiAlertService } from 'ng-jhipster';
import { ArTEMiSResultModule, ResultService } from 'app/entities/result';
import { ParticipationService } from '../entities/participation';
import { MomentModule } from 'ngx-moment';
import { AceEditorModule } from 'ng2-ace-editor';
import { TreeviewModule } from 'ngx-treeview';

import {
    // services
    CodeEditorService,
    DomainService,
    CodeEditorRepositoryService,
    CodeEditorRepositoryFileService,
    CodeEditorBuildLogService,
    CodeEditorSessionService,
    // layout
    CodeEditorGridComponent,
    // components
    CodeEditorAceComponent,
    CodeEditorFileBrowserComponent,
    CodeEditorFileBrowserDeleteComponent,
    CodeEditorFileBrowserFileComponent,
    CodeEditorFileBrowserFolderComponent,
    CodeEditorFileBrowserCreateNodeComponent,
    CodeEditorBuildOutputComponent,
    CodeEditorStatusComponent,
    EditorInstructionsResultDetailComponent,
    CodeEditorActionsComponent,
    CodeEditorInstructionsComponent,
    // containers
    CodeEditorInstructorContainerComponent,
    CodeEditorStudentContainerComponent,
    CodeEditorFileService,
} from './';

import { ArTEMiSProgrammingExerciseModule } from 'app/entities/programming-exercise/programming-exercise.module';
import { ArTEMiSMarkdownEditorModule } from 'app/markdown-editor';

const ENTITY_STATES = [...codeEditorRoute];

@NgModule({
    imports: [
        ArTEMiSSharedModule,
        AceEditorModule,
        ArTEMiSResultModule,
        ArTEMiSMarkdownEditorModule,
        MomentModule,
        ArTEMiSProgrammingExerciseModule,
        TreeviewModule.forRoot(),
        RouterModule.forChild(ENTITY_STATES),
    ],
    declarations: [
        CodeEditorGridComponent,
        CodeEditorInstructorContainerComponent,
        CodeEditorStudentContainerComponent,
        CodeEditorFileBrowserComponent,
        CodeEditorFileBrowserDeleteComponent,
        CodeEditorFileBrowserFileComponent,
        CodeEditorFileBrowserFolderComponent,
        CodeEditorFileBrowserCreateNodeComponent,
        CodeEditorAceComponent,
        CodeEditorBuildOutputComponent,
        CodeEditorInstructionsComponent,
        EditorInstructionsResultDetailComponent,
        CodeEditorStatusComponent,
        CodeEditorActionsComponent,
    ],
    exports: [CodeEditorInstructorContainerComponent, CodeEditorStudentContainerComponent],
    entryComponents: [CodeEditorInstructorContainerComponent, CodeEditorStudentContainerComponent, CodeEditorFileBrowserDeleteComponent, EditorInstructionsResultDetailComponent],
    providers: [
        JhiAlertService,
        ResultService,
        ParticipationService,
        CodeEditorService,
        DomainService,
        CodeEditorRepositoryService,
        CodeEditorRepositoryFileService,
        CodeEditorBuildLogService,
        CodeEditorSessionService,
        CodeEditorFileService,
        { provide: JhiLanguageService, useClass: JhiLanguageService },
    ],
    schemas: [CUSTOM_ELEMENTS_SCHEMA],
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
