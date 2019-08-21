import { CUSTOM_ELEMENTS_SCHEMA, NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { NgJhipsterModule } from 'ng-jhipster';
import { RouterModule } from '@angular/router';
import { JhiAlertService, JhiLanguageService } from 'ng-jhipster';
import { MomentModule } from 'ngx-moment';
import { AceEditorModule } from 'ng2-ace-editor';
import { TreeviewModule } from 'ngx-treeview';

import { JhiLanguageHelper } from 'app/core';
import { codeEditorRoute } from './code-editor.route';
import { ArtemisResultModule } from 'app/entities/result';

import {
    CodeEditorAceComponent,
    CodeEditorActionsComponent,
    CodeEditorBuildLogService,
    CodeEditorBuildOutputComponent,
    CodeEditorConflictStateService,
    CodeEditorFileBrowserComponent,
    CodeEditorFileBrowserCreateNodeComponent,
    CodeEditorFileBrowserDeleteComponent,
    CodeEditorFileBrowserFileComponent,
    CodeEditorFileBrowserFolderComponent,
    CodeEditorFileService,
    CodeEditorGridComponent,
    CodeEditorGridService,
    CodeEditorInstructionsComponent,
    CodeEditorInstructorContainerComponent,
    CodeEditorRepositoryFileService,
    CodeEditorRepositoryService,
    CodeEditorResolveConflictModalComponent,
    CodeEditorSessionService,
    CodeEditorStatusComponent,
    CodeEditorStudentContainerComponent,
    CodeEditorSubmissionService,
    DomainService,
} from './';

import { ArtemisProgrammingExerciseModule } from 'app/entities/programming-exercise/programming-exercise.module';
import { ArtemisMarkdownEditorModule } from 'app/markdown-editor';
import { ArtemisExerciseHintModule } from 'app/entities/exercise-hint/exercise-hint.module';
import { ExerciseHintStudentDialogComponent } from 'app/entities/exercise-hint';
import { ArtemisSharedPipesModule } from 'app/shared/pipes/shared-pipes.module';
import { ArtemisSharedModule } from 'app/shared';
import { ArtemisProgrammingExerciseInstructionsRenderModule } from 'app/entities/programming-exercise/instructions/instructions-render';
import { ArtemisProgrammingExerciseInstructionsEditorModule } from 'app/entities/programming-exercise/instructions/instructions-editor';

const ENTITY_STATES = [...codeEditorRoute];

@NgModule({
    imports: [
        ArtemisSharedModule,
        AceEditorModule,
        ArtemisResultModule,
        MomentModule,
        ArtemisProgrammingExerciseInstructionsRenderModule,
        ArtemisProgrammingExerciseInstructionsEditorModule,
        TreeviewModule.forRoot(),
        RouterModule.forChild(ENTITY_STATES),
        ArtemisExerciseHintModule,
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
        CodeEditorStatusComponent,
        CodeEditorActionsComponent,
        CodeEditorResolveConflictModalComponent,
    ],
    exports: [CodeEditorInstructorContainerComponent, CodeEditorStudentContainerComponent],
    entryComponents: [
        CodeEditorInstructorContainerComponent,
        CodeEditorStudentContainerComponent,
        CodeEditorFileBrowserDeleteComponent,
        ExerciseHintStudentDialogComponent,
        CodeEditorResolveConflictModalComponent,
    ],
    providers: [
        JhiAlertService,
        DomainService,
        CodeEditorRepositoryService,
        CodeEditorRepositoryFileService,
        CodeEditorBuildLogService,
        CodeEditorSessionService,
        CodeEditorFileService,
        CodeEditorGridService,
        CodeEditorConflictStateService,
        CodeEditorSubmissionService,
        { provide: JhiLanguageService, useClass: JhiLanguageService },
    ],
    schemas: [CUSTOM_ELEMENTS_SCHEMA],
})
export class ArtemisCodeEditorModule {
    constructor(private languageService: JhiLanguageService, private languageHelper: JhiLanguageHelper) {
        this.languageHelper.language.subscribe((languageKey: string) => {
            if (languageKey) {
                this.languageService.changeLanguage(languageKey);
            }
        });
    }
}
