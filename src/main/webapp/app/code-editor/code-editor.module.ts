import { CUSTOM_ELEMENTS_SCHEMA, NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';
import { JhiLanguageService } from 'ng-jhipster';
import { JhiLanguageHelper } from 'app/core';

import { ArTEMiSSharedModule } from '../shared';
import { codeEditorRoute } from './code-editor.route';
import { JhiAlertService } from 'ng-jhipster';
import { CodeEditorBuildableComponent } from './code-editor-buildable.component';
import { CodeEditorInstructorContainerComponent } from './mode/code-editor-instructor-container.component';
import { CodeEditorStudentContainerComponent } from './mode/code-editor-student-container.component';
import { CodeEditorService } from './code-editor.service';
import {
    DomainService,
    RepositoryFileParticipationService,
    RepositoryParticipationService,
    TestRepositoryFileService,
    TestRepositoryService,
} from './code-editor-repository.service';
import { ArTEMiSResultModule, ResultService } from 'app/entities/result';
import { ParticipationService } from '../entities/participation';
import { MomentModule } from 'ngx-moment';
import { CodeEditorAceComponent } from './ace/code-editor-ace.component';
import { AceEditorModule } from 'ng2-ace-editor';
import { TreeviewModule } from 'ngx-treeview';
import { CodeEditorFileBrowserComponent } from './file-browser/code-editor-file-browser.component';
import { CodeEditorBuildOutputComponent } from './build-output/code-editor-build-output.component';
import { CodeEditorInstructionsComponent } from './instructions/code-editor-instructions.component';
import { CodeEditorStatusComponent } from './status/code-editor-status.component';
import { EditorInstructionsResultDetailComponent } from './instructions/code-editor-instructions-result-detail';
import { ArTEMiSProgrammingExerciseModule } from 'app/entities/programming-exercise/programming-exercise.module';
import { CodeEditorActionsComponent } from 'app/code-editor/actions/code-editor-actions.component';
import { ArTEMiSMarkdownEditorModule } from 'app/markdown-editor';
import { CodeEditorComponent } from './code-editor.component';

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
        CodeEditorBuildableComponent,
        CodeEditorInstructorContainerComponent,
        CodeEditorStudentContainerComponent,
        CodeEditorFileBrowserComponent,
        CodeEditorAceComponent,
        CodeEditorBuildOutputComponent,
        CodeEditorInstructionsComponent,
        EditorInstructionsResultDetailComponent,
        CodeEditorStatusComponent,
        CodeEditorActionsComponent,
    ],
    exports: [CodeEditorInstructorContainerComponent, CodeEditorStudentContainerComponent],
    entryComponents: [CodeEditorInstructorContainerComponent, CodeEditorStudentContainerComponent],
    providers: [
        JhiAlertService,
        ResultService,
        ParticipationService,
        CodeEditorService,
        DomainService,
        RepositoryFileParticipationService,
        RepositoryParticipationService,
        TestRepositoryFileService,
        TestRepositoryService,
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
