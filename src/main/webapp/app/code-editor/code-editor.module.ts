import { CUSTOM_ELEMENTS_SCHEMA, NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';
import { JhiLanguageService } from 'ng-jhipster';
import { JhiLanguageHelper } from 'app/core';

import { ArTEMiSSharedModule } from '../shared';
import { codeEditorRoute } from './code-editor.route';
import { JhiAlertService } from 'ng-jhipster';
import { CodeEditorComponent } from './code-editor.component';
import { CodeEditorInstructorContainerComponent } from './mode/instructor/code-editor-instructor-container.component';
import { CodeEditorStudentContainerComponent } from './mode/student/code-editor-student-container.component';
import { CodeEditorService } from './code-editor.service';
import { RepositoryService } from '../entities/repository';
import { ArTEMiSResultModule, ResultService } from '../entities/result';
import { ParticipationService } from '../entities/participation';
import { MomentModule } from 'ngx-moment';
import { CodeEditorAceComponent } from './ace/code-editor-ace.component';
import { AceEditorModule } from 'ng2-ace-editor';
import { TreeviewModule } from 'ngx-treeview';
import { CodeEditorFileBrowserComponent } from './file-browser/code-editor-file-browser.component';
import { CodeEditorBuildOutputComponent } from './build-output/code-editor-build-output.component';
import { CodeEditorFileBrowserDeleteComponent } from './file-browser/code-editor-file-browser-delete';
import { CodeEditorInstructionsComponent } from './instructions/code-editor-instructions.component';
import { CodeEditorStatusComponent } from './status/code-editor-status.component';
import { EditorInstructionsResultDetailComponent } from './instructions/code-editor-instructions-result-detail';
import { ArTEMiSProgrammingExerciseModule } from 'app/entities/programming-exercise/programming-exercise.module';
import { CodeEditorActionsComponent } from 'app/code-editor/actions/code-editor-actions.component';
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
        CodeEditorComponent,
        CodeEditorInstructorContainerComponent,
        CodeEditorStudentContainerComponent,
        CodeEditorAceComponent,
        CodeEditorFileBrowserComponent,
        CodeEditorFileBrowserDeleteComponent,
        CodeEditorBuildOutputComponent,
        CodeEditorInstructionsComponent,
        EditorInstructionsResultDetailComponent,
        CodeEditorStatusComponent,
        CodeEditorActionsComponent,
    ],
    exports: [CodeEditorInstructorContainerComponent, CodeEditorStudentContainerComponent],
    entryComponents: [CodeEditorInstructorContainerComponent, CodeEditorStudentContainerComponent, CodeEditorFileBrowserDeleteComponent],
    providers: [JhiAlertService, RepositoryService, ResultService, ParticipationService, CodeEditorService, { provide: JhiLanguageService, useClass: JhiLanguageService }],
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
