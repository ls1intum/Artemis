import { NgModule } from '@angular/core';
import { MomentModule } from 'ngx-moment';
import { AceEditorModule } from 'ng2-ace-editor';
import { TreeviewModule } from 'ngx-treeview';
import { CodeEditorBuildOutputComponent } from 'app/exercises/programming/shared/code-editor/build-output/code-editor-build-output.component';
import { CodeEditorGridComponent } from 'app/exercises/programming/shared/code-editor/layout/code-editor-grid.component';
import { CodeEditorActionsComponent } from 'app/exercises/programming/shared/code-editor/actions/code-editor-actions.component';
import { CodeEditorFileBrowserFolderComponent } from 'app/exercises/programming/shared/code-editor/file-browser/code-editor-file-browser-folder.component';
import { CodeEditorAceComponent } from 'app/exercises/programming/shared/code-editor/ace/code-editor-ace.component';
import { CodeEditorFileBrowserDeleteComponent } from 'app/exercises/programming/shared/code-editor/file-browser/code-editor-file-browser-delete';
import { CodeEditorInstructionsComponent } from 'app/exercises/programming/shared/code-editor/instructions/code-editor-instructions.component';
import { CodeEditorResolveConflictModalComponent } from 'app/exercises/programming/shared/code-editor/actions/code-editor-resolve-conflict-modal.component';
import { ExerciseHintStudentDialogComponent } from 'app/exercises/shared/exercise-hint/exercise-hint-student-dialog.component';
import { ArtemisProgrammingExerciseInstructionsEditorModule } from 'app/exercises/programming/manage/instructions-editor/programming-exercise-instructions-editor.module';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { CodeEditorRepositoryIsLockedComponent } from 'app/exercises/programming/shared/code-editor/layout/code-editor-repository-is-locked.component';
import { CodeEditorFileBrowserCreateNodeComponent } from 'app/exercises/programming/shared/code-editor/file-browser/code-editor-file-browser-create-node.component';
import { CodeEditorFileBrowserFileComponent } from 'app/exercises/programming/shared/code-editor/file-browser/code-editor-file-browser-file.component';
import { CodeEditorFileBrowserComponent } from 'app/exercises/programming/shared/code-editor/file-browser/code-editor-file-browser.component';
import { CodeEditorStatusComponent } from 'app/exercises/programming/shared/code-editor/status/code-editor-status.component';
import { FeatureToggleModule } from 'app/shared/feature-toggle/feature-toggle.module';

@NgModule({
    imports: [AceEditorModule, MomentModule, ArtemisSharedModule, FeatureToggleModule, TreeviewModule.forRoot(), ArtemisProgrammingExerciseInstructionsEditorModule],
    declarations: [
        CodeEditorGridComponent,
        CodeEditorRepositoryIsLockedComponent,
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
    exports: [
        CodeEditorGridComponent,
        CodeEditorRepositoryIsLockedComponent,
        CodeEditorAceComponent,
        CodeEditorFileBrowserComponent,
        CodeEditorActionsComponent,
        CodeEditorInstructionsComponent,
        CodeEditorBuildOutputComponent,
    ],
    entryComponents: [CodeEditorFileBrowserDeleteComponent, ExerciseHintStudentDialogComponent, CodeEditorResolveConflictModalComponent],
    providers: [],
})
export class ArtemisCodeEditorModule {}
