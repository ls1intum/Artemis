import { NgModule } from '@angular/core';
import { AlertService } from 'app/core/alert/alert.service';
import { RouterModule } from '@angular/router';
import { MomentModule } from 'ngx-moment';
import { AceEditorModule } from 'ng2-ace-editor';
import { TreeviewModule } from 'ngx-treeview';
import { codeEditorRoute } from './code-editor.route';
import { CodeEditorBuildOutputComponent } from 'app/exercises/programming/shared/code-editor/build-output/code-editor-build-output.component';
import { CodeEditorGridComponent } from 'app/exercises/programming/shared/code-editor/layout/code-editor-grid.component';
import { CodeEditorActionsComponent } from 'app/exercises/programming/shared/code-editor/actions/code-editor-actions.component';
import { CodeEditorFileBrowserFolderComponent } from 'app/exercises/programming/shared/code-editor/file-browser/code-editor-file-browser-folder.component';
import { CodeEditorAceComponent } from 'app/exercises/programming/shared/code-editor/ace/code-editor-ace.component';
import { CodeEditorFileBrowserDeleteComponent } from 'app/exercises/programming/shared/code-editor/file-browser/code-editor-file-browser-delete';
import { CodeEditorInstructionsComponent } from 'app/exercises/programming/shared/code-editor/instructions/code-editor-instructions.component';
import { CodeEditorResolveConflictModalComponent } from 'app/exercises/programming/shared/code-editor/actions/code-editor-resolve-conflict-modal.component';
import { CodeEditorStudentContainerComponent } from 'app/exercises/programming/shared/code-editor/code-editor-student-container.component';
import { ExerciseHintStudentDialogComponent } from 'app/exercises/shared/exercise-hint/exercise-hint-student-dialog.component';
import { ArtemisProgrammingExerciseInstructionsEditorModule } from 'app/exercises/programming/manage/instructions/instructions-editor/programming-exercise-instructions-editor.module';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { CodeEditorRepositoryIsLockedComponent } from 'app/exercises/programming/shared/code-editor/layout/code-editor-repository-is-locked.component';
import { ArtemisProgrammingExerciseStatusModule } from 'app/exercises/programming/manage/status/programming-exercise-status.module';
import { CodeEditorFileBrowserCreateNodeComponent } from 'app/exercises/programming/shared/code-editor/file-browser/code-editor-file-browser-create-node.component';
import { CodeEditorFileBrowserFileComponent } from 'app/exercises/programming/shared/code-editor/file-browser/code-editor-file-browser-file.component';
import { ArtemisResultModule } from 'app/exercises/shared/result/result.module';
import { ArtemisExerciseHintModule } from 'app/exercises/shared/exercise-hint/exercise-hint.module';
import { CodeEditorFileBrowserComponent } from 'app/exercises/programming/shared/code-editor/file-browser/code-editor-file-browser.component';
import { CodeEditorStatusComponent } from 'app/exercises/programming/shared/code-editor/status/code-editor-status.component';
import { ArtemisProgrammingExerciseActionsModule } from 'app/exercises/programming/manage/actions/programming-exercise-actions.module';
import { FeatureToggleModule } from 'app/shared/feature-toggle/feature-toggle.module';
import { CodeEditorInstructorOrionContainerComponent } from 'app/exercises/programming/shared/code-editor/instructor/code-editor-instructor-orion-container.component';
import { OrionModule } from 'app/shared/orion/orion.module';
import { CodeEditorInstructorContainerComponent } from 'app/exercises/programming/shared/code-editor/instructor/code-editor-instructor-container.component';

const ENTITY_STATES = [...codeEditorRoute];

@NgModule({
    imports: [
        RouterModule.forChild(ENTITY_STATES),
        AceEditorModule,
        MomentModule,
        ArtemisSharedModule,
        ArtemisResultModule,
        ArtemisProgrammingExerciseInstructionsEditorModule,
        ArtemisProgrammingExerciseStatusModule,
        ArtemisProgrammingExerciseActionsModule,
        TreeviewModule.forRoot(),
        ArtemisExerciseHintModule,
        FeatureToggleModule,
        OrionModule,
    ],
    declarations: [
        CodeEditorGridComponent,
        CodeEditorRepositoryIsLockedComponent,
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
        CodeEditorInstructorOrionContainerComponent,
    ],
    exports: [CodeEditorInstructorContainerComponent, CodeEditorStudentContainerComponent],
    entryComponents: [
        CodeEditorInstructorOrionContainerComponent,
        CodeEditorInstructorContainerComponent,
        CodeEditorStudentContainerComponent,
        CodeEditorFileBrowserDeleteComponent,
        ExerciseHintStudentDialogComponent,
        CodeEditorResolveConflictModalComponent,
    ],
    providers: [],
})
export class ArtemisCodeEditorModule {}
